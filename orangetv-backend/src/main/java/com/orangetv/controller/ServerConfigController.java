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

        // 检查是否配置了 LinuxDo OAuth（如果配置了 client-id 则启用）
        boolean enableLinuxDoLogin = linuxDoClientId != null && !linuxDoClientId.isEmpty();
        config.put("enableLinuxDoLogin", enableLinuxDoLogin);

        return ResponseEntity.ok(config);
    }
}
