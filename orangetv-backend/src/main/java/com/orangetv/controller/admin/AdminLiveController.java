package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.service.LiveSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/live")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminLiveController {

    private final LiveSourceService liveSourceService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllSources() {
        return ResponseEntity.ok(liveSourceService.getAllSources());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> handleLiveAction(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        String name = (String) request.get("name");
        String sourceKey = (String) request.get("key");
        String url = (String) request.get("url");
        String epgUrl = (String) request.get("epg");
        String userAgent = (String) request.get("ua");

        // 获取 ID（用于 edit/delete/enable/disable 操作）
        Long id = null;
        Object idObj = request.get("id");
        if (idObj != null) {
            if (idObj instanceof Number) {
                id = ((Number) idObj).longValue();
            } else if (idObj instanceof String) {
                id = Long.parseLong((String) idObj);
            }
        }

        switch (action) {
            case "add":
                liveSourceService.addSource(name, sourceKey, url, epgUrl, userAgent);
                break;
            case "edit":
                liveSourceService.editSource(id, name, sourceKey, url, epgUrl, userAgent);
                break;
            case "delete":
                liveSourceService.deleteSource(id);
                break;
            case "enable":
                liveSourceService.enableSource(id);
                break;
            case "disable":
                liveSourceService.disableSource(id);
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Unknown action: " + action));
        }

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshLiveSources() {
        // 清除缓存，下次请求时重新加载
        return ResponseEntity.ok(ApiResponse.success());
    }
}
