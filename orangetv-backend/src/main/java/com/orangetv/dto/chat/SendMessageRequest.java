package com.orangetv.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "conversation_id不能为空")
    private String conversationId;

    @NotBlank(message = "content不能为空")
    private String content;

    private String messageType = "text";
}
