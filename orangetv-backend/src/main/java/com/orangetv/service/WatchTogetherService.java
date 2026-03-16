package com.orangetv.service;

import com.orangetv.dto.watchtogether.*;
import com.orangetv.entity.WatchRoom;
import com.orangetv.repository.WatchRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 共同观影服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchTogetherService {

    private final WatchRoomRepository watchRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key prefix for watch invites
    private static final String INVITE_KEY_PREFIX = "watch_invites:";

    // 内存中的活跃房间缓存（用于快速查找）
    private final Map<String, WatchRoom> activeRooms = new ConcurrentHashMap<>();

    // 用户到房间的映射
    private final Map<String, String> userRoomMap = new ConcurrentHashMap<>();

    /**
     * 创建共同观影房间
     */
    @Transactional
    public WatchRoom createRoom(String hostId, VideoInfoDto videoInfo) {
        String roomId = UUID.randomUUID().toString();

        WatchRoom room = WatchRoom.builder()
                .id(roomId)
                .hostId(hostId)
                .videoTitle(videoInfo.getTitle())
                .videoSource(videoInfo.getSource())
                .videoId(videoInfo.getId())
                .episodeIndex(videoInfo.getEpisodeIndex())
                .videoUrl(videoInfo.getVideoUrl())
                .videoCover(videoInfo.getCover())
                .status("waiting")
                .build();

        room = watchRoomRepository.save(room);
        activeRooms.put(roomId, room);
        userRoomMap.put(hostId, roomId);

        log.info("创建共同观影房间: roomId={}, hostId={}, video={}", roomId, hostId, videoInfo.getTitle());
        return room;
    }

    /**
     * 发送观影邀请
     */
    public void sendInvite(String hostId, String targetUserId, WatchRoom room, String token) {
        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_invite")
                .roomId(room.getId())
                .hostId(hostId)
                .targetUserId(targetUserId)
                .token(token)
                .videoInfo(VideoInfoDto.builder()
                        .title(room.getVideoTitle())
                        .source(room.getVideoSource())
                        .id(room.getVideoId())
                        .episodeIndex(room.getEpisodeIndex())
                        .videoUrl(room.getVideoUrl())
                        .cover(room.getVideoCover())
                        .build())
                .timestamp(System.currentTimeMillis())
                .build();

        // 保存邀请到 Redis（为目标用户保存 - 收到的邀请）
        saveInviteToRedis(targetUserId, "received", message);
        // 保存邀请到 Redis（为邀请人保存 - 发出的邀请）
        saveInviteToRedis(hostId, "sent", message);

        // 发送给目标用户
        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/watch-together",
                message
        );

        log.info("发送观影邀请: from={}, to={}, roomId={}", hostId, targetUserId, room.getId());
    }

    /**
     * 保存邀请到 Redis
     */
    private void saveInviteToRedis(String visitorId, String visitorRole, WatchTogetherMessageDto invite) {
        String key = INVITE_KEY_PREFIX + visitorId;
        String inviteId = UUID.randomUUID().toString();

        Map<String, Object> inviteData = new HashMap<>();
        inviteData.put("id", inviteId);
        inviteData.put("from", invite.getHostId());
        inviteData.put("to", invite.getTargetUserId());
        inviteData.put("roomId", invite.getRoomId());
        inviteData.put("videoTitle", invite.getVideoInfo() != null ? invite.getVideoInfo().getTitle() : "未知视频");
        inviteData.put("timestamp", invite.getTimestamp());
        inviteData.put("expired", false);
        inviteData.put("direction", visitorRole); // "sent" 或 "received"

        // 使用 hash 存储，field 为邀请 ID
        redisTemplate.opsForHash().put(key, inviteId, inviteData);

        // 同时记录 roomId 到 inviteId 的映射，便于后续根据 roomId 标记失效
        String roomInviteKey = "room_invites:" + invite.getRoomId();
        redisTemplate.opsForHash().put(roomInviteKey, visitorId + ":" + inviteId, visitorId);

        log.debug("保存邀请到 Redis: visitorId={}, inviteId={}, roomId={}, direction={}", visitorId, inviteId, invite.getRoomId(), visitorRole);
    }

    /**
     * 获取用户的所有邀请历史
     */
    public List<Map<String, Object>> getInvites(String userId) {
        String key = INVITE_KEY_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        return entries.values().stream()
                .map(v -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> invite = (Map<String, Object>) v;
                    return invite;
                })
                .sorted((a, b) -> {
                    Long ta = ((Number) a.getOrDefault("timestamp", 0L)).longValue();
                    Long tb = ((Number) b.getOrDefault("timestamp", 0L)).longValue();
                    return tb.compareTo(ta); // 按时间倒序
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除单个邀请
     */
    public void deleteInvite(String userId, String inviteId) {
        String key = INVITE_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(key, inviteId);
        log.debug("删除邀请: userId={}, inviteId={}", userId, inviteId);
    }

    /**
     * 标记邀请为已失效
     */
    public void markInviteExpired(String userId, String inviteId) {
        String key = INVITE_KEY_PREFIX + userId;
        Object inviteObj = redisTemplate.opsForHash().get(key, inviteId);
        if (inviteObj != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> invite = (Map<String, Object>) inviteObj;
            invite.put("expired", true);
            redisTemplate.opsForHash().put(key, inviteId, invite);
            log.debug("标记邀请已失效: userId={}, inviteId={}", userId, inviteId);
        }
    }

    /**
     * 根据房间ID标记所有相关邀请为已失效
     */
    public void markInvitesByRoomExpired(String roomId) {
        String roomInviteKey = "room_invites:" + roomId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(roomInviteKey);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String compositeKey = (String) entry.getKey();
            String visitorId = (String) entry.getValue();
            // compositeKey 格式: visitorId:inviteId
            String[] parts = compositeKey.split(":");
            if (parts.length >= 2) {
                String inviteId = parts[1];
                markInviteExpired(visitorId, inviteId);
            }
        }

        // 清理房间邀请映射
        redisTemplate.delete(roomInviteKey);
        log.debug("标记房间所有邀请已失效: roomId={}, count={}", roomId, entries.size());
    }

    /**
     * 接受邀请
     */
    @Transactional
    public void acceptInvite(String roomId, String guestId) {
        WatchRoom room = activeRooms.get(roomId);
        if (room == null) {
            room = watchRoomRepository.findById(roomId).orElse(null);
        }

        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return;
        }

        room.setGuestId(guestId);
        room.setStatus("active");
        watchRoomRepository.save(room);
        activeRooms.put(roomId, room);
        userRoomMap.put(guestId, roomId);

        // 通知主持人
        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_invite_accept")
                .roomId(roomId)
                .guestId(guestId)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(
                room.getHostId(),
                "/queue/watch-together",
                message
        );

        log.info("接受观影邀请: roomId={}, guestId={}", roomId, guestId);
    }

    /**
     * 拒绝邀请
     */
    public void rejectInvite(String roomId, String guestId, String hostId) {
        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_invite_reject")
                .roomId(roomId)
                .guestId(guestId)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(
                hostId,
                "/queue/watch-together",
                message
        );

        log.info("拒绝观影邀请: roomId={}, guestId={}", roomId, guestId);
    }

    /**
     * 同步播放状态
     */
    public void syncPlayState(String roomId, String senderId, WatchSyncStateDto syncState) {
        WatchRoom room = activeRooms.get(roomId);
        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return;
        }

        // 确定目标用户
        String targetUserId = senderId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
        if (targetUserId == null) {
            return;
        }

        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_sync")
                .roomId(roomId)
                .senderId(senderId)
                .syncState(syncState)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/watch-together",
                message
        );
    }

    /**
     * 发送互动特效
     */
    public void sendEffect(String roomId, String senderId, WatchEffectDto effect) {
        WatchRoom room = activeRooms.get(roomId);
        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return;
        }

        // 确定目标用户
        String targetUserId = senderId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
        if (targetUserId == null) {
            return;
        }

        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_effect")
                .roomId(roomId)
                .senderId(senderId)
                .effect(effect)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/watch-together",
                message
        );

        log.debug("发送特效: roomId={}, from={}, type={}", roomId, senderId, effect.getType());
    }

    /**
     * 发送聊天消息
     */
    public void sendChatMessage(String roomId, String senderId, WatchChatMessageDto chatMessage) {
        WatchRoom room = activeRooms.get(roomId);
        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return;
        }

        // 确定目标用户
        String targetUserId = senderId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
        if (targetUserId == null) {
            return;
        }

        WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                .type("watch_chat")
                .roomId(roomId)
                .senderId(senderId)
                .message(chatMessage)
                .timestamp(System.currentTimeMillis())
                .build();

        messagingTemplate.convertAndSendToUser(
                targetUserId,
                "/queue/watch-together",
                message
        );

        log.debug("发送聊天消息: roomId={}, from={}", roomId, senderId);
    }

    /**
     * 离开房间
     */
    @Transactional
    public void leaveRoom(String roomId, String userId) {
        WatchRoom room = activeRooms.get(roomId);
        if (room == null) {
            room = watchRoomRepository.findById(roomId).orElse(null);
        }

        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return;
        }

        // 判断是否是房主离开
        boolean isHost = userId.equals(room.getHostId());

        // 确定目标用户
        String targetUserId = isHost ? room.getGuestId() : room.getHostId();

        // 更新房间状态
        room.setStatus("ended");
        watchRoomRepository.save(room);
        activeRooms.remove(roomId);
        userRoomMap.remove(userId);
        if (targetUserId != null) {
            userRoomMap.remove(targetUserId);
        }

        // 只有房主离开时才标记该房间所有邀请为已失效
        if (isHost) {
            markInvitesByRoomExpired(roomId);
        }

        // 通知对方
        if (targetUserId != null) {
            WatchTogetherMessageDto message = WatchTogetherMessageDto.builder()
                    .type("watch_leave")
                    .roomId(roomId)
                    .senderId(userId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    targetUserId,
                    "/queue/watch-together",
                    message
            );
        }

        log.info("离开房间: roomId={}, userId={}, isHost={}", roomId, userId, isHost);
    }

    /**
     * 获取房间信息
     */
    public Optional<WatchRoom> getRoom(String roomId) {
        WatchRoom room = activeRooms.get(roomId);
        if (room != null) {
            return Optional.of(room);
        }
        return watchRoomRepository.findById(roomId);
    }

    /**
     * 获取用户当前所在房间
     */
    public Optional<String> getUserCurrentRoom(String userId) {
        return Optional.ofNullable(userRoomMap.get(userId));
    }

    /**
     * 定时清理过期房间（每10分钟执行一次）
     */
    @Scheduled(fixedRate = 600000)
    @Transactional
    public void cleanupExpiredRooms() {
        // 清理等待超过30分钟的房间
        LocalDateTime waitingExpireTime = LocalDateTime.now().minusMinutes(30);
        List<WatchRoom> expiredWaitingRooms = watchRoomRepository.findExpiredWaitingRooms(waitingExpireTime);
        for (WatchRoom room : expiredWaitingRooms) {
            room.setStatus("ended");
            watchRoomRepository.save(room);
            activeRooms.remove(room.getId());
            userRoomMap.remove(room.getHostId());
            log.info("清理过期等待房间: roomId={}", room.getId());
        }

        // 清理结束超过24小时的房间记录
        LocalDateTime endedExpireTime = LocalDateTime.now().minusHours(24);
        List<WatchRoom> oldEndedRooms = watchRoomRepository.findOldEndedRooms(endedExpireTime);
        watchRoomRepository.deleteAll(oldEndedRooms);
        log.info("清理旧房间记录: count={}", oldEndedRooms.size());
    }
}
