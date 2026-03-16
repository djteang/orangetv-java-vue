package com.orangetv.controller;

import com.orangetv.config.SkipWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api")
@SkipWrapper
public class ImageProxyController {

    @GetMapping("/image-proxy")
    public void proxyImage(@RequestParam String url, HttpServletResponse response) {
        try {
            String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);

            HttpURLConnection conn = (HttpURLConnection) URI.create(decodedUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            // 豆瓣图片需要 Referer 头，否则返回 403/418
            conn.setRequestProperty("Referer", "https://movie.douban.com/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                log.warn("Image proxy upstream returned {}: {}", statusCode, decodedUrl);
                response.sendError(HttpStatus.BAD_GATEWAY.value(), "Upstream returned " + statusCode);
                return;
            }

            // 使用上游返回的 Content-Type，如果没有则根据 URL 推断
            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                contentType = "image/jpeg";
                if (decodedUrl.contains(".png")) {
                    contentType = "image/png";
                } else if (decodedUrl.contains(".gif")) {
                    contentType = "image/gif";
                } else if (decodedUrl.contains(".webp")) {
                    contentType = "image/webp";
                } else if (decodedUrl.contains(".svg")) {
                    contentType = "image/svg+xml";
                }
            }

            response.setContentType(contentType);
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Cache-Control", "public, max-age=86400");

            try (InputStream in = conn.getInputStream()) {
                in.transferTo(response.getOutputStream());
            }
        } catch (Exception e) {
            log.error("Image proxy error: {}", e.getMessage());
            try {
                response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error");
            } catch (IOException ignored) {}
        }
    }
}
