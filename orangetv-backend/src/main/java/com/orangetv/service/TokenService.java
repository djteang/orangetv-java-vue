package com.orangetv.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final long TOKEN_TTL_HOURS = 24;

    public void storeToken(Long userId, String token) {
        String key = TOKEN_KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(key, token, TOKEN_TTL_HOURS, TimeUnit.HOURS);
    }

    public boolean isTokenValid(Long userId, String token) {
        try {
            String key = TOKEN_KEY_PREFIX + userId;
            String storedToken = stringRedisTemplate.opsForValue().get(key);
            return token.equals(storedToken);
        } catch (Exception e) {
            log.warn("Redis unavailable during token validation, falling back to allow: {}", e.getMessage());
            return true;
        }
    }

    public void removeToken(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        stringRedisTemplate.delete(key);
    }
}
