package com.orangetv.controller.auth;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.auth.ChangePasswordRequest;
import com.orangetv.dto.auth.LoginRequest;
import com.orangetv.dto.auth.LoginResponse;
import com.orangetv.dto.auth.RegisterRequest;
import com.orangetv.entity.User;
import com.orangetv.repository.UserRepository;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.AuthService;
import com.orangetv.service.LinuxDoOAuthService;
import com.orangetv.service.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final LinuxDoOAuthService linuxDoOAuthService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            tokenService.removeToken(userId);
        }
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", user.getUsername());
        userInfo.put("role", user.getRole());
        userInfo.put("avatar", user.getAvatar());

        return ResponseEntity.ok(ApiResponse.success(userInfo));
    }

    @GetMapping("/auth/linuxdo/auth-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLinuxDoAuthUrl(@RequestParam(required = false) String machineCode) {
        Map<String, String> result = linuxDoOAuthService.getAuthUrl(machineCode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/auth/linuxdo/callback")
    public void linuxDoCallback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        try {
            LoginResponse loginResponse = linuxDoOAuthService.handleCallback(code, state);
            String frontendUrl = linuxDoOAuthService.getFrontendUrl();
            response.sendRedirect(frontendUrl + "/login?oauth=success&token=" + URLEncoder.encode(loginResponse.getToken(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            String frontendUrl = linuxDoOAuthService.getFrontendUrl();
            response.sendRedirect(frontendUrl + "/login?oauth=error&message=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        }
    }
}
