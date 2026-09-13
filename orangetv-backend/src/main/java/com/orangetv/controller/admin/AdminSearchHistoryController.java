package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.admin.AdminUserHistoryPage;
import com.orangetv.dto.admin.AdminUserHistoryPage.SiteSearchEntry;
import com.orangetv.service.AdminUserHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/search-history")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminSearchHistoryController {

    private final AdminUserHistoryService historyService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserHistoryPage<SiteSearchEntry>>> getSearchHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(historyService.getAllSearchHistory(page, size)));
    }
}
