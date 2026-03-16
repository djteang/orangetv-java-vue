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
public class PlayRecordDto {

    private String key;
    private String title;

    @JsonProperty("source_name")
    private String sourceName;

    private String cover;
    private String year;
    private Integer index;

    @JsonProperty("total_episodes")
    private Integer totalEpisodes;

    @JsonProperty("play_time")
    private Double playTime;

    @JsonProperty("total_time")
    private Double totalTime;

    @JsonProperty("save_time")
    private Long saveTime;

    @JsonProperty("search_title")
    private String searchTitle;
}
