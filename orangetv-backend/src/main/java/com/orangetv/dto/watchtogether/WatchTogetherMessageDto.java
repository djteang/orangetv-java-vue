package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 共同观影 WebSocket 消息 DTO
 * 统一的消息格式，用于所有共同观影相关的 WebSocket 通信
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchTogetherMessageDto {

    /**
     * 消息类型:
     * - watch_invite: 邀请观看
     * - watch_invite_accept: 接受邀请
     * - watch_invite_reject: 拒绝邀请
     * - watch_sync: 播放同步
     * - watch_effect: 互动特效
     * - watch_leave: 离开房间
     * - watch_chat: 聊天消息
     */
    private String type;

    @JsonProperty("room_id")
    private String roomId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("target_user_id")
    private String targetUserId;

    /**
     * 邀请相关数据
     */
    @JsonProperty("video_info")
    private VideoInfoDto videoInfo;

    private String token;

    @JsonProperty("host_id")
    private String hostId;

    @JsonProperty("guest_id")
    private String guestId;

    /**
     * 同步状态数据
     */
    @JsonProperty("sync_state")
    private WatchSyncStateDto syncState;

    /**
     * 特效数据
     */
    private WatchEffectDto effect;

    /**
     * 聊天消息数据
     */
    private WatchChatMessageDto message;

    private Long timestamp;
}
