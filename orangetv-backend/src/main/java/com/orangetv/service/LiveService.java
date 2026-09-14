package com.orangetv.service;

import com.orangetv.entity.LiveSource;
import com.orangetv.repository.LiveSourceRepository;
import com.orangetv.util.ContentDecoder;
import com.orangetv.util.LivePlaylistParser;
import com.orangetv.util.LiveHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@Slf4j
@Service
public class LiveService {

    private final LiveSourceRepository liveSourceRepository;
    private final RestTemplate restTemplate;

    public LiveService(LiveSourceRepository liveSourceRepository,
                       @Qualifier("liveMetadataRestTemplate") RestTemplate restTemplate) {
        this.liveSourceRepository = liveSourceRepository;
        this.restTemplate = restTemplate;
    }

    private static final String DEFAULT_USER_AGENT = "AptvPlayer/1.4.10";

    // 缓存所有频道数据
    private Map<String, List<Map<String, Object>>> cachedAllChannels = null;
    private long cacheTimestamp = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSources() {
        List<LiveSource> sources = liveSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (LiveSource source : sources) {
            Map<String, Object> item = new HashMap<>();
            item.put("key", source.getSourceKey());
            item.put("name", source.getName());
            item.put("url", source.getUrl());
            item.put("epg", source.getEpgUrl());
            item.put("ua", source.getUserAgent());
            item.put("headers", LiveHeaders.read(source.getRequestHeaders()));
            item.put("inline", source.getChannelConfig() != null);
            item.put("channelCount", source.getChannelCount());
            result.add(item);
        }

        return result;
    }

