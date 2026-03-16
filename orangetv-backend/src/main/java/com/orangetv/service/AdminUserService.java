package com.orangetv.service;

import com.orangetv.entity.User;
import com.orangetv.entity.UserGroup;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.UserGroupRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addUser(String username, String password, String groupName) {
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("用户名已存在");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role("user")
                .enabled(true)
                .build();

        if (groupName != null) {
            userGroupRepository.findByName(groupName)
                    .ifPresent(group -> user.getGroups().add(group));
        }

        userRepository.save(user);
    }

    @Transactional
    public void banUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setEnabled(false);
        userRepository.save(user);
        tokenService.removeToken(user.getId());
    }

    @Transactional
    public void unbanUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void setAdmin(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setRole("admin");
        userRepository.save(user);
    }

    @Transactional
    public void cancelAdmin(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setRole("user");
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.removeToken(user.getId());
    }

    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        tokenService.removeToken(user.getId());
        userRepository.delete(user);
    }

    @Transactional
    public void updateUserGroups(String username, List<String> groupNames) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        user.getGroups().clear();
        for (String groupName : groupNames) {
            userGroupRepository.findByName(groupName)
                    .ifPresent(group -> user.getGroups().add(group));
        }

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllGroups() {
        return userGroupRepository.findAll().stream()
                .map(group -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", group.getId());
                    map.put("name", group.getName());
                    map.put("description", group.getDescription());
                    map.put("color", group.getColor());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void createGroup(String name, String description, List<String> enabledApis) {
        if (userGroupRepository.existsByName(name)) {
            throw ApiException.conflict("用户组已存在");
        }

        UserGroup group = UserGroup.builder()
                .name(name)
                .description(description)
                .build();

        userGroupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(String name) {
        UserGroup group = userGroupRepository.findByName(name)
                .orElseThrow(() -> ApiException.notFound("用户组不存在"));
        userGroupRepository.delete(group);
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("role", user.getRole());
        map.put("enabled", user.getEnabled());
        map.put("banned", !user.getEnabled());
        map.put("groups", user.getGroups().stream().map(UserGroup::getName).collect(Collectors.toList()));
        map.put("avatar", user.getAvatar());
        map.put("createdAt", user.getCreatedAt());
        map.put("lastLoginAt", user.getLastLoginAt());
        return map;
    }
}
