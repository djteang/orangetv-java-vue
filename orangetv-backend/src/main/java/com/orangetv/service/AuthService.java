package com.orangetv.service;

import com.orangetv.dto.auth.ChangePasswordRequest;
import com.orangetv.dto.auth.LoginRequest;
import com.orangetv.dto.auth.LoginResponse;
import com.orangetv.dto.auth.RegisterRequest;
import com.orangetv.entity.MachineCode;
import com.orangetv.entity.User;
import com.orangetv.entity.UserGroup;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.MachineCodeRepository;
import com.orangetv.repository.UserGroupRepository;
import com.orangetv.repository.UserRepository;
import com.orangetv.security.JwtTokenProvider;
import com.orangetv.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final MachineCodeRepository machineCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final SiteConfigService siteConfigService;

    @Value("${orangetv.allow-registration:true}")
    private boolean allowRegistration;

    @Value("${orangetv.default-user-group:default}")
    private String defaultUserGroup;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 验证用户名密码
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // 检查用户是否被禁用
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> ApiException.unauthorized("用户不存在"));

        if (!user.getEnabled()) {
            throw ApiException.unauthorized("用户已被封禁");
        }

        // 从数据库读取设备码验证配置
        boolean requireDeviceCode = siteConfigService.getBooleanConfig("require_device_code", false);

        // 如果启用了设备码验证，必须提供设备码
        if (requireDeviceCode && (request.getMachineCode() == null || request.getMachineCode().isEmpty())) {
            throw ApiException.badRequest("设备码验证已启用，请提供设备码");
        }

        // 检查机器码
        boolean machineCodeBound = false;
        if (requireDeviceCode && request.getMachineCode() != null) {
            machineCodeBound = checkAndBindMachineCode(user, request.getMachineCode());
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成 JWT token
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        tokenService.storeToken(user.getId(), token);

        return LoginResponse.builder()
                .ok(true)
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .machineCodeBound(machineCodeBound)
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (!allowRegistration) {
            throw ApiException.forbidden("注册功能已关闭");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw ApiException.conflict("用户名已存在");
        }

        // 创建新用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .enabled(true)
                .build();

        // 添加默认用户组
        userGroupRepository.findByName(defaultUserGroup)
                .ifPresent(group -> user.getGroups().add(group));

        userRepository.save(user);

        // 绑定机器码
        boolean machineCodeBound = false;
        if (request.getMachineCode() != null) {
            machineCodeBound = checkAndBindMachineCode(user, request.getMachineCode());
        }

        // 生成 JWT token
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        tokenService.storeToken(user.getId(), token);

        return LoginResponse.builder()
                .ok(true)
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .machineCodeBound(machineCodeBound)
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        tokenService.removeToken(userId);
    }

    private boolean checkAndBindMachineCode(User user, String machineCode) {
        // 检查用户是否已经绑定了设备码
        List<MachineCode> userMachineCodes = machineCodeRepository.findByUser(user);

        if (!userMachineCodes.isEmpty()) {
            // 用户已经绑定了设备码，检查当前设备码是否在已绑定列表中
            Optional<MachineCode> matchingCode = userMachineCodes.stream()
                    .filter(mc -> mc.getMachineCode().equals(machineCode))
                    .findFirst();

            if (matchingCode.isPresent()) {
                // 设备码匹配，更新最后使用时间
                matchingCode.get().setLastUsedAt(LocalDateTime.now());
                machineCodeRepository.save(matchingCode.get());
                return true;
            } else {
                // 设备码不匹配，拒绝登录
                throw ApiException.forbidden("此账户已绑定其他设备，请使用已绑定的设备登录");
            }
        }

        // 用户还没有绑定设备码，检查该设备码是否被其他用户绑定
        Optional<MachineCode> existingCode = machineCodeRepository.findByMachineCode(machineCode);
        if (existingCode.isPresent()) {
            throw ApiException.conflict("此设备已绑定到其他账户");
        }

        // 绑定新设备码
        MachineCode newCode = MachineCode.builder()
                .user(user)
                .machineCode(machineCode)
                .createdAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();
        machineCodeRepository.save(newCode);

        return true;
    }
}
