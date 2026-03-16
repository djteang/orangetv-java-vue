package com.orangetv.controller.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.orangetv.dto.ApiResponse;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SiteConfigService siteConfigService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> result = new HashMap<>();

        String role = "guest";
        if (SecurityUtils.isOwner()) {
            role = "owner";
        } else if (SecurityUtils.isAdmin()) {
            role = "admin";
        } else if (SecurityUtils.isAuthenticated()) {
            role = "user";
        }

        result.put("Role", role);
        result.put("Config", siteConfigService.getAllConfig());

        // 结构化字段
        Map<String, Object> configSubscription = new HashMap<>();
        configSubscription.put("URL", siteConfigService.getConfigValue("config_subscription_url", ""));
        configSubscription.put("AutoUpdate", siteConfigService.getBooleanConfig("config_subscription_auto_update", false));
        configSubscription.put("LastCheck", siteConfigService.getConfigValue("config_subscription_last_check", ""));
        result.put("ConfigSubscribtion", configSubscription);

        result.put("ConfigFile", siteConfigService.getConfigValue("config_file", ""));

        String categoriesJson = siteConfigService.getConfigValue("custom_categories");
        if (categoriesJson != null && !categoriesJson.isBlank()) {
            try {
                result.put("CustomCategories", new com.fasterxml.jackson.databind.ObjectMapper().readValue(categoriesJson, Object.class));
            } catch (Exception e) {
                result.put("CustomCategories", java.util.Collections.emptyList());
            }
        } else {
            result.put("CustomCategories", java.util.Collections.emptyList());
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<Void>> updateConfig(@RequestBody Map<String, Object> request) throws JsonProcessingException {
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String type = "string";
            String strValue;

            if (value instanceof Boolean) {
                type = "boolean";
                strValue = value.toString();
            } else if (value instanceof Number) {
                type = "number";
                strValue = value.toString();
            } else if (value instanceof Map || value instanceof java.util.List) {
                type = "json";
                strValue = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
            } else {
                strValue = value != null ? value.toString() : null;
            }

            siteConfigService.setConfig(key, strValue, type, null);
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
