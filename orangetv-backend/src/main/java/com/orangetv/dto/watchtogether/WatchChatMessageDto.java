package com.orangetv.dto.watchtogether;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 共同观影聊天消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchChatMessageDto {

    private String id;

    private String sender;

    private String content;

    private Long timestamp;
}
