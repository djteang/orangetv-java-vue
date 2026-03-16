package com.orangetv.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存跳过配置请求
 * 支持两种格式：
 * 1. 嵌套格式: { key: "...", config: { enable, intro_time, outro_time } }
 * 2. 扁平格式: { key: "...", enable: true, intro_time: 90, outro_time: 60 }
 */
@Data
public class SaveSkipConfigRequest {

    @NotBlank(message = "key不能为空")
    private String key;

    // 嵌套格式的 config 对象（可选）
    private SkipConfigDto config;

    // 扁平格式的字段（当 config 为 null 时使用）
    private Boolean enable;

    @JsonProperty("intro_time")
    private Integer introTime;

    @JsonProperty("outro_time")
    private Integer outroTime;

    /**
     * 获取有效的 SkipConfigDto，优先使用嵌套的 config，否则从扁平字段构造
     */
    public SkipConfigDto getEffectiveConfig() {
        if (config != null) {
            return config;
        }
        // 从扁平字段构造
        return SkipConfigDto.builder()
                .key(key)
                .enable(enable)
                .introTime(introTime)
                .outroTime(outroTime)
                .build();
    }
}
