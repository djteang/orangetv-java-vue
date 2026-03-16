package com.orangetv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.orangetv.exception.ApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final VideoSourceService videoSourceService;
    private final SiteConfigService siteConfigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final Pattern YELLOW_CONTENT_PATTERN = Pattern.compile(
            "伦理|情色|福利|色情|成人|AV|无码|有码|三级|激情|诱惑|裸|18禁|限制级|女优",
            Pattern.CASE_INSENSITIVE
    );

    @Cacheable(value = "search", key = "'search_' + #keyword")
    public Map<String, Object> search(String keyword) {
        List<Map<String, Object>> sources = videoSourceService.getEnabledSources();
        List<Map<String, Object>> allResults = new ArrayList<>();

        // 并行搜索所有源
        List<CompletableFuture<List<Map<String, Object>>>> futures = sources.stream()
                .map(source -> CompletableFuture.supplyAsync(() ->
                        searchSource(source, keyword), executorService))
                .collect(Collectors.toList());

        // 等待所有搜索完成（最多20秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(20, TimeUnit.SECONDS);

            for (CompletableFuture<List<Map<String, Object>>> future : futures) {
                try {
                    List<Map<String, Object>> results = future.get();
                    if (results != null) {
                        allResults.addAll(results);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get search results: {}", e.getMessage());
                }
            }
        } catch (TimeoutException e) {
            log.warn("Search timeout for keyword: {}", keyword);
            // 收集已完成的结果
            for (CompletableFuture<List<Map<String, Object>>> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    try {
                        List<Map<String, Object>> results = future.getNow(null);
                        if (results != null) {
                            allResults.addAll(results);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage());
        }

        // 先过滤不相关内容，再过滤黄色内容
        List<Map<String, Object>> filteredResults = filterIrrelevantContent(allResults, keyword);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("results", filterYellowContent(filteredResults));
        return resultMap;
    }

    public Map<String, Object> searchOne(String keyword, String resourceId) {
        List<Map<String, Object>> sources = videoSourceService.getEnabledSources();
        Map<String, Object> source = sources.stream()
                .filter(s -> resourceId.equals(s.get("key").toString()))
                .findFirst()
                .orElse(null);

        if (source == null) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("results", Collections.emptyList());
            errorMap.put("error", "Source not found");
            return errorMap;
        }

        List<Map<String, Object>> results = searchSource(source, keyword);
        // 过滤不相关内容
        List<Map<String, Object>> filteredResults = filterIrrelevantContent(
                results != null ? results : Collections.emptyList(), keyword);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("results", filterYellowContent(filteredResults));
        return resultMap;
    }

    public List<Map<String, Object>> getResources() {
        return videoSourceService.getEnabledSources().stream()
                .map(source -> {
                    Map<String, Object> resource = new HashMap<>();
                    resource.put("key", source.get("key"));
                    resource.put("name", source.get("name"));
                    resource.put("api", source.get("api"));
                    return resource;
                })
                .collect(Collectors.toList());
    }

    public List<String> getSuggestions(String keyword) {
        return Collections.emptyList();
    }

    /**
     * 根据 source 和 id 获取视频详情
     */
    @Cacheable(value = "videoDetail", key = "'detail_' + #sourceKey + '_' + #videoId")
    public Map<String, Object> getVideoDetail(String sourceKey, String videoId) {
        List<Map<String, Object>> sources = videoSourceService.getEnabledSources();
        Map<String, Object> source = sources.stream()
                .filter(s -> sourceKey.equals(s.get("key").toString()))
                .findFirst()
                .orElse(null);

        if (source == null) {
            throw ApiException.notFound("视频源不存在");
        }

        String api = (String) source.get("api");
        String sourceName = (String) source.get("name");

        try {
            String detailUrl = api + "?ac=videolist&ids=" + videoId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.set("Accept-Encoding", "gzip, deflate");
            headers.set("Connection", "keep-alive");
            headers.set("Cache-Control", "no-cache");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(detailUrl, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();

            if (response == null || response.isBlank()) {
                throw ApiException.internal("获取视频详情失败");
            }

            response = response.trim();
            if (!response.startsWith("{") && !response.startsWith("[")) {
                throw ApiException.internal("无效的响应格式");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode listNode = root.get("list");

            if (listNode == null || !listNode.isArray() || listNode.isEmpty()) {
                throw ApiException.notFound("视频不存在");
            }

            // 获取第一个结果
            List<Map<String, Object>> results = parseResultList(listNode, sourceKey, sourceName);
            if (results.isEmpty()) {
                throw ApiException.notFound("视频不存在");
            }

            return results.get(0);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get video detail: {}", e.getMessage());
            throw ApiException.internal("获取视频详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取视频播放地址
     */
    public String getPlayUrl(String sourceKey, String videoId, int episodeIndex) {
        Map<String, Object> videoData = getVideoDetail(sourceKey, videoId);

        @SuppressWarnings("unchecked")
        List<String> episodes = (List<String>) videoData.get("episodes");
        if (episodes == null || episodes.isEmpty()) {
            throw ApiException.notFound("没有可用的播放源");
        }

        if (episodeIndex < 0 || episodeIndex >= episodes.size()) {
            throw ApiException.badRequest("集数索引超出范围");
        }

        return episodes.get(episodeIndex);
    }

    private List<Map<String, Object>> filterYellowContent(List<Map<String, Object>> results) {
        Boolean disableFilter = siteConfigService.getBooleanConfig("disable_yellow_filter", false);
        if (Boolean.TRUE.equals(disableFilter)) {
            return results;
        }
        return results.stream()
                .filter(r -> !isYellowContent(r))
                .collect(Collectors.toList());
    }

    /**
     * 过滤不相关内容 - 确保结果标题与搜索关键词相关
     */
    private List<Map<String, Object>> filterIrrelevantContent(List<Map<String, Object>> results, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return results;
        }

        // 预处理关键词：转小写，提取所有字符（包括中文）
        String normalizedKeyword = keyword.toLowerCase().trim();
        // 将关键词拆分为字符数组，用于部分匹配
        char[] keywordChars = normalizedKeyword.toCharArray();

        return results.stream()
                .filter(r -> isRelevant(r, normalizedKeyword, keywordChars))
                .collect(Collectors.toList());
    }

    /**
     * 判断结果是否与关键词相关
     */
    private boolean isRelevant(Map<String, Object> item, String keyword, char[] keywordChars) {
        String title = String.valueOf(item.getOrDefault("title", "")).toLowerCase();

        if (title.isEmpty()) {
            return false;
        }

        // 1. 完全包含关键词
        if (title.contains(keyword)) {
            return true;
        }

        // 2. 关键词完全包含在标题中（反向匹配，如搜索"哈利波特"能匹配"哈利·波特"）
        String titleNoSpace = title.replaceAll("[\\s·.·　]+", "");
        String keywordNoSpace = keyword.replaceAll("[\\s·.·　]+", "");
        if (titleNoSpace.contains(keywordNoSpace) || keywordNoSpace.contains(titleNoSpace)) {
            return true;
        }

        // 3. 检查关键词中的主要字符是否大部分出现在标题中（模糊匹配）
        int matchCount = 0;
        for (char c : keywordChars) {
            if (c != ' ' && title.indexOf(c) >= 0) {
                matchCount++;
            }
        }
        // 如果超过70%的字符匹配，认为相关
        int nonSpaceCount = 0;
        for (char c : keywordChars) {
            if (c != ' ') nonSpaceCount++;
        }
        if (nonSpaceCount > 0 && (double) matchCount / nonSpaceCount >= 0.7) {
            return true;
        }

        return false;
    }

    private boolean isYellowContent(Map<String, Object> item) {
        String typeName = String.valueOf(item.getOrDefault("type_name", ""));
        String title = String.valueOf(item.getOrDefault("title", ""));
        String desc = String.valueOf(item.getOrDefault("desc", ""));
        String combined = typeName + "|" + title + "|" + desc;
        return YELLOW_CONTENT_PATTERN.matcher(combined).find();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchSource(Map<String, Object> source, String keyword) {
        String sourceName = (String) source.get("name");
        String api = (String) source.get("api");
        String sourceKey = source.get("key").toString();

        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String searchUrl = api + "?ac=videolist&wd=" + encodedKeyword;

            // 带浏览器模拟头发起请求，避免被 CDN/WAF 拦截（521/403 等）
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.set("Accept-Encoding", "gzip, deflate");
            headers.set("Connection", "keep-alive");
            headers.set("Cache-Control", "no-cache");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity;
            try {
                responseEntity = restTemplate.exchange(searchUrl, HttpMethod.GET, entity, String.class);
            } catch (Exception e) {
                log.warn("Failed to connect to source {}: {}", sourceName, e.getMessage());
                return Collections.emptyList();
            }

            String response = responseEntity.getBody();
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            // 检查响应是否为有效 JSON（过滤 "搜索关闭"、"暂不支持搜索" 等纯文本响应）
            response = response.trim();
            if (!response.startsWith("{") && !response.startsWith("[")) {
                log.debug("Source {} returned non-JSON response, skipping", sourceName);
                return Collections.emptyList();
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(response);
            } catch (Exception e) {
                log.debug("Source {} returned invalid JSON, skipping", sourceName);
                return Collections.emptyList();
            }

            JsonNode listNode = root.get("list");
            if (listNode == null || !listNode.isArray() || listNode.isEmpty()) {
                return Collections.emptyList();
            }

            // 获取分页信息，搜索后续页
            int maxPages = siteConfigService.getIntConfig("search_downstream_max_page", 1);
            int pageCount = root.has("pagecount") ? root.get("pagecount").asInt(1) : 1;

            List<Map<String, Object>> allResults = new ArrayList<>(parseResultList(listNode, sourceKey, sourceName));

            // 抓取后续页（如果配置允许）
            if (maxPages > 1 && pageCount > 1) {
                int pagesToFetch = Math.min(pageCount, maxPages);
                for (int page = 2; page <= pagesToFetch; page++) {
                    try {
                        String pageUrl = api + "?ac=videolist&wd=" + encodedKeyword + "&pg=" + page;
                        ResponseEntity<String> pageResponse = restTemplate.exchange(pageUrl, HttpMethod.GET, entity, String.class);
                        String pageBody = pageResponse.getBody();
                        if (pageBody != null && !pageBody.isBlank()) {
                            JsonNode pageRoot = objectMapper.readTree(pageBody.trim());
                            JsonNode pageList = pageRoot.get("list");
                            if (pageList != null && pageList.isArray() && !pageList.isEmpty()) {
                                allResults.addAll(parseResultList(pageList, sourceKey, sourceName));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to fetch page {} from source {}", page, sourceName);
                    }
                }
            }

            return allResults.stream().limit(50).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to search source {}: {}", sourceName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseResultList(JsonNode listNode, String sourceKey, String sourceName) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonNode item : listNode) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", item.has("vod_id") ? item.get("vod_id").asText() : "");
            result.put("title", item.has("vod_name") ? item.get("vod_name").asText("").trim().replaceAll("\\s+", " ") : "");
            result.put("poster", item.has("vod_pic") ? item.get("vod_pic").asText("") : "");
            result.put("year", extractYear(item));
            result.put("desc", cleanHtmlTags(item.has("vod_content") ? item.get("vod_content").asText("") : ""));
            result.put("type_name", item.has("type_name") ? item.get("type_name").asText("") : "");
            result.put("source", sourceKey);
            result.put("source_name", sourceName);

            // 解析播放列表 — 匹配参考项目的 $$$ / # / $ 分割逻辑，优先取 m3u8 链接
            String playUrl = item.has("vod_play_url") ? item.get("vod_play_url").asText("") : "";
            parsePlayUrl(playUrl, result);

            results.add(result);
        }
        return results;
    }

    /**
     * 解析 vod_play_url，匹配参考项目逻辑：
     * - 先用 $$$ 分割不同播放源
     * - 每个源用 # 分割不同集
     * - 每集用 $ 分割标题和链接
     * - 优先选取含 .m3u8 链接最多的那组源
     * - 如果没有 m3u8 链接，回退到任意链接最多的那组
     */
    private void parsePlayUrl(String playUrl, Map<String, Object> result) {
        if (playUrl == null || playUrl.isEmpty()) {
            result.put("episodes", Collections.emptyList());
            result.put("episodes_titles", Collections.emptyList());
            return;
        }

        // 按 $$$ 分割不同播放源
        String[] sourceGroups = playUrl.split("\\$\\$\\$");

        List<String> bestEpisodes = new ArrayList<>();
        List<String> bestTitles = new ArrayList<>();
        List<String> fallbackEpisodes = new ArrayList<>();
        List<String> fallbackTitles = new ArrayList<>();

        for (String group : sourceGroups) {
            List<String> m3u8Episodes = new ArrayList<>();
            List<String> m3u8Titles = new ArrayList<>();
            List<String> anyEpisodes = new ArrayList<>();
            List<String> anyTitles = new ArrayList<>();

            String[] episodes = group.split("#");
            for (String ep : episodes) {
                String[] parts = ep.split("\\$");
                if (parts.length >= 2) {
                    String title = parts[0];
                    String url = parts[1];
                    anyTitles.add(title);
                    anyEpisodes.add(url);
                    if (url.endsWith(".m3u8")) {
                        m3u8Titles.add(title);
                        m3u8Episodes.add(url);
                    }
                } else if (parts.length == 1 && !parts[0].isBlank()) {
                    anyTitles.add("第" + (anyTitles.size() + 1) + "集");
                    anyEpisodes.add(parts[0]);
                    if (parts[0].endsWith(".m3u8")) {
                        m3u8Titles.add("第" + (m3u8Titles.size() + 1) + "集");
                        m3u8Episodes.add(parts[0]);
                    }
                }
            }

            // 优先选 m3u8 链接最多的源组
            if (m3u8Episodes.size() > bestEpisodes.size()) {
                bestEpisodes = m3u8Episodes;
                bestTitles = m3u8Titles;
            }
            // 回退：任意链接最多的源组
            if (anyEpisodes.size() > fallbackEpisodes.size()) {
                fallbackEpisodes = anyEpisodes;
                fallbackTitles = anyTitles;
            }
        }

        if (!bestEpisodes.isEmpty()) {
            result.put("episodes", bestEpisodes);
            result.put("episodes_titles", bestTitles);
        } else {
            result.put("episodes", fallbackEpisodes);
            result.put("episodes_titles", fallbackTitles);
        }
    }

    private String extractYear(JsonNode item) {
        if (!item.has("vod_year")) return "unknown";
        String year = item.get("vod_year").asText("");
        // 提取4位数字年份
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}").matcher(year);
        return m.find() ? m.group() : "unknown";
    }

    private String cleanHtmlTags(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("<[^>]+>", "").trim();
    }
}
