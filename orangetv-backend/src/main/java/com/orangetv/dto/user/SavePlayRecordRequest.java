package com.orangetv.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存播放记录请求
 * 支持两种格式：
 * 1. 嵌套格式: { key: "...", record: { title, source_name, ... } }
 * 2. 扁平格式: { key: "...", title: "...", source_name: "...", ... }
 */
@Data
public class SavePlayRecordRequest {

    @NotBlank(message = "key不能为空")
    private String key;

    // 嵌套格式的 record 对象（可选）
    private PlayRecordDto record;

    // 扁平格式的字段（当 record 为 null 时使用）
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

    /**
     * 获取有效的 PlayRecordDto，优先使用嵌套的 record，否则从扁平字段构造
     */
    public PlayRecordDto getEffectiveRecord() {
        if (record != null) {
            return record;
        }
        // 从扁平字段构造
        return PlayRecordDto.builder()
                .key(key)
                .title(title)
                .sourceName(sourceName)
                .cover(cover)
                .year(year)
                .index(index)
                .totalEpisodes(totalEpisodes)
                .playTime(playTime)
                .totalTime(totalTime)
                .saveTime(saveTime)
                .searchTitle(searchTitle)
                .build();
    }
}
