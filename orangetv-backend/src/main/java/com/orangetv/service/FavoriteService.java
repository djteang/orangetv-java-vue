package com.orangetv.service;

import com.orangetv.dto.user.FavoriteDto;
import com.orangetv.dto.user.SaveFavoriteRequest;
import com.orangetv.entity.Favorite;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.FavoriteRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, FavoriteDto> getAllFavorites(Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, FavoriteDto> result = new LinkedHashMap<>();

        for (Favorite fav : favorites) {
            result.put(fav.getVideoKey(), toDto(fav));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public FavoriteDto getFavorite(Long userId, String key) {
        return favoriteRepository.findByUserIdAndVideoKey(userId, key)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void saveFavorite(Long userId, SaveFavoriteRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        Favorite favorite = favoriteRepository.findByUserIdAndVideoKey(userId, request.getKey())
                .orElse(Favorite.builder()
                        .user(user)
                        .videoKey(request.getKey())
                        .build());

        FavoriteDto dto = request.getEffectiveFavorite();
        favorite.setTitle(dto.getTitle());
        favorite.setCover(dto.getCover());
        favorite.setApiName(dto.getSourceName());
        favorite.setVodRemarks(dto.getTotalEpisodes() != null ? dto.getTotalEpisodes().toString() : null);

        favoriteRepository.save(favorite);
    }

    @Transactional
    public void deleteFavorite(Long userId, String key) {
        favoriteRepository.findByUserIdAndVideoKey(userId, key)
                .ifPresent(favoriteRepository::delete);
    }

    @Transactional
    public void deleteAllFavorites(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        favoriteRepository.deleteAllByUser(user);
    }

    private FavoriteDto toDto(Favorite favorite) {
        return FavoriteDto.builder()
                .key(favorite.getVideoKey())
                .title(favorite.getTitle())
                .cover(favorite.getCover())
                .sourceName(favorite.getApiName())
                .totalEpisodes(favorite.getVodRemarks() != null ?
                        Integer.parseInt(favorite.getVodRemarks()) : null)
                .saveTime(favorite.getCreatedAt() != null ?
                        favorite.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 : null)
                .build();
    }
}
