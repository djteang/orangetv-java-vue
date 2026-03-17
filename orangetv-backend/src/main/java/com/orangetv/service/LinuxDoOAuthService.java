package com.orangetv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.dto.auth.LoginResponse;
import com.orangetv.entity.MachineCode;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.MachineCodeRepository;
import com.orangetv.repository.UserRepository;
import com.orangetv.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinuxDoOAuthService {

    private final UserRepository userRepository;
    private final MachineCodeRepository machineCodeRepository;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final SiteConfigService siteConfigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String LINUX_DO_AUTH_URL = "https://connect.linux.do/oauth2/authorize";
    private static final String LINUX_DO_TOKEN_URL = "https://connect.linux.do/oauth2/token";
    private static final String LINUX_DO_USER_INFO_URL = "https://connect.linux.do/api/user";
    private static final int MIN_TRUST_LEVEL = 2;

    @Value("${oauth.linuxdo.client-id:}")
    private String clientId;

    @Value("${oauth.linuxdo.client-secret:}")
    private String clientSecret;

    @Value("${oauth.linuxdo.redirect-uri:}")
    private String redirectUri;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final Map<String, Long> oauthStateMap = new ConcurrentHashMap<>();
    private final Map<String, String> stateMachineCodeMap = new ConcurrentHashMap<>();

    public Map<String, String> getAuthUrl(String machineCode) {
        String state = UUID.randomUUID().toString();
        oauthStateMap.put(state, System.currentTimeMillis());

        // 如果提供了设备码，保存到 state 映射中
        if (machineCode != null && !machineCode.isEmpty()) {
            stateMachineCodeMap.put(state, machineCode);
        }

        String authUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=read&state=%s",
                LINUX_DO_AUTH_URL, clientId, URLEncoder.encode(redirectUri, StandardCharsets.UTF_8), state);

        Map<String, String> result = new HashMap<>();
        result.put("authUrl", authUrl);
        return result;
    }

    @Transactional
    public LoginResponse handleCallback(String code, String state) {
        // 验证 state
        Long timestamp = oauthStateMap.remove(state);
        if (timestamp == null || System.currentTimeMillis() - timestamp > 600000) {
            throw ApiException.unauthorized("无效的 state 参数");
        }

        // 获取关联的设备码
        String machineCode = stateMachineCodeMap.remove(state);

        // 检查是否启用了设备码验证
        boolean requireDeviceCode = siteConfigService.getBooleanConfig("require_device_code", false);
        if (requireDeviceCode && (machineCode == null || machineCode.isEmpty())) {
            throw ApiException.badRequest("设备码验证已启用，请提供设备码");
        }

        // 获取 access token
        String accessToken = getAccessToken(code);
        if (accessToken == null) {
            throw ApiException.unauthorized("获取访问令牌失败");
        }

        // 获取用户信息
        Map<String, Object> userInfo = getUserInfo(accessToken);
        if (userInfo == null) {
            throw ApiException.unauthorized("获取用户信息失败");
        }

        // 检查 trust_level
        Integer trustLevel = (Integer) userInfo.get("trust_level");
        if (trustLevel == null || trustLevel < MIN_TRUST_LEVEL) {
            throw ApiException.forbidden("LinuxDo 账号信任等级不足，需要达到 2 级及以上才能登录");
        }

        String platformUserId = (String) userInfo.get("id");
        String username = (String) userInfo.get("username");
        String nickname = (String) userInfo.get("name");
        String avatar = (String) userInfo.get("avatar_url");

        // 查找或创建用户
        User user = userRepository.findByUsername("linuxdo_" + platformUserId).orElse(null);

        if (user == null) {
            // 自动创建用户
            user = new User();
            user.setUsername("linuxdo_" + platformUserId);
            user.setNickname(nickname != null ? nickname : username);
            user.setPassword(UUID.randomUUID().toString()); // 随机密码，OAuth 用户不使用密码登录
            user.setAvatar(avatar);
            user.setRole("USER");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Created new LinuxDo user: {}", user.getUsername());
        }

        // 处理设备码绑定
        boolean machineCodeBound = false;
        if (requireDeviceCode && machineCode != null && !machineCode.isEmpty()) {
            machineCodeBound = checkAndBindMachineCode(user, machineCode);
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成 JWT token
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        tokenService.storeToken(user.getId(), token);

        LoginResponse response = LoginResponse.builder()
                .ok(true)
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .machineCodeBound(machineCodeBound)
                .expiresIn(tokenProvider.getExpirationTime())
                .build();

        return response;
    }

    private boolean checkAndBindMachineCode(User user, String machineCode) {
        MachineCode existingCode = machineCodeRepository.findByMachineCode(machineCode).orElse(null);

        if (existingCode != null) {
            // 设备码已存在，检查是否属于当前用户
            if (!existingCode.getUser().getId().equals(user.getId())) {
                throw ApiException.conflict("此设备已绑定到其他账户");
            }
            // 更新最后使用时间
            existingCode.setLastUsedAt(LocalDateTime.now());
            machineCodeRepository.save(existingCode);
            return true;
        } else {
            // 创建新的设备码绑定
            MachineCode newCode = new MachineCode();
            newCode.setUser(user);
            newCode.setMachineCode(machineCode);
            newCode.setCreatedAt(LocalDateTime.now());
            newCode.setLastUsedAt(LocalDateTime.now());
            machineCodeRepository.save(newCode);
            return true;
        }
    }

    private String getAccessToken(String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", code);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(LINUX_DO_TOKEN_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                return jsonNode.get("access_token").asText();
            }
        } catch (Exception e) {
            log.error("Failed to get LinuxDo access token: {}", e.getMessage(), e);
        }
        return null;
    }

    private Map<String, Object> getUserInfo(String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(LINUX_DO_USER_INFO_URL, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", jsonNode.get("id").asText());
                userInfo.put("username", jsonNode.get("username").asText());
                userInfo.put("name", jsonNode.has("name") && !jsonNode.get("name").isNull() ? jsonNode.get("name").asText() : null);
                userInfo.put("avatar_url", jsonNode.has("avatar_template") ? "https://connect.linux.do" + jsonNode.get("avatar_template").asText().replace("{size}", "120") : null);
                userInfo.put("trust_level", jsonNode.has("trust_level") ? jsonNode.get("trust_level").asInt() : 0);
                return userInfo;
            }
        } catch (Exception e) {
            log.error("Failed to get LinuxDo user info: {}", e.getMessage(), e);
        }
        return null;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }
}
