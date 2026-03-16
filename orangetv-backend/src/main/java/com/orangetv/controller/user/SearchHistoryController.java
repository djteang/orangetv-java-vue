package com.orangetv.controller.user;

import com.orangetv.dto.ApiResponse;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/searchhistory")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @GetMapping
    public ResponseEntity<List<String>> getSearchHistory() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> history = searchHistoryService.getSearchHistory(userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> addSearchHistory(@RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String keyword = request.get("keyword");
        searchHistoryService.addSearchHistory(userId, keyword);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteSearchHistory(@RequestParam(required = false) String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (keyword != null && !keyword.isEmpty()) {
            searchHistoryService.deleteSearchHistory(userId, keyword);
        } else {
            searchHistoryService.deleteAllSearchHistory(userId);
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
