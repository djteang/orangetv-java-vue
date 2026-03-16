package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 互动特效 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchEffectDto {

    private String id;

    /**
     * 特效类型: heart, like, clap, fire, laugh, wow
     */
    private String type;

    private String sender;

    private Long timestamp;
}
