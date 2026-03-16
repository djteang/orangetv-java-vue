package com.orangetv.controller.live;

import com.orangetv.config.SkipWrapper;
import com.orangetv.service.LiveService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService liveService;
    private final RestTemplate restTemplate;

    @GetMapping("/sources")
    public ResponseEntity<List<Map<String, Object>>> getSources() {
        return ResponseEntity.ok(liveService.getSources());
    }

    /**
     * 获取所有直播分类（从所有启用的直播源中解析 M3U 并提取分组）
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(liveService.getCategories());
    }

    /**
     * 获取指定分类下的频道列表
     */
    @GetMapping("/sources/{category}")
    public ResponseEntity<List<Map<String, Object>>> getSourcesByCategory(@PathVariable String category) {
        return ResponseEntity.ok(liveService.getChannelsByCategory(category));
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels(@RequestParam String source) {
        return ResponseEntity.ok(liveService.getChannels(source));
    }

    @GetMapping("/epg")
    public ResponseEntity<Map<String, Object>> getEpg(@RequestParam String source) {
        return ResponseEntity.ok(liveService.getEpg(source));
    }

    @GetMapping("/precheck")
    public ResponseEntity<Map<String, Object>> precheck(@RequestParam String url) {
        return ResponseEntity.ok(liveService.precheck(url));
    }

    /**
     * 代理 HLS 流 - 直接处理而非重定向
     */
    @GetMapping("/proxy")
    @SkipWrapper
    public void proxyStream(
            @RequestParam String url,
            HttpServletResponse response) {
        try {
            log.info("Live proxy request: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 判断是 m3u8 还是 ts 分片
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains(".m3u8") || lowerUrl.contains("m3u8")) {
                // 获取 m3u8 内容
                ResponseEntity<String> proxyResponse = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                String content = proxyResponse.getBody();
                if (content != null) {
                    // 重写 m3u8 内容
                    content = rewriteM3u8Content(content, url);
                }

                response.setContentType("application/vnd.apple.mpegurl");
                response.setCharacterEncoding("UTF-8");
                if (content != null) {
                    response.getWriter().write(content);
                }
            } else {
                // 获取 ts 分片或其他二进制内容
                ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                        url, HttpMethod.GET, entity, byte[].class);

                response.setContentType("video/mp2t");
                if (proxyResponse.getBody() != null) {
                    response.getOutputStream().write(proxyResponse.getBody());
                }
            }
        } catch (Exception e) {
            String exName = e.getClass().getName();
            if (!exName.contains("ClientAbortException")) {
                log.warn("Live proxy error: {} - {}", url, e.getMessage());
            }
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error");
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * 重写 m3u8 内容，将相对路径转换为代理路径
     */
    private String rewriteM3u8Content(String content, String baseUrl) {
        String base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);

        StringBuilder result = new StringBuilder();
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                result.append("\n");
                continue;
            }

            if (line.startsWith("#")) {
                // 处理 URI 属性 (如加密密钥)
                if (line.contains("URI=\"")) {
                    line = rewriteUriAttribute(line, base);
                }
                result.append(line).append("\n");
            } else {
                // URL 行
                String fullUrl;
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    fullUrl = line;
                } else {
                    fullUrl = base + line;
                }
                // 使用 URL 编码（与前端一致）
                String encodedUrl = java.net.URLEncoder.encode(fullUrl, StandardCharsets.UTF_8);
                result.append("/api/live/proxy?url=").append(encodedUrl).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * 重写 URI 属性
     */
    private String rewriteUriAttribute(String line, String base) {
        int start = line.indexOf("URI=\"") + 5;
        int end = line.indexOf("\"", start);
        if (start > 4 && end > start) {
            String uri = line.substring(start, end);
            String fullUrl;
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                fullUrl = uri;
            } else {
                fullUrl = base + uri;
            }
            String encodedUrl = java.net.URLEncoder.encode(fullUrl, StandardCharsets.UTF_8);
            return line.substring(0, start) + "/api/live/proxy?url=" + encodedUrl + line.substring(end);
        }
        return line;
    }
}
