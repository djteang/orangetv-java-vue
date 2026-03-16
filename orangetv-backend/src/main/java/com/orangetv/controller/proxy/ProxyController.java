package com.orangetv.controller.proxy;

import com.orangetv.config.SkipWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
@SkipWrapper
public class ProxyController {

    private final RestTemplate restTemplate;

    @GetMapping("/m3u8")
    public void proxyM3u8(
            @RequestParam String url,
            @RequestParam(required = false) String ua,
            @RequestParam(required = false) String referer,
            HttpServletResponse response) {
        try {
            String decodedUrl = decodeUrl(url);
            log.info("Proxying M3U8: {}", decodedUrl);
            HttpHeaders headers = buildHeaders(ua, referer);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            // 使用 byte[] 获取以避免 Content-Type 不匹配问题
            ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                    decodedUrl, HttpMethod.GET, entity, byte[].class);

            byte[] body = proxyResponse.getBody();
            if (body == null || body.length == 0) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Empty response from upstream");
                return;
            }

            // 检查是否真的是 m3u8 内容（应该以 #EXTM3U 开头）
            String contentPreview = new String(body, 0, Math.min(50, body.length), StandardCharsets.UTF_8).trim();

            if (contentPreview.startsWith("#EXTM3U") || contentPreview.startsWith("#EXT")) {
                // 这是有效的 m3u8 内容
                String content = new String(body, StandardCharsets.UTF_8);
                // 移除 BOM 字符
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }
                log.info("Valid M3U8 content, length: {}", content.length());
                // 重写 M3U8 中的相对路径
                content = rewriteM3u8Content(content, decodedUrl);

