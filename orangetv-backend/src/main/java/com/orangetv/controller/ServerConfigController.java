package com.orangetv.controller;

import com.orangetv.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServerConfigController {

    private final SiteConfigService siteConfigService;

    @Value("${orangetv.site-name:OrangeTV}")
    private String siteName;

    @Value("${orangetv.require-device-code:false}")
    private boolean requireDeviceCode;

    @Value("${orangetv.allow-registration:true}")
    private boolean allowRegistration;

    @Value("${oauth.linuxdo.client-id:}")
    private String linuxDoClientId;

    @GetMapping("/server-config")
    public ResponseEntity<Map<String, Object>> getServerConfig() {
        // 获取数据库中的所有配置
        Map<String, Object> config = new HashMap<>(siteConfigService.getAllConfig());

        // 设置默认值（如果数据库中没有配置）
        config.putIfAbsent("site_name", siteName);
        config.putIfAbsent("siteName", config.get("site_name"));
        config.putIfAbsent("require_device_code", requireDeviceCode);
        config.putIfAbsent("requireDeviceCode", config.get("require_device_code"));
        config.putIfAbsent("allow_registration", allowRegistration);
        config.putIfAbsent("allowRegistration", config.get("allow_registration"));
        config.put("storageType", "mysql");
        boolean disableYellowFilter = Boolean.TRUE.equals(siteConfigService.getBooleanConfig("disable_yellow_filter", false));
        config.put("disableYellowFilter", disableYellowFilter);
        config.put("disable_yellow_filter", disableYellowFilter);
        boolean yellowFilterApplyGlobally = Boolean.TRUE.equals(siteConfigService.getBooleanConfig("yellow_filter_apply_globally", disableYellowFilter));
        config.put("yellowFilterApplyGlobally", yellowFilterApplyGlobally);
        config.put("yellow_filter_apply_globally", yellowFilterApplyGlobally);

        // 检查是否配置了 LinuxDo OAuth（需要同时满足：站点配置启用 + 配置了 client-id）
        Boolean enableLinuxDoLogin = siteConfigService.getBooleanConfig("enable_linuxdo_login", false);
        // 只有在配置了 client-id 且站点配置启用时才真正启用
        boolean actuallyEnabled = enableLinuxDoLogin && linuxDoClientId != null && !linuxDoClientId.isEmpty();
        config.put("enableLinuxDoLogin", actuallyEnabled);

        // 弹幕配置
        Boolean enableDanmu = siteConfigService.getBooleanConfig("enable_danmu", false);
        config.put("enableDanmu", enableDanmu);
        config.put("enable_danmu", enableDanmu);

        String danmuApiUrl = siteConfigService.getConfigValue("danmu_api_url");
        config.put("danmuApiUrl", danmuApiUrl != null ? danmuApiUrl : "");
        config.put("danmu_api_url", danmuApiUrl != null ? danmuApiUrl : "");

        return ResponseEntity.ok(config);
    }
}
