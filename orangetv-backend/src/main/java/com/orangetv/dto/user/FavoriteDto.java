package com.orangetv.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteDto {

    private String key;

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
}
