package com.orangetv.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存收藏请求
 * 支持两种格式：
 * 1. 嵌套格式: { key: "...", favorite: { title, source_name, ... } }
 * 2. 扁平格式: { key: "...", title: "...", source_name: "...", ... }
 */
@Data
public class SaveFavoriteRequest {

    @NotBlank(message = "key不能为空")
    private String key;

    // 嵌套格式的 favorite 对象（可选）
    private FavoriteDto favorite;

    // 扁平格式的字段（当 favorite 为 null 时使用）
    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("total_episodes")
    private Integer totalEpisodes;

    private String title;
    private String year;
    private String cover;

    @JsonProperty("save_time")
    private Long saveTime;

    @JsonProperty("search_title")
    private String searchTitle;

    private String origin;

    /**
     * 获取有效的 FavoriteDto，优先使用嵌套的 favorite，否则从扁平字段构造
     */
    public FavoriteDto getEffectiveFavorite() {
        if (favorite != null) {
            return favorite;
        }
        // 从扁平字段构造
        return FavoriteDto.builder()
                .key(key)
                .sourceName(sourceName)
                .totalEpisodes(totalEpisodes)
                .title(title)
                .year(year)
                .cover(cover)
                .saveTime(saveTime)
                .searchTitle(searchTitle)
                .origin(origin)
                .build();
    }
}
