package com.orangetv.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SearchStreamService {
    private final SearchService searchService;
    private final VideoSourceService videoSourceService;
    private final RestTemplate searchClient;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final long timeoutMillis;
    private final int parallelism;

    @Autowired
    public SearchStreamService(SearchService searchService, VideoSourceService videoSourceService,
                               @Qualifier("searchRestTemplate") RestTemplate searchClient) {
        this(searchService, videoSourceService, searchClient,
                new ThreadPoolExecutor(16, 16, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128),
                        namedThreads("search-source-"), new ThreadPoolExecutor.AbortPolicy()),
                deadlineScheduler(), 20000, 8);
    }

    SearchStreamService(SearchService searchService, VideoSourceService videoSourceService, RestTemplate searchClient,
                        ExecutorService workers, ScheduledExecutorService scheduler, long timeoutMillis, int parallelism) {
        this.searchService = searchService;
        this.videoSourceService = videoSourceService;
        this.searchClient = searchClient;
        this.workers = workers;
        this.scheduler = scheduler;
        this.timeoutMillis = timeoutMillis;
        this.parallelism = parallelism;
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger index = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static ScheduledExecutorService deadlineScheduler() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, namedThreads("search-deadline-"));
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    public SseEmitter search(String keyword, boolean disableYellowFilter) {
        SseEmitter emitter = new SseEmitter(timeoutMillis + 2000);
        List<Map<String, Object>> sources = videoSourceService.getEnabledSources();
        SearchSession session = new SearchSession(keyword, sources, emitter,
                !sources.isEmpty() && searchService.isYellowFilterDisabled(disableYellowFilter));
        emitter.onCompletion(session::cancel);
        emitter.onTimeout(() -> session.finish(true));
        emitter.onError(error -> session.cancel());
        session.start();
        return emitter;
    }

    private final class SearchSession {
        private final String keyword;
        private final boolean yellowFilterDisabled;
        private final List<Map<String, Object>> sources;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger next = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final List<Future<?>> tasks = Collections.synchronizedList(new ArrayList<>());
        private final Object writeLock = new Object();
        private volatile ScheduledFuture<?> deadline;

        SearchSession(String keyword, List<Map<String, Object>> sources, SseEmitter emitter, boolean yellowFilterDisabled) {
            this.yellowFilterDisabled = yellowFilterDisabled;
            this.keyword = keyword;
            this.sources = sources;
            this.emitter = emitter;
        }

        Map<String, Object> progress(boolean timedOut) {
            return Map.of("totalSources", sources.size(), "completedSources", completed.get(),
                    "failedSources", failed.get(), "timedOut", timedOut);
        }

        void start() {
            send("init", progress(false));
            if (closed.get()) return;
            if (sources.isEmpty()) { finish(false); return; }
            deadline = scheduler.schedule(() -> finish(true), timeoutMillis, TimeUnit.MILLISECONDS);
            if (closed.get()) { deadline.cancel(false); return; }
            for (int i = 0; i < Math.min(parallelism, sources.size()); i++) dispatchNext();
        }

        // 每次只分派有限个来源，完成一个再补一个，避免大量搜索挤满线程池。
        void dispatchNext() {
            while (!closed.get()) {
                int index = next.getAndIncrement();
                if (index >= sources.size()) return;
                Map<String, Object> source = sources.get(index);
                FutureTask<Void> task = new FutureTask<>(() -> {
                    boolean sourceFailed = false;
                    try {
                        searchService.searchSourcePages(source, keyword, searchClient, batch -> {
                            if (closed.get()) return;
                            List<Map<String, Object>> filtered = searchService.filterSearchResults(batch, keyword, yellowFilterDisabled);
                            if (!filtered.isEmpty()) send("results", Map.of("source", source.get("key"), "results", filtered));
                        }, closed::get);
                    } catch (Exception e) {
                        sourceFailed = true;
                    } finally {
                        if (!closed.get()) {
                            if (sourceFailed) failed.incrementAndGet();
                            int count = completed.incrementAndGet();
                            send("progress", progress(false));
                            if (count == sources.size()) finish(false);
                            else dispatchNext();
                        }
                    }
                    return null;
                });
                tasks.add(task);
                if (closed.get()) { task.cancel(true); return; }
                try {
                    workers.execute(task);
                    return;
                } catch (RejectedExecutionException e) {
                    tasks.remove(task);
                    task.cancel(false);
                    failed.incrementAndGet();
                    if (completed.incrementAndGet() == sources.size()) { finish(false); return; }
                    send("progress", progress(false));
                }
            }
        }

        void send(String event, Object data) {
            synchronized (writeLock) {
                if (closed.get()) return;
                try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                } catch (Exception e) {
                    cancel();
                    emitter.completeWithError(e);
                }
            }
        }

        void finish(boolean timedOut) {
            synchronized (writeLock) {
                if (!closed.compareAndSet(false, true)) return;
                if (deadline != null) deadline.cancel(false);
                if (timedOut) stopTasks();
                try { emitter.send(SseEmitter.event().name("done").data(progress(timedOut))); }
                catch (Exception ignored) { /* 客户端可能已经取消。 */ }
                emitter.complete();
            }
        }

        void stopTasks() {
            synchronized (tasks) { tasks.forEach(task -> task.cancel(true)); tasks.clear(); }
            if (workers instanceof ThreadPoolExecutor pool) pool.purge();
        }

        void cancel() {
            if (!closed.compareAndSet(false, true)) return;
            if (deadline != null) deadline.cancel(false);
            stopTasks();
        }
    }

    @PreDestroy
    public void close() {
        workers.shutdownNow();
        scheduler.shutdownNow();
        if (searchClient.getRequestFactory() instanceof HttpComponentsClientHttpRequestFactory factory) {
            try { factory.destroy(); } catch (Exception ignored) { }
        }
    }
}
