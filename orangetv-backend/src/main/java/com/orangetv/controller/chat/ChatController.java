package com.orangetv.controller.chat;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.chat.*;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.ChatService;
import com.orangetv.service.OnlineUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final OnlineUserService onlineUserService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getConversations() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.getConversations(userId));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.createConversation(userId, request));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(chatService.getMessages(conversationId, limit, offset));
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.sendMessage(userId, request));
    }

    @GetMapping("/friends")
    public ResponseEntity<List<Map<String, Object>>> getFriends() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.getFriends(userId));
    }

    @GetMapping("/friend-requests")
    public ResponseEntity<List<Map<String, Object>>> getFriendRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.getFriendRequests(userId));
    }

    @PostMapping("/friend-requests")
    public ResponseEntity<ApiResponse<Void>> sendFriendRequest(@RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.sendFriendRequest(userId, request.get("to_user"), request.get("message"));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/friend-requests/{id}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptFriendRequest(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.handleFriendRequest(userId, id, true);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/friend-requests/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectFriendRequest(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.handleFriendRequest(userId, id, false);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/search-users")
    public ResponseEntity<List<Map<String, Object>>> searchUsers(@RequestParam String q) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(chatService.searchUsers(userId, q));
    }

    @GetMapping("/online-users")
    public ResponseEntity<List<Map<String, Object>>> getOnlineUsers() {
        return ResponseEntity.ok(onlineUserService.getOnlineUsers());
    }

    @GetMapping("/background/{friendUsername}")
    public ResponseEntity<Map<String, String>> getChatBackground(@PathVariable String friendUsername) {
        Long userId = SecurityUtils.getCurrentUserId();
        String background = chatService.getChatBackground(userId, friendUsername);
        return ResponseEntity.ok(Map.of("background", background != null ? background : ""));
    }

    @PostMapping("/background/{friendUsername}")
    public ResponseEntity<ApiResponse<Void>> saveChatBackground(
            @PathVariable String friendUsername,
            @RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.saveChatBackground(userId, friendUsername, request.get("background"));
        return ResponseEntity.ok(ApiResponse.success());
    }
}
