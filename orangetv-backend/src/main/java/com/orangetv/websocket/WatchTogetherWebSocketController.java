package com.orangetv.websocket;

import com.orangetv.dto.watchtogether.*;
import com.orangetv.entity.WatchRoom;
import com.orangetv.service.WatchTogetherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * 共同观影 WebSocket 控制器
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WatchTogetherWebSocketController {

    private final WatchTogetherService watchTogetherService;

    /**
     * 处理共同观影消息
     * 客户端发送到: /app/watch-together
     */
    @MessageMapping("/watch-together")
    public void handleWatchTogetherMessage(
            @Payload WatchTogetherMessageDto message,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = principal != null ? principal.getName() : null;
        if (userId == null) {
            log.warn("未认证的 WebSocket 消息");
            return;
        }

        log.info("收到共同观影消息: type={}, userId={}, roomId={}",
                message.getType(), userId, message.getRoomId());

        switch (message.getType()) {
            case "watch_invite":
                handleInvite(userId, message);
                break;
            case "watch_invite_accept":
                handleInviteAccept(userId, message);
                break;
            case "watch_invite_reject":
                handleInviteReject(userId, message);
                break;
            case "watch_sync":
                handleSync(userId, message);
                break;
            case "watch_effect":
                handleEffect(userId, message);
                break;
            case "watch_chat":
                handleChat(userId, message);
                break;
            case "watch_leave":
                handleLeave(userId, message);
                break;
            default:
                log.warn("未知的消息类型: {}", message.getType());
        }
    }

    /**
     * 处理邀请消息
     */
    private void handleInvite(String hostId, WatchTogetherMessageDto message) {
        if (message.getTargetUserId() == null) {
            log.warn("邀请消息缺少 targetUserId");
            return;
        }

        WatchRoom room = null;

        // 如果已有 roomId，使用已有房间；否则创建新房间
        if (message.getRoomId() != null) {
            room = watchTogetherService.getRoom(message.getRoomId()).orElse(null);
            if (room == null && message.getVideoInfo() != null) {
                // 房间不存在但有视频信息，创建新房间
                room = watchTogetherService.createRoom(hostId, message.getVideoInfo());
            } else if (room == null) {
                log.warn("房间不存在且缺少视频信息: roomId={}", message.getRoomId());
                return;
            }
        } else if (message.getVideoInfo() != null) {
            // 没有 roomId 但有视频信息，创建新房间
            room = watchTogetherService.createRoom(hostId, message.getVideoInfo());
        } else {
            log.warn("邀请消息缺少 roomId 和 videoInfo");
            return;
        }

        // 生成邀请 token
        String token = message.getToken() != null ? message.getToken() : generateToken();

        // 发送邀请
        watchTogetherService.sendInvite(hostId, message.getTargetUserId(), room, token);
    }

    /**
     * 处理接受邀请
     */
    private void handleInviteAccept(String guestId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null) {
            log.warn("接受邀请消息缺少 roomId");
            return;
        }

        watchTogetherService.acceptInvite(message.getRoomId(), guestId);
    }

    /**
     * 处理拒绝邀请
     */
    private void handleInviteReject(String guestId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null || message.getHostId() == null) {
            log.warn("拒绝邀请消息缺少必要参数");
            return;
        }

        watchTogetherService.rejectInvite(message.getRoomId(), guestId, message.getHostId());
    }

    /**
     * 处理播放同步
     */
    private void handleSync(String senderId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null || message.getSyncState() == null) {
            log.warn("同步消息缺少必要参数");
            return;
        }

        // 设置发送者信息
        message.getSyncState().setUpdatedBy(senderId);
        message.getSyncState().setTimestamp(System.currentTimeMillis());

        watchTogetherService.syncPlayState(message.getRoomId(), senderId, message.getSyncState());
    }

    /**
     * 处理互动特效
     */
    private void handleEffect(String senderId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null || message.getEffect() == null) {
            log.warn("特效消息缺少必要参数");
            return;
        }

        // 设置发送者和时间戳
        WatchEffectDto effect = message.getEffect();
        effect.setSender(senderId);
        if (effect.getId() == null) {
            effect.setId("effect-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        }
        effect.setTimestamp(System.currentTimeMillis());

        watchTogetherService.sendEffect(message.getRoomId(), senderId, effect);
    }

    /**
     * 处理聊天消息
     */
    private void handleChat(String senderId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null || message.getMessage() == null) {
            log.warn("聊天消息缺少必要参数");
            return;
        }

        // 设置发送者和时间戳
        WatchChatMessageDto chatMessage = message.getMessage();
        chatMessage.setSender(senderId);
        if (chatMessage.getId() == null) {
            chatMessage.setId("chat-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        }
        chatMessage.setTimestamp(System.currentTimeMillis());

        watchTogetherService.sendChatMessage(message.getRoomId(), senderId, chatMessage);
    }

    /**
     * 处理离开房间
     */
    private void handleLeave(String userId, WatchTogetherMessageDto message) {
        if (message.getRoomId() == null) {
            log.warn("离开消息缺少 roomId");
            return;
        }

        watchTogetherService.leaveRoom(message.getRoomId(), userId);
    }

    /**
     * 生成邀请 token
     */
    private String generateToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            token.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return token.toString();
    }
}
