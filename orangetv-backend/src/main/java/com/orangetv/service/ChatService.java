package com.orangetv.service;

import com.orangetv.dto.chat.*;
import com.orangetv.entity.*;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key for online users (same as OnlineUserService)
    private static final String ONLINE_USERS_KEY = "online_users";

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findByUserId(userId);
        return conversations.stream()
                .map(this::toConversationDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConversationDto createConversation(Long userId, CreateConversationRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        // 如果是私聊，检查是否已存在
        if ("private".equals(request.getType()) && request.getParticipants().size() == 1) {
            User otherUser = userRepository.findByUsername(request.getParticipants().get(0))
                    .orElseThrow(() -> ApiException.notFound("对方用户不存在"));

            Optional<Conversation> existing = conversationRepository.findPrivateConversation(userId, otherUser.getId());
            if (existing.isPresent()) {
                return toConversationDto(existing.get());
            }
        }

        Conversation conversation = Conversation.builder()
                .type(request.getType())
                .name(request.getName())
                .creator(creator)
                .build();
        conversation.prePersist();
        conversationRepository.save(conversation);

        // 添加创建者为参与者
        ConversationParticipant creatorParticipant = ConversationParticipant.builder()
                .conversation(conversation)
                .user(creator)
                .build();
        participantRepository.save(creatorParticipant);

        // 添加其他参与者
        for (String username : request.getParticipants()) {
            User participant = userRepository.findByUsername(username).orElse(null);
            if (participant != null && !participant.getId().equals(userId)) {
                ConversationParticipant cp = ConversationParticipant.builder()
                        .conversation(conversation)
                        .user(participant)
                        .build();
                participantRepository.save(cp);
            }
        }

        return toConversationDto(conversation);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(String conversationId, int limit, int offset) {
        List<ChatMessage> messages = messageRepository.findRecentMessages(
                conversationId, PageRequest.of(offset / limit, limit));
        return messages.stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageDto sendMessage(Long userId, SendMessageRequest request) {
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> ApiException.notFound("对话不存在"));

        // 检查用户是否是对话参与者
        if (!participantRepository.existsByConversationIdAndUserId(conversation.getId(), userId)) {
            throw ApiException.forbidden("您不是该对话的参与者");
        }

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType())
                .build();
        messageRepository.save(message);

        // 更新对话时间
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        ChatMessageDto dto = toMessageDto(message);

        // 通过 WebSocket 推送消息给所有参与者
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), dto);

        // 推送给私聊的对方用户
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("type", "chat_message");
        chatMessage.put("data", dto);
        chatMessage.put("timestamp", System.currentTimeMillis());

        for (ConversationParticipant participant : participants) {
            if (!participant.getUser().getId().equals(userId)) {
                messagingTemplate.convertAndSendToUser(
                    participant.getUser().getUsername(),
                    "/queue/chat",
                    chatMessage
                );
            }
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFriends(Long userId) {
        List<User> friends = friendRepository.findFriendsByUserId(userId);
        return friends.stream()
                .map(f -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", f.getId());
                    map.put("username", f.getUsername());
                    map.put("nickname", f.getNickname());
                    map.put("avatar", f.getAvatar());
                    // Check online status from Redis
                    Boolean isOnline = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, f.getUsername());
                    map.put("status", Boolean.TRUE.equals(isOnline) ? "online" : "offline");
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFriendRequests(Long userId) {
        // 获取收到的好友申请
        List<FriendRequest> receivedRequests = friendRequestRepository.findByToUserIdOrderByCreatedAtDesc(userId);
        // 获取发出的好友申请
        List<FriendRequest> sentRequests = friendRequestRepository.findByFromUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> result = new ArrayList<>();

        // 处理收到的申请
        for (FriendRequest r : receivedRequests) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("from_user", r.getFromUser().getUsername());
            map.put("from_user_avatar", r.getFromUser().getAvatar());
            map.put("to_user", r.getToUser().getUsername());
            map.put("to_user_avatar", r.getToUser().getAvatar());
            map.put("message", r.getMessage());
            map.put("status", r.getStatus());
            map.put("direction", "received"); // 收到的申请
            map.put("created_at", r.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1000);
            map.put("updated_at", r.getUpdatedAt() != null ? r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC) * 1000 : null);
            result.add(map);
        }

        // 处理发出的申请
        for (FriendRequest r : sentRequests) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("from_user", r.getFromUser().getUsername());
            map.put("from_user_avatar", r.getFromUser().getAvatar());
            map.put("to_user", r.getToUser().getUsername());
            map.put("to_user_avatar", r.getToUser().getAvatar());
            map.put("message", r.getMessage());
            map.put("status", r.getStatus());
            map.put("direction", "sent"); // 发出的申请
            map.put("created_at", r.getCreatedAt().toEpochSecond(ZoneOffset.UTC) * 1000);
            map.put("updated_at", r.getUpdatedAt() != null ? r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC) * 1000 : null);
            result.add(map);
        }

        // 按创建时间倒序排序
        result.sort((a, b) -> {
            Long ta = (Long) a.get("created_at");
            Long tb = (Long) b.get("created_at");
            return tb.compareTo(ta);
        });

        return result;
    }

    @Transactional
    public void sendFriendRequest(Long userId, String toUsername, String message) {
        User fromUser = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        User toUser = userRepository.findByUsername(toUsername)
                .orElseThrow(() -> ApiException.notFound("目标用户不存在"));

        if (fromUser.getId().equals(toUser.getId())) {
            throw ApiException.badRequest("不能添加自己为好友");
        }

        if (friendRepository.existsByUserIdAndFriendId(userId, toUser.getId())) {
            throw ApiException.conflict("已经是好友了");
        }

        if (friendRequestRepository.existsByFromUserAndToUserAndStatus(fromUser, toUser, "pending")) {
            throw ApiException.conflict("已发送过好友申请");
        }

        FriendRequest request = FriendRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .message(message)
                .status("pending")
                .build();
        friendRequestRepository.save(request);
    }

    @Transactional
    public void handleFriendRequest(Long userId, Long requestId, boolean accept) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("好友申请不存在"));

        if (!request.getToUser().getId().equals(userId)) {
            throw ApiException.forbidden("无权处理此申请");
        }

        if (!request.isPending()) {
            throw ApiException.badRequest("申请已处理");
        }

        if (accept) {
            request.setStatus("accepted");

            // 双向添加好友
            Friend friend1 = Friend.builder()
                    .user(request.getToUser())
                    .friend(request.getFromUser())
                    .build();
            Friend friend2 = Friend.builder()
                    .user(request.getFromUser())
                    .friend(request.getToUser())
                    .build();
            friendRepository.save(friend1);
            friendRepository.save(friend2);
        } else {
            request.setStatus("rejected");
        }

        friendRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchUsers(Long currentUserId, String keyword) {
        List<User> users = userRepository.searchByKeyword(keyword);
        // 获取当前用户的好友列表
        List<User> friends = friendRepository.findFriendsByUserId(currentUserId);
        Set<Long> friendIds = friends.stream().map(User::getId).collect(Collectors.toSet());

        return users.stream()
                .filter(u -> !u.getId().equals(currentUserId)) // 排除自己
                .limit(20)
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("nickname", u.getNickname());
                    map.put("avatar", u.getAvatar());
                    map.put("isFriend", friendIds.contains(u.getId()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    // China timezone offset (UTC+8)
    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);

    private ConversationDto toConversationDto(Conversation conversation) {
        List<String> participants = participantRepository.findByConversationId(conversation.getId())
                .stream()
                .map(p -> p.getUser().getUsername())
                .collect(Collectors.toList());

        return ConversationDto.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .type(conversation.getType())
                .participants(participants)
                .isGroup("group".equals(conversation.getType()))
                .createdAt(conversation.getCreatedAt() != null ?
                        conversation.getCreatedAt().toEpochSecond(CHINA_ZONE) * 1000 : null)
                .updatedAt(conversation.getUpdatedAt() != null ?
                        conversation.getUpdatedAt().toEpochSecond(CHINA_ZONE) * 1000 : null)
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getUsername())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .timestamp(message.getCreatedAt() != null ?
                        message.getCreatedAt().toEpochSecond(CHINA_ZONE) * 1000 : null)
                .isRead(false)
                .build();
    }

    // Redis key prefix for chat backgrounds
    private static final String CHAT_BACKGROUND_KEY_PREFIX = "chat_background:";

    /**
     * 保存聊天背景 URL
     */
    public void saveChatBackground(Long userId, String friendUsername, String backgroundUrl) {
        String key = CHAT_BACKGROUND_KEY_PREFIX + userId + ":" + friendUsername;
        if (backgroundUrl != null && !backgroundUrl.isEmpty()) {
            redisTemplate.opsForValue().set(key, backgroundUrl);
        } else {
            redisTemplate.delete(key);
        }
        log.debug("保存聊天背景: userId={}, friend={}, url={}", userId, friendUsername, backgroundUrl);
    }

    /**
     * 获取聊天背景 URL
     */
    public String getChatBackground(Long userId, String friendUsername) {
        String key = CHAT_BACKGROUND_KEY_PREFIX + userId + ":" + friendUsername;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }
}
