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
public class SkipConfigDto {

    private String key;
    private Boolean enable;

    @JsonProperty("intro_time")
    private Integer introTime;

    @JsonProperty("outro_time")
    private Integer outroTime;
}
