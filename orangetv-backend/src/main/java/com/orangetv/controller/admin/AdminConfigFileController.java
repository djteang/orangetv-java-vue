package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.service.ConfigFileSyncService;
import com.orangetv.service.SiteConfigService;
import com.orangetv.util.ContentDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class AdminConfigFileController {

    private final SiteConfigService siteConfigService;
    private final ConfigFileSyncService configFileSyncService;

    @PostMapping("/config_subscription/fetch")
    public ResponseEntity<ApiResponse<String>> fetchSubscription(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少订阅 URL"));
            }
            RestTemplate restTemplate = new RestTemplate();
            String content = restTemplate.getForObject(url, String.class);
            // Decode base58/base64 content before returning
            content = ContentDecoder.tryDecode(content);
            // 更新上次检查时间
            siteConfigService.setConfig("config_subscription_last_check",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "string", "订阅上次检查时间");
            return ResponseEntity.ok(ApiResponse.success(content));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "拉取订阅失败: " + e.getMessage()));
        }
    }

    @PostMapping("/config_file")
    public ResponseEntity<ApiResponse<Void>> saveConfigFile(@RequestBody Map<String, Object> request) {
        try {
            String configFile = (String) request.get("config_file");
            if (configFile != null) {
                siteConfigService.setConfig("config_file", configFile, "string", "配置文件内容");

                // Decode and sync to video/live sources
                String decoded = ContentDecoder.tryDecode(configFile);
                try {
                    configFileSyncService.syncFromConfigFile(decoded);
                } catch (Exception e) {
                    log.warn("Config file sync failed (non-fatal): {}", e.getMessage());
                }
            }

            String subscriptionUrl = (String) request.get("config_subscription_url");
            if (subscriptionUrl != null) {
                siteConfigService.setConfig("config_subscription_url", subscriptionUrl, "string", "配置订阅 URL");
            }

            Object autoUpdate = request.get("config_subscription_auto_update");
            if (autoUpdate != null) {
                siteConfigService.setConfig("config_subscription_auto_update", autoUpdate.toString(), "boolean", "配置订阅自动更新");
            }

            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "保存失败: " + e.getMessage()));
        }
    }
}
