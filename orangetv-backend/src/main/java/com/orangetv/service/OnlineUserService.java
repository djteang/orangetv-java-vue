package com.orangetv.service;

import com.orangetv.entity.User;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 在线用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key for online users
    private static final String ONLINE_USERS_KEY = "online_users";

    // Local cache for session to username mapping
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * 用户上线
     */
    public void userOnline(String sessionId, String username) {
        sessionUserMap.put(sessionId, username);
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
        log.info("用户上线: username={}, sessionId={}", username, sessionId);
    }

    /**
     * 用户下线
     */
    public void userOffline(String sessionId) {
        String username = sessionUserMap.remove(sessionId);
        if (username != null) {
            // Check if user has other active sessions
            boolean hasOtherSessions = sessionUserMap.containsValue(username);
            if (!hasOtherSessions) {
                redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);
                log.info("用户下线: username={}, sessionId={}", username, sessionId);
            }
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String username) {
        Boolean isMember = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, username);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 获取所有在线用户
     */
    public Set<String> getOnlineUsernames() {
        Set<Object> members = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        if (members == null) {
            return new HashSet<>();
        }
        return members.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /**
     * 获取在线用户详情列表
     */
    public List<Map<String, Object>> getOnlineUsers() {
        Set<String> onlineUsernames = getOnlineUsernames();
        if (onlineUsernames.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = userRepository.findByUsernameIn(onlineUsernames);
        return users.stream()
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", user.getId());
                    map.put("username", user.getUsername());
                    map.put("nickname", user.getNickname());
                    map.put("avatar", user.getAvatar());
                    map.put("status", "online");
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定用户ID的在线好友
     */
    public List<Map<String, Object>> getOnlineFriends(Long userId, List<User> friends) {
        Set<String> onlineUsernames = getOnlineUsernames();
        return friends.stream()
                .filter(friend -> onlineUsernames.contains(friend.getUsername()))
                .map(friend -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", friend.getId());
                    map.put("username", friend.getUsername());
                    map.put("nickname", friend.getNickname());
                    map.put("avatar", friend.getAvatar());
                    map.put("status", "online");
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 清理所有在线用户（服务重启时调用）
     */
    public void clearAllOnlineUsers() {
        redisTemplate.delete(ONLINE_USERS_KEY);
        sessionUserMap.clear();
        log.info("清理所有在线用户记录");
    }
}
