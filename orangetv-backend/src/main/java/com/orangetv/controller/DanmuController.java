package com.orangetv.controller;

import com.orangetv.dto.ApiResponse;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.DanmuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/danmu")
@RequiredArgsConstructor
public class DanmuController {

    private final DanmuService danmuService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDanmus(@RequestParam String id) {
        return ResponseEntity.ok(danmuService.getDanmus(id));
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
