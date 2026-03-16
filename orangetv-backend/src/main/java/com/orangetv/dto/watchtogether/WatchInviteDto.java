package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 共同观影邀请消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchInviteDto {

    @JsonProperty("room_id")
    private String roomId;

    @JsonProperty("host_id")
    private String hostId;

    @JsonProperty("target_user_id")
    private String targetUserId;

    @JsonProperty("video_info")
    private VideoInfoDto videoInfo;

    private String token;

    private Long timestamp;
}
