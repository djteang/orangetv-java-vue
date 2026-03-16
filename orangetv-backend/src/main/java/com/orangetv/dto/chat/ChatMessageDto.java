package com.orangetv.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private Long id;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("sender_id")
    private Long senderId;

    @JsonProperty("sender_name")
    private String senderName;

    private String content;

    @JsonProperty("message_type")
    private String messageType;

    private Long timestamp;

    @JsonProperty("is_read")
    private Boolean isRead;
}
