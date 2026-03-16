package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放同步状态 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchSyncStateDto {

    @JsonProperty("is_playing")
    private Boolean isPlaying;

    @JsonProperty("current_time")
    private Double currentTime;

    private Double volume;

    @JsonProperty("updated_by")
    private String updatedBy;

    private Long timestamp;
}
