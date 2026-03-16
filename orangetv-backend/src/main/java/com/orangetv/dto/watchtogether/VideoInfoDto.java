package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 共同观影视频信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoInfoDto {

    private String title;

    private String source;

    private String id;

    @JsonProperty("episode_index")
    private Integer episodeIndex;

    @JsonProperty("video_url")
    private String videoUrl;

    private String cover;
}
