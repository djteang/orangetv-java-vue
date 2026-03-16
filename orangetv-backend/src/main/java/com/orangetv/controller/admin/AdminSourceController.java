package com.orangetv.controller.admin;

import com.orangetv.config.SkipWrapper;
import com.orangetv.dto.ApiResponse;
import com.orangetv.service.VideoSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/admin/source")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminSourceController {

    private final VideoSourceService videoSourceService;
    private final ExecutorService validationExecutor = Executors.newFixedThreadPool(10);

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllSources() {
        return ResponseEntity.ok(videoSourceService.getAllSources());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> handleSourceAction(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        String key = (String) request.get("key");
        String name = (String) request.get("name");
        String api = (String) request.get("api");
        String detail = (String) request.get("detail");
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) request.get("keys");

        Long id = key != null ? Long.parseLong(key) : null;

        switch (action) {
            case "add":
                videoSourceService.addSource(key, name, api, detail);
                break;
            case "edit":
                videoSourceService.editSource(id, name, api, detail);
                break;
            case "delete":
                videoSourceService.deleteSource(id);
                break;
            case "enable":
                videoSourceService.enableSource(id);
                break;
            case "disable":
                videoSourceService.disableSource(id);
                break;
            case "sort":
                List<Long> ids = keys.stream().map(Long::parseLong).toList();
                videoSourceService.sortSources(ids);
                break;
            case "batch_enable":
                for (String k : keys) {
                    videoSourceService.enableSource(Long.parseLong(k));
                }
                break;
            case "batch_disable":
                for (String k : keys) {
                    videoSourceService.disableSource(Long.parseLong(k));
                }
                break;
            case "batch_delete":
                for (String k : keys) {
                    videoSourceService.deleteSource(Long.parseLong(k));
                }
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Unknown action: " + action));
        }

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateSource(@RequestBody Map<String, String> request) {
        String api = request.get("api");
        return ResponseEntity.ok(videoSourceService.validateSource(api));
    }

    /**
     * SSE 流式验证所有视频源有效性
     * 参数：
     *   q - 搜索关键词
     *   source - (可选) 指定单个源 key
     *   tempApi - (可选) 临时 API 地址（用于新增源验证）
     *   tempName - (可选) 临时源名称
     */
    @SkipWrapper
    @GetMapping(value = "/validate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter validateSourcesStream(
            @RequestParam("q") String searchKeyword,
            @RequestParam(value = "source", required = false) String sourceKey,
            @RequestParam(value = "tempApi", required = false) String tempApi,
            @RequestParam(value = "tempName", required = false) String tempName) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时

        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> sources;

                if (tempApi != null && tempName != null) {
                    // 临时源验证
                    Map<String, Object> tempSource = new HashMap<>();
                    tempSource.put("key", "temp");
                    tempSource.put("name", tempName);
                    tempSource.put("api", tempApi);
                    sources = List.of(tempSource);
                } else if (sourceKey != null) {
                    // 单个源验证
                    sources = videoSourceService.getAllSources().stream()
                            .filter(s -> sourceKey.equals(s.get("key").toString()))
                            .toList();
                    if (sources.isEmpty()) {
                        emitter.send(SseEmitter.event().data(
                                Map.of("type", "error", "message", "指定的视频源不存在")));
                        emitter.complete();
                        return;
                    }
                } else {
                    // 全部源验证
                    sources = videoSourceService.getAllSources();
                }

                // 发送开始事件
                emitter.send(SseEmitter.event().data(
                        Map.of("type", "start", "totalSources", sources.size())));

                AtomicInteger completedCount = new AtomicInteger(0);
                int totalSources = sources.size();

                // 并行验证所有源
                List<CompletableFuture<Void>> futures = sources.stream()
                        .map(source -> CompletableFuture.runAsync(() -> {
                            String sKey = source.get("key").toString();
                            String sName = (String) source.get("name");
                            String sApi = (String) source.get("api");

                            Map<String, Object> result = videoSourceService.validateSourceWithSearch(
                                    sApi, sKey, sName, searchKeyword);

                            try {
                                synchronized (emitter) {
                                    emitter.send(SseEmitter.event().data(result));
                                }
                            } catch (IOException e) {
                                log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
                            }

                            // 检查是否全部完成
                            if (completedCount.incrementAndGet() == totalSources) {
                                try {
                                    synchronized (emitter) {
                                        emitter.send(SseEmitter.event().data(
                                                Map.of("type", "complete", "completedSources", totalSources)));
                                    }
                                    emitter.complete();
                                } catch (IOException e) {
                                    log.debug("SSE complete 发送失败: {}", e.getMessage());
                                }
                            }
                        }, validationExecutor))
                        .toList();

                // 等待所有验证完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            } catch (Exception e) {
                log.error("SSE 验证流异常: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().data(
                            Map.of("type", "error", "message", e.getMessage())));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        }, validationExecutor);

        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.debug("SSE 连接错误: {}", t.getMessage()));

        return emitter;
    }

    /**
     * 单个视频源测速
     */
    @GetMapping("/speedtest")
    public ResponseEntity<Map<String, Object>> speedTestSource(
            @RequestParam("key") String sourceKey,
            @RequestParam(value = "api", required = false) String apiUrl) {

        String api = apiUrl;
        if (api == null || api.isEmpty()) {
            // 从数据库获取
            List<Map<String, Object>> sources = videoSourceService.getAllSources();
            Map<String, Object> source = sources.stream()
                    .filter(s -> sourceKey.equals(s.get("key").toString()))
                    .findFirst()
                    .orElse(null);

            if (source == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "源不存在"));
            }
            api = (String) source.get("api");
        }

        Map<String, Object> result = videoSourceService.speedTestSource(api, sourceKey);
        return ResponseEntity.ok(result);
    }
}
