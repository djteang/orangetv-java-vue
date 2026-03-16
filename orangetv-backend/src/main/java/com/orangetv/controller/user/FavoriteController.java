package com.orangetv.controller.user;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.user.FavoriteDto;
import com.orangetv.dto.user.SaveFavoriteRequest;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<?> getFavorites(@RequestParam(required = false) String key) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (key != null && !key.isEmpty()) {
            FavoriteDto favorite = favoriteService.getFavorite(userId, key);
            return ResponseEntity.ok(favorite);
        }

        Map<String, FavoriteDto> favorites = favoriteService.getAllFavorites(userId);
        return ResponseEntity.ok(favorites);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveFavorite(@Valid @RequestBody SaveFavoriteRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        favoriteService.saveFavorite(userId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteFavorite(@RequestParam(required = false) String key) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (key != null && !key.isEmpty()) {
            favoriteService.deleteFavorite(userId, key);
        } else {
            favoriteService.deleteAllFavorites(userId);
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
