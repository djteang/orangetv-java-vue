package com.orangetv.websocket;

import com.orangetv.dto.chat.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{conversationId}")
    @SendTo("/topic/conversation/{conversationId}")
    public ChatMessageDto handleMessage(
            @DestinationVariable String conversationId,
            @Payload ChatMessageDto message,
            Principal principal) {
        log.info("Received message in conversation {}: {}", conversationId, message.getContent());

        if (principal != null) {
            message.setSenderName(principal.getName());
        }

        return message;
    }

    @MessageMapping("/typing/{conversationId}")
    @SendTo("/topic/typing/{conversationId}")
    public String handleTyping(
            @DestinationVariable String conversationId,
            Principal principal) {
        return principal != null ? principal.getName() : "anonymous";
    }
}
