package com.orangetv.controller;

import com.orangetv.dto.ApiResponse;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.UserRepository;
import com.orangetv.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final UserRepository userRepository;

    @GetMapping
    public Map<String, String> getAvatar(@RequestParam String user) {
        return userRepository.findByUsername(user)
                .map(u -> Map.of("avatar", u.getAvatar() != null ? u.getAvatar() : ""))
                .orElse(Map.of("avatar", ""));
    }

    @PostMapping
    public ApiResponse<Void> updateAvatar(@RequestBody Map<String, String> request) {
        String avatar = request.get("avatar");
        String targetUser = request.get("targetUser");

        if (avatar == null || avatar.isEmpty()) {
            throw ApiException.badRequest("头像数据不能为空");
        }

        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw ApiException.unauthorized("未登录");
        }

        String usernameToUpdate = (targetUser != null && !targetUser.isEmpty()) ? targetUser : currentUsername;
        if (!usernameToUpdate.equals(currentUsername) && !SecurityUtils.isAdmin()) {
            throw ApiException.forbidden("无权修改他人头像");
        }

        User userEntity = userRepository.findByUsername(usernameToUpdate).orElse(null);
        if (userEntity == null) {
            throw ApiException.badRequest("用户不存在");
        }

        userEntity.setAvatar(avatar);
        userRepository.save(userEntity);

        return ApiResponse.success();
    }
}
