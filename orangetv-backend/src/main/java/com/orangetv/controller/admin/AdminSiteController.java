package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/site")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminSiteController {

    private final SiteConfigService siteConfigService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> updateSiteConfig(@RequestBody Map<String, Object> request) {
        if (request.containsKey("SiteName")) {
            siteConfigService.setConfig("site_name", (String) request.get("SiteName"));
        }
        if (request.containsKey("Announcement")) {
            siteConfigService.setConfig("announcement", (String) request.get("Announcement"));
        }
        if (request.containsKey("RequireDeviceCode")) {
            siteConfigService.setConfig("require_device_code",
                    String.valueOf(request.get("RequireDeviceCode")), "boolean", null);
        }
        if (request.containsKey("DisableYellowFilter")) {
            siteConfigService.setConfig("disable_yellow_filter",
                    String.valueOf(request.get("DisableYellowFilter")), "boolean", null);
        }
        if (request.containsKey("FluidSearch")) {
            siteConfigService.setConfig("fluid_search",
                    String.valueOf(request.get("FluidSearch")), "boolean", null);
        }
        if (request.containsKey("SearchDownstreamMaxPage")) {
            siteConfigService.setConfig("search_downstream_max_page",
                    String.valueOf(request.get("SearchDownstreamMaxPage")), "number", null);
        }
        if (request.containsKey("SiteInterfaceCacheTime")) {
            siteConfigService.setConfig("site_interface_cache_time",
                    String.valueOf(request.get("SiteInterfaceCacheTime")), "number", null);
        }
        if (request.containsKey("EnableLinuxDoLogin")) {
            siteConfigService.setConfig("enable_linuxdo_login",
                    String.valueOf(request.get("EnableLinuxDoLogin")), "boolean", null);
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
