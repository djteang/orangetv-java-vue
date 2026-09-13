package com.orangetv.controller.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.admin.ConfigFilePreview;
import com.orangetv.dto.admin.ConfigSyncResult;
import com.orangetv.exception.ApiException;
import com.orangetv.service.ConfigFileSyncService;
import com.orangetv.service.SiteConfigService;
import com.orangetv.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
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
    private final RestTemplate restTemplate;

    @PostMapping("/config_subscription/fetch")
    public ResponseEntity<ApiResponse<ConfigFilePreview>> fetchSubscription(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isBlank()) throw ApiException.badRequest("缺少订阅 URL");
        url = url.trim();
        var uri = ConfigFileSyncService.requireHttpUrl(url, "订阅地址");
        byte[] bytes;
        try {
            bytes = restTemplate.getForObject(uri, byte[].class);
        } catch (HttpStatusCodeException e) {
            throw new ApiException("拉取订阅失败：远程服务返回 HTTP " + e.getStatusCode().value(), HttpStatus.BAD_GATEWAY);
        } catch (ResourceAccessException e) {
            throw new ApiException("无法连接订阅地址或请求超时，请检查地址和后端网络连接", HttpStatus.BAD_GATEWAY);
        } catch (RestClientException e) {
            throw new ApiException("拉取订阅失败，请确认地址可直接访问 JSON 或 M3U 内容", HttpStatus.BAD_GATEWAY);
        }
        String content = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        ConfigFilePreview preview = configFileSyncService.prepareConfigFile(content, url);
        siteConfigService.setConfig("config_subscription_last_check",
                AppTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), "string", "订阅上次检查时间");
        return ResponseEntity.ok(ApiResponse.success(preview));
    }

    @PostMapping("/config_file")
    public ResponseEntity<ApiResponse<ConfigSyncResult>> saveConfigFile(@RequestBody SaveConfigFileRequest request) {
        try {
            ConfigSyncResult result = configFileSyncService.saveConfigFile(
                    request.configFile(), request.subscriptionUrl(), request.autoUpdate());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (DataAccessException e) {
            log.error("Failed to save config and synchronize sources", e);
            throw ApiException.internal("保存配置失败，未完成源同步，请检查数据库连接和字段约束");
        }
    }

    public record SaveConfigFileRequest(
            @JsonProperty("config_file") String configFile,
            @JsonProperty("config_subscription_url") String subscriptionUrl,
            @JsonProperty("config_subscription_auto_update") Boolean autoUpdate) {}
}
