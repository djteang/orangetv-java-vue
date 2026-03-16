package com.orangetv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.entity.VideoSource;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.VideoSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSourceService {

    private final VideoSourceRepository videoSourceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Cacheable(value = "config", key = "'video_sources'")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEnabledSources() {
        return videoSourceRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllSources() {
        return videoSourceRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void addSource(String key, String name, String api, String detail) {
        if (videoSourceRepository.existsByName(key)) {
            throw ApiException.conflict("源名称已存在");
        }

        VideoSource source = VideoSource.builder()
                .name(name)
                .apiUrl(api)
                .apiType("cms")
                .enabled(true)
                .sortOrder(videoSourceRepository.findAll().size())
                .extConfig(detail)
                .build();

        videoSourceRepository.save(source);
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void editSource(Long id, String name, String api, String detail) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("源不存在"));

        if (name != null) source.setName(name);
        if (api != null) source.setApiUrl(api);
        if (detail != null) source.setExtConfig(detail);

        videoSourceRepository.save(source);
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void deleteSource(Long id) {
        videoSourceRepository.deleteById(id);
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void enableSource(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("源不存在"));
        source.setEnabled(true);
        videoSourceRepository.save(source);
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void disableSource(Long id) {
        VideoSource source = videoSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("源不存在"));
        source.setEnabled(false);
        videoSourceRepository.save(source);
    }

    @CacheEvict(value = "config", key = "'video_sources'")
    @Transactional
    public void sortSources(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            VideoSource source = videoSourceRepository.findById(ids.get(i)).orElse(null);
            if (source != null) {
                source.setSortOrder(i);
                videoSourceRepository.save(source);
            }
        }
    }

    /**
     * 验证单个视频源 API 是否可用（简单版本，用于旧接口兼容）
     */
    public Map<String, Object> validateSource(String api) {
        try {
            long startTime = System.currentTimeMillis();
            HttpHeaders headers = buildApiHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    api, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            String body = response.getBody();
            boolean valid = body != null && (body.contains("list") || body.contains("vod"));
            Map<String, Object> result = new HashMap<>();
            result.put("valid", valid);
            result.put("message", valid ? "验证成功" : "无效的API响应");
            result.put("responseTime", responseTime);
            return result;
        } catch (Exception e) {
            return Map.of("valid", false, "message", "连接失败: " + e.getMessage(), "responseTime", -1);
        }
    }

    /**
     * 使用搜索关键词验证视频源，返回详细结果（用于 SSE 流式验证）
     */
    public Map<String, Object> validateSourceWithSearch(String api, String sourceKey, String sourceName, String searchKeyword) {
        Map<String, Object> result = new HashMap<>();
        result.put("source", sourceKey);

        long startTime = System.currentTimeMillis();
        try {
            String encodedKeyword = URLEncoder.encode(searchKeyword, StandardCharsets.UTF_8);
            String searchUrl = api + "?ac=videolist&wd=" + encodedKeyword;

            HttpHeaders headers = buildApiHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            String body = responseEntity.getBody();
            if (body == null || body.isBlank()) {
                result.put("type", "source_error");
                result.put("status", "invalid");
                result.put("error", "空响应");
                result.put("responseTime", responseTime);
                result.put("resultCount", 0);
                return result;
            }

            // 检查是否为有效 JSON
            body = body.trim();
            if (!body.startsWith("{") && !body.startsWith("[")) {
                result.put("type", "source_error");
                result.put("status", "invalid");
                result.put("error", "非JSON响应");
                result.put("responseTime", responseTime);
                result.put("resultCount", 0);
                return result;
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode listNode = root.get("list");

            if (listNode != null && listNode.isArray() && !listNode.isEmpty()) {
                // 过滤匹配搜索关键词的结果
                int matchCount = 0;
                for (JsonNode item : listNode) {
                    String title = item.has("vod_name") ? item.get("vod_name").asText("") : "";
                    if (title.toLowerCase().contains(searchKeyword.toLowerCase())) {
                        matchCount++;
                    }
                }

                if (matchCount > 0) {
                    result.put("type", "source_result");
                    result.put("status", "valid");
                    result.put("resultCount", matchCount);
                } else {
                    result.put("type", "source_result");
                    result.put("status", "no_results");
                    result.put("resultCount", 0);
                }
            } else {
                result.put("type", "source_result");
                result.put("status", "no_results");
                result.put("resultCount", 0);
            }

            result.put("responseTime", responseTime);
            return result;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            result.put("type", "source_error");
            result.put("status", "invalid");
            result.put("error", e.getMessage());
            result.put("responseTime", responseTime);
            result.put("resultCount", 0);
            return result;
        }
    }

    /**
     * 对单个视频源进行测速（仅测量响应时间，不解析内容）
     */
    public Map<String, Object> speedTestSource(String api, String sourceKey) {
        Map<String, Object> result = new HashMap<>();
        result.put("source", sourceKey);

        long startTime = System.currentTimeMillis();
        try {
            HttpHeaders headers = buildApiHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    api, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            int statusCode = responseEntity.getStatusCode().value();
            result.put("responseTime", responseTime);
            result.put("statusCode", statusCode);
            result.put("success", true);
            return result;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            result.put("responseTime", responseTime);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 构建模拟浏览器的请求头，用于绕过简单的反爬/CDN 防护
     */
    private HttpHeaders buildApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("Accept-Encoding", "gzip, deflate");
        headers.set("Connection", "keep-alive");
        headers.set("Cache-Control", "no-cache");
        return headers;
    }

    private Map<String, Object> toMap(VideoSource source) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", source.getId());
        map.put("key", source.getId().toString());
        map.put("name", source.getName());
        map.put("api", source.getApiUrl());
        map.put("type", source.getApiType());
        map.put("enabled", source.getEnabled());
        map.put("disabled", !source.getEnabled());
        map.put("detail", source.getExtConfig());
        map.put("from", "custom");
        return map;
    }
}
