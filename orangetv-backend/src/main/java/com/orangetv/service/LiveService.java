package com.orangetv.service;

import com.orangetv.entity.LiveSource;
import com.orangetv.repository.LiveSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveService {

    private final LiveSourceRepository liveSourceRepository;
    private final RestTemplate restTemplate;

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
                headers.set("User-Agent", userAgent);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        source.getUrl(), HttpMethod.GET, entity, String.class);
                String m3uContent = response.getBody();

                List<Map<String, Object>> channels = parseM3u(source.getSourceKey(), m3uContent);

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

    @Cacheable(value = "live", key = "'channels_' + #sourceKey")
    @Transactional(readOnly = true)
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
            headers.set("User-Agent", userAgent);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    source.getUrl(), HttpMethod.GET, entity, String.class);
            String m3uContent = response.getBody();

            List<Map<String, Object>> channels = parseM3u(sourceKey, m3uContent);

            return Map.of(
                    "source", source.getName(),
                    "channels", channels,
                    "epgUrl", source.getEpgUrl() != null ? source.getEpgUrl() : ""
            );
        } catch (Exception e) {
            log.error("Failed to fetch channels from source: {}", source.getName(), e);
            return Map.of("error", e.getMessage(), "channels", Collections.emptyList());
        }
    }

    @Cacheable(value = "epg", key = "'epg_' + #sourceKey")
    @Transactional(readOnly = true)
    public Map<String, Object> getEpg(String sourceKey) {
        LiveSource source = liveSourceRepository.findBySourceKey(sourceKey)
                .orElse(null);

        if (source == null || source.getEpgUrl() == null) {
            return Map.of("error", "EPG not available", "programs", Collections.emptyMap());
        }

        try {
            // 使用自定义 UA 获取 EPG 内容
            String userAgent = source.getUserAgent() != null ? source.getUserAgent() : DEFAULT_USER_AGENT;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", userAgent);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    source.getEpgUrl(), HttpMethod.GET, entity, String.class);
            String epgContent = response.getBody();

            return Map.of("source", source.getName(), "epg", epgContent);
        } catch (Exception e) {
            log.error("Failed to fetch EPG from source: {}", source.getName(), e);
            return Map.of("error", e.getMessage(), "programs", Collections.emptyMap());
        }
    }

    public Map<String, Object> precheck(String url) {
        try {
            restTemplate.headForHeaders(url);
            return Map.of("ok", true, "url", url);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    private List<Map<String, Object>> parseM3u(String sourceKey, String content) {
        List<Map<String, Object>> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return channels;
        }

        String[] lines = content.split("\n");
        String currentName = null;
        String currentLogo = null;
        String currentGroup = null;
        String currentTvgId = null;
        int channelIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXTINF:")) {
                // 解析频道信息
                currentTvgId = extractAttribute(line, "tvg-id");
                currentName = extractAttribute(line, "tvg-name");
                if (currentName == null || currentName.isEmpty()) {
                    int commaIndex = line.lastIndexOf(',');
                    if (commaIndex > 0) {
                        currentName = line.substring(commaIndex + 1).trim();
                    }
                }
                currentLogo = extractAttribute(line, "tvg-logo");
                currentGroup = extractAttribute(line, "group-title");
                if (currentGroup == null || currentGroup.isEmpty()) {
                    currentGroup = "无分组";
                }
            } else if (!line.isEmpty() && !line.startsWith("#") && currentName != null) {
                Map<String, Object> channel = new HashMap<>();
                channel.put("id", sourceKey + "-" + channelIndex);
                channel.put("tvgId", currentTvgId);
                channel.put("name", currentName);
                channel.put("url", line);
                channel.put("logo", currentLogo);
                channel.put("group", currentGroup);
                channels.add(channel);
                channelIndex++;
                currentName = null;
                currentLogo = null;
                currentGroup = null;
                currentTvgId = null;
            }
        }

        return channels;
    }

    private String extractAttribute(String line, String attr) {
        String pattern = attr + "=\"";
        int start = line.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = line.indexOf("\"", start);
        if (end < 0) return null;
        return line.substring(start, end);
    }
}