    /**
     * 获取所有直播分类
     */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        Map<String, List<Map<String, Object>>> allChannels = getAllChannelsGrouped();
        return new ArrayList<>(allChannels.keySet());
    }

    /**
     * 获取指定分类下的频道列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelsByCategory(String category) {
        Map<String, List<Map<String, Object>>> allChannels = getAllChannelsGrouped();
        return allChannels.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 获取所有频道并按分组聚合
     */
    private synchronized Map<String, List<Map<String, Object>>> getAllChannelsGrouped() {
        long now = System.currentTimeMillis();
        if (cachedAllChannels != null && (now - cacheTimestamp) < CACHE_DURATION) {
            return cachedAllChannels;
        }

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        List<LiveSource> sources = liveSourceRepository.findByEnabledTrueOrderBySortOrderAsc();

        for (LiveSource source : sources) {
            try {
                String userAgent = source.getUserAgent() != null ? source.getUserAgent() : DEFAULT_USER_AGENT;
                HttpHeaders headers = new HttpHeaders();
                headers.setAll(LiveHeaders.read(source.getRequestHeaders()));
                headers.set("User-Agent", userAgent);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                String m3uContent = source.getChannelConfig();
                if (m3uContent == null) {
                    ResponseEntity<String> response = restTemplate.exchange(
                            URI.create(source.getUrl()), HttpMethod.GET, entity, String.class);
                    m3uContent = response.getBody();
                }

                List<Map<String, Object>> channels = parsePlaylist(source.getSourceKey(), m3uContent);

                // 按 group 分组
                for (Map<String, Object> channel : channels) {
                    String group = (String) channel.getOrDefault("group", "无分组");
                    // 添加 category 字段用于前端显示
                    channel.put("category", source.getName());
                    grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(channel);
                }

                // 更新频道数量
                if (source.getChannelCount() == null || !source.getChannelCount().equals(channels.size())) {
                    source.setChannelCount(channels.size());
                    liveSourceRepository.save(source);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch channels from source {}: {}", source.getName(), e.getMessage());
            }
        }

        cachedAllChannels = grouped;
        cacheTimestamp = now;
        return grouped;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cachedAllChannels = null;
        cacheTimestamp = 0;
    }

    @Cacheable(value = "live", key = "'channels_' + #sourceKey", unless = "#result.containsKey('error')")
    @Transactional
    public Map<String, Object> getChannels(String sourceKey) {
        LiveSource source = liveSourceRepository.findBySourceKey(sourceKey)
                .orElse(null);

        if (source == null) {
            return Map.of("error", "Source not found", "channels", Collections.emptyList());
        }

        try {
            // 使用自定义 UA 获取 M3U 内容
            String userAgent = source.getUserAgent() != null ? source.getUserAgent() : DEFAULT_USER_AGENT;
            HttpHeaders headers = new HttpHeaders();
            headers.setAll(LiveHeaders.read(source.getRequestHeaders()));
            headers.set("User-Agent", userAgent);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String m3uContent = source.getChannelConfig();
            if (m3uContent == null) {
                ResponseEntity<String> response = restTemplate.exchange(
                        URI.create(source.getUrl()), HttpMethod.GET, entity, String.class);
                m3uContent = response.getBody();
            }

            List<Map<String, Object>> channels = parsePlaylist(sourceKey, m3uContent);
            if (channels.isEmpty()) {
                return Map.of("error", "直播源中未识别到频道，请检查是否为 M3U、TXT 或 JSON 频道列表", "channels", channels);
            }
            if (!Objects.equals(source.getChannelCount(), channels.size())) {
                source.setChannelCount(channels.size());
                liveSourceRepository.save(source);
            }

            // Redis 的 NON_FINAL 类型序列化需要可携带类型信息的缓存根对象。
            return new LinkedHashMap<>(Map.of(
                    "source", source.getName(),
                    "channels", channels,
                    "epgUrl", source.getEpgUrl() != null ? source.getEpgUrl() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to fetch channels from source: {}", source.getName(), e);
            String message = e instanceof ResourceAccessException
                    ? "服务器无法连接直播源，请检查源地址及服务器的 IPv4/IPv6 网络。"
                    : e instanceof HttpStatusCodeException http
                        ? "直播源返回 HTTP " + http.getStatusCode().value() + "，请检查地址和请求头是否有效。"
                        : "直播源内容无法解析：" + Objects.toString(e.getMessage(), "未知错误");
            return Map.of("error", message, "channels", Collections.emptyList());
        }
    }

    @Cacheable(value = "epg", key = "'epg_' + #sourceKey", unless = "#result.containsKey('error')")
    @Transactional(readOnly = true)
    public Map<String, Object> getEpg(String sourceKey) {
        LiveSource source = liveSourceRepository.findBySourceKey(sourceKey)
                .orElse(null);

        if (source == null || source.getEpgUrl() == null) {
            return Map.of("error", "EPG not available", "programs", Collections.emptyMap());
        }

        try {
            // EPG 可能由另一个站点提供，不能携带直播站点的 Cookie / Authorization。
            String userAgent = source.getUserAgent() != null ? source.getUserAgent() : DEFAULT_USER_AGENT;
            HttpHeaders headers = new HttpHeaders();
            headers.setAll(LiveHeaders.forUrl(LiveHeaders.read(source.getRequestHeaders()),
                    URI.create(source.getUrl()), URI.create(source.getEpgUrl())));
            headers.set("User-Agent", userAgent);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(source.getEpgUrl()), HttpMethod.GET, entity, String.class);
            String epgContent = response.getBody();

            return new LinkedHashMap<>(Map.of("source", source.getName(), "epg", epgContent));
        } catch (Exception e) {
            log.error("Failed to fetch EPG from source: {}", source.getName(), e);
            return Map.of("error", e.getMessage(), "programs", Collections.emptyMap());
        }
    }

    public Map<String, Object> precheck(String url) {
        try {
            restTemplate.headForHeaders(URI.create(url));
            return Map.of("ok", true, "url", url);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    private List<Map<String, Object>> parsePlaylist(String sourceKey, String content) {
        return LivePlaylistParser.parse(sourceKey, ContentDecoder.tryDecode(content));
    }
}
