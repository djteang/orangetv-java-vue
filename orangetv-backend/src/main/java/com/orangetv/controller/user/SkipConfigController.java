package com.orangetv.controller.user;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.user.SaveSkipConfigRequest;
import com.orangetv.dto.user.SkipConfigDto;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.SkipConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skipconfigs")
@RequiredArgsConstructor
public class SkipConfigController {

    private final SkipConfigService skipConfigService;

    @GetMapping
    public ResponseEntity<?> getSkipConfigs(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String id) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (source != null && id != null) {
            String key = source + "+" + id;
            SkipConfigDto config = skipConfigService.getSkipConfig(userId, key);
            return ResponseEntity.ok(config);
        }

        Map<String, SkipConfigDto> configs = skipConfigService.getAllSkipConfigs(userId);
        return ResponseEntity.ok(configs);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveSkipConfig(@Valid @RequestBody SaveSkipConfigRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        skipConfigService.saveSkipConfig(userId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteSkipConfig(@RequestParam String key) {
        Long userId = SecurityUtils.getCurrentUserId();
        skipConfigService.deleteSkipConfig(userId, key);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