                response.setContentType("application/vnd.apple.mpegurl");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(content);
            } else {
                // 不是 m3u8，可能是直接的视频流，直接透传
                log.info("Not M3U8 content, proxying as binary stream. First bytes: {}", contentPreview.substring(0, Math.min(20, contentPreview.length())));

                // 获取上游响应的 Content-Type
                MediaType upstreamContentType = proxyResponse.getHeaders().getContentType();
                if (upstreamContentType != null) {
                    response.setContentType(upstreamContentType.toString());
                } else {
                    response.setContentType("video/mp4");
                }

                response.setContentLength(body.length);
                response.getOutputStream().write(body);
            }
        } catch (Exception e) {
            log.error("M3U8 proxy error for URL {}: {}", url, e.getMessage());
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e.getMessage());
                }
            } catch (IOException ignored) {}
        }
    }

    @GetMapping("/segment")
    public void proxySegment(
            @RequestParam String url,
            @RequestParam(required = false) String ua,
            @RequestParam(required = false) String referer,
            HttpServletResponse response) {
        try {
            String decodedUrl = decodeUrl(url);
            log.debug("Proxying segment: {}", decodedUrl);
            HttpHeaders headers = buildHeaders(ua, referer);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                    decodedUrl, HttpMethod.GET, entity, byte[].class);

            response.setContentType("video/mp2t");

            if (proxyResponse.getBody() != null) {
                response.getOutputStream().write(proxyResponse.getBody());
            }
        } catch (Exception e) {
            // 忽略客户端断开连接的异常
            String exName = e.getClass().getName();
            if (!exName.contains("ClientAbortException")) {
                log.warn("Segment proxy error: {}", e.getMessage());
            }
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error");
                }
            } catch (IOException ignored) {}
        }
    }

    @GetMapping("/video")
    public void proxyVideo(
            @RequestParam String url,
            @RequestParam(required = false) String ua,
            @RequestParam(required = false) String referer,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            String decodedUrl = decodeUrl(url);
            HttpHeaders headers = buildHeaders(ua, referer);

            // 处理 Range 请求
            String range = request.getHeader("Range");
            if (range != null) {
                headers.set("Range", range);
            }

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                    decodedUrl, HttpMethod.GET, entity, byte[].class);

            // 设置响应头
            response.setContentType("video/mp4");
            response.setHeader("Accept-Ranges", "bytes");

            if (proxyResponse.getHeaders().getContentLength() > 0) {
                response.setContentLengthLong(proxyResponse.getHeaders().getContentLength());
            }

            String contentRange = proxyResponse.getHeaders().getFirst("Content-Range");
            if (contentRange != null) {
                response.setHeader("Content-Range", contentRange);
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
            }

            if (proxyResponse.getBody() != null) {
                response.getOutputStream().write(proxyResponse.getBody());
            }
        } catch (Exception e) {
            log.error("Video proxy error: {}", e.getMessage());
            try {
                response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error");
            } catch (IOException ignored) {}
        }
    }

    @GetMapping("/logo")
    public void proxyLogo(
            @RequestParam String url,
            HttpServletResponse response) {
        try {
            String decodedUrl = decodeUrl(url);
            ResponseEntity<byte[]> proxyResponse = restTemplate.getForEntity(decodedUrl, byte[].class);

            String contentType = "image/png";
            if (decodedUrl.endsWith(".jpg") || decodedUrl.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (decodedUrl.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (decodedUrl.endsWith(".webp")) {
                contentType = "image/webp";
            }

            response.setContentType(contentType);
            response.setHeader("Cache-Control", "public, max-age=86400");

            if (proxyResponse.getBody() != null) {
                response.getOutputStream().write(proxyResponse.getBody());
            }
        } catch (Exception e) {
            log.error("Logo proxy error: {}", e.getMessage());
            try {
                response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error");
            } catch (IOException ignored) {}
        }
    }

    @GetMapping("/key")
    public void proxyKey(
            @RequestParam String url,
            @RequestParam(required = false) String ua,
            @RequestParam(required = false) String referer,
            HttpServletResponse response) {
        try {
            String decodedUrl = decodeUrl(url);
            HttpHeaders headers = buildHeaders(ua, referer);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                    decodedUrl, HttpMethod.GET, entity, byte[].class);

            response.setContentType("application/octet-stream");

            if (proxyResponse.getBody() != null) {
                response.getOutputStream().write(proxyResponse.getBody());
            }
        } catch (Exception e) {
            log.error("Key proxy error: {}", e.getMessage());
            try {
                response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error");
            } catch (IOException ignored) {}
        }
    }

    private String decodeUrl(String url) {
        // 优先使用 URL 解码（与参考项目保持一致）
        try {
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
            // 如果解码后是有效的 URL，则返回
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                return decoded;
            }
        } catch (Exception ignored) {}

        // 尝试 Base64 解码（向后兼容）
        try {
            String decoded = new String(Base64.getDecoder().decode(url), StandardCharsets.UTF_8);
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                return decoded;
            }
        } catch (Exception ignored) {}

        // 如果都不行，返回原始值
        return url;
    }

    private HttpHeaders buildHeaders(String ua, String referer) {
        HttpHeaders headers = new HttpHeaders();
        if (ua != null && !ua.isEmpty()) {
            headers.set("User-Agent", ua);
        } else {
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        }
        if (referer != null && !referer.isEmpty()) {
            headers.set("Referer", referer);
        }
        return headers;
    }

    private String rewriteM3u8Content(String content, String baseUrl) {
        // 获取基础 URL
        String base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);

        StringBuilder result = new StringBuilder();
        // 处理 Windows 和 Unix 换行符
        String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                result.append("\n");
                continue;
            }
            if (line.startsWith("#")) {
                // 处理 URI 属性
                if (line.contains("URI=\"")) {
                    line = rewriteUriAttribute(line, base);
                }
                result.append(line).append("\n");
            } else {
                // 这是一个 URL 行
                if (!line.startsWith("http")) {
                    line = base + line;
                }
                // 使用 URL 编码（与参考项目保持一致）
                String encoded = URLEncoder.encode(line, StandardCharsets.UTF_8);
                // 判断是 m3u8 还是 ts 分片
                String lowerLine = line.toLowerCase();
                if (lowerLine.contains(".m3u8") || lowerLine.contains("m3u8")) {
                    result.append("/api/proxy/m3u8?url=").append(encoded).append("\n");
                } else {
                    // 其他都作为分片处理（.ts 或其他格式）
                    result.append("/api/proxy/segment?url=").append(encoded).append("\n");
                }
            }
        }
        return result.toString();
    }

    private String rewriteUriAttribute(String line, String base) {
        int start = line.indexOf("URI=\"") + 5;
        int end = line.indexOf("\"", start);
        if (start > 4 && end > start) {
            String uri = line.substring(start, end);
            if (!uri.startsWith("http")) {
                uri = base + uri;
            }
            // 使用 URL 编码
            String encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8);
            return line.substring(0, start) + "/api/proxy/key?url=" + encoded + line.substring(end);
        }
        return line;
    }
}
