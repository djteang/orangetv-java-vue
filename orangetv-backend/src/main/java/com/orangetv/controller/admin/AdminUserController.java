package com.orangetv.controller.admin;

import com.orangetv.dto.ApiResponse;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> handleUserAction(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        String targetUsername = (String) request.get("targetUsername");
        String targetPassword = (String) request.get("targetPassword");
        String userGroup = (String) request.get("userGroup");
        String groupAction = (String) request.get("groupAction");
        String groupName = (String) request.get("groupName");
        @SuppressWarnings("unchecked")
        List<String> userGroups = (List<String>) request.get("userGroups");
        @SuppressWarnings("unchecked")
        List<String> enabledApis = (List<String>) request.get("enabledApis");

        switch (action) {
            case "add":
                adminUserService.addUser(targetUsername, targetPassword, userGroup);
                break;
            case "ban":
                adminUserService.banUser(targetUsername);
                break;
            case "unban":
                adminUserService.unbanUser(targetUsername);
                break;
            case "setAdmin":
                checkOwnerPermission();
                adminUserService.setAdmin(targetUsername);
                break;
            case "cancelAdmin":
                checkOwnerPermission();
                adminUserService.cancelAdmin(targetUsername);
                break;
            case "changePassword":
                adminUserService.changePassword(targetUsername, targetPassword);
                break;
            case "deleteUser":
                adminUserService.deleteUser(targetUsername);
                break;
            case "updateUserGroups":
                adminUserService.updateUserGroups(targetUsername, userGroups);
                break;
            case "userGroup":
                handleGroupAction(groupAction, groupName, enabledApis);
                break;
            default:
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "Unknown action: " + action));
        }

        return ResponseEntity.ok(ApiResponse.success());
    }

    private void handleGroupAction(String groupAction, String groupName, List<String> enabledApis) {
        switch (groupAction) {
            case "add":
                adminUserService.createGroup(groupName, null, enabledApis);
                break;
            case "delete":
                adminUserService.deleteGroup(groupName);
                break;
            default:
                throw new IllegalArgumentException("Unknown group action: " + groupAction);
        }
    }

    private void checkOwnerPermission() {
        if (!SecurityUtils.isOwner()) {
            throw new org.springframework.security.access.AccessDeniedException("Only owner can perform this action");
        }
    }
}
