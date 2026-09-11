package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.admin.AdminUserHistoryPage;
import com.orangetv.dto.admin.AdminUserHistoryPage.PlayEntry;
import com.orangetv.dto.admin.AdminUserHistoryPage.SearchEntry;
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
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminUserHistoryController {

    private final AdminUserHistoryService historyService;

    @GetMapping("/search-history")
    public ResponseEntity<ApiResponse<AdminUserHistoryPage<SearchEntry>>> getSearchHistory(
            @RequestParam String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(historyService.getSearchHistory(username, page, size)));
    }

    @GetMapping("/play-history")
    public ResponseEntity<ApiResponse<AdminUserHistoryPage<PlayEntry>>> getPlayHistory(
            @RequestParam String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(historyService.getPlayHistory(username, page, size)));
    }
}
