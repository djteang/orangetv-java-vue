package com.orangetv.controller;

import com.orangetv.config.SkipWrapper;
import com.orangetv.dto.ApiResponse;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.DanmuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/danmu")
@RequiredArgsConstructor
public class DanmuController {

    private final DanmuService danmuService;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDanmus(@RequestParam String id) {
        return ResponseEntity.ok(danmuService.getDanmus(id));
    }

    /**
     * 代理外部弹幕 API 请求，解决 HTTPS 页面访问 HTTP API 的 Mixed Content 问题
     */
    @SkipWrapper
    @GetMapping("/proxy")
    public ResponseEntity<String> proxyDanmuApi(@RequestParam String url) {
        try {
            if (restTemplate == null) {
                restTemplate = new RestTemplate();
            }

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json, text/plain, */*");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );

            // 返回响应，保持原始内容类型
            return ResponseEntity
                .status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> addDanmu(@RequestBody Map<String, Object> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String videoId = (String) request.get("id");
        String content = (String) request.get("content");
        Double timePoint = request.get("time") != null ? ((Number) request.get("time")).doubleValue() : 0.0;
        String color = (String) request.get("color");
        Integer type = request.get("type") != null ? ((Number) request.get("type")).intValue() : 0;

        Map<String, Object> danmu = danmuService.addDanmu(userId, videoId, content, timePoint, color, type);
        return ResponseEntity.ok(ApiResponse.success(danmu));
    }
}
