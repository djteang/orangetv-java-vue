package com.orangetv.controller.watchtogether;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.watchtogether.*;
import com.orangetv.entity.WatchRoom;
import com.orangetv.service.WatchTogetherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 共同观影 REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/watch-together")
@RequiredArgsConstructor
public class WatchTogetherController {

    private final WatchTogetherService watchTogetherService;

    /**
     * 创建共同观影房间
     */
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody VideoInfoDto videoInfo) {

        String userId = userDetails.getUsername();
        WatchRoom room = watchTogetherService.createRoom(userId, videoInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getId());
        result.put("hostId", room.getHostId());
        result.put("status", room.getStatus());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取房间信息
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRoom(
            @PathVariable String roomId) {

        Optional<WatchRoom> roomOpt = watchTogetherService.getRoom(roomId);

        if (roomOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(404, "房间不存在"));
        }

        WatchRoom room = roomOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getId());
        result.put("hostId", room.getHostId());
        result.put("guestId", room.getGuestId());
        result.put("status", room.getStatus());
        result.put("videoInfo", VideoInfoDto.builder()
                .title(room.getVideoTitle())
                .source(room.getVideoSource())
                .id(room.getVideoId())
                .episodeIndex(room.getEpisodeIndex())
                .videoUrl(room.getVideoUrl())
                .cover(room.getVideoCover())
                .build());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 发送邀请
     */
    @PostMapping("/rooms/{roomId}/invite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendInvite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {

        String userId = userDetails.getUsername();
        String targetUserId = request.get("targetUserId");

        if (targetUserId == null || targetUserId.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "目标用户ID不能为空"));
        }

        Optional<WatchRoom> roomOpt = watchTogetherService.getRoom(roomId);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(404, "房间不存在"));
        }

        WatchRoom room = roomOpt.get();
        if (!room.getHostId().equals(userId)) {
            return ResponseEntity.ok(ApiResponse.error(403, "只有房主可以发送邀请"));
        }

        // 生成 token
        String token = generateToken();
        watchTogetherService.sendInvite(userId, targetUserId, room, token);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("roomId", roomId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 接受邀请
     */
    @PostMapping("/rooms/{roomId}/accept")
    public ResponseEntity<ApiResponse<String>> acceptInvite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {

        String userId = userDetails.getUsername();
        watchTogetherService.acceptInvite(roomId, userId);

        return ResponseEntity.ok(ApiResponse.success("已加入房间"));
    }

    /**
     * 拒绝邀请
     */
    @PostMapping("/rooms/{roomId}/reject")
    public ResponseEntity<ApiResponse<String>> rejectInvite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId,
            @RequestBody Map<String, String> request) {

        String userId = userDetails.getUsername();
        String hostId = request.get("hostId");

        if (hostId == null || hostId.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "主持人ID不能为空"));
        }

        watchTogetherService.rejectInvite(roomId, userId, hostId);

        return ResponseEntity.ok(ApiResponse.success("已拒绝邀请"));
    }

    /**
     * 离开房间
     */
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<ApiResponse<String>> leaveRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {

        String userId = userDetails.getUsername();
        watchTogetherService.leaveRoom(roomId, userId);

        return ResponseEntity.ok(ApiResponse.success("已离开房间"));
    }

    /**
     * 获取用户当前所在房间
     */
    @GetMapping("/current-room")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentRoom(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        Optional<String> roomIdOpt = watchTogetherService.getUserCurrentRoom(userId);

        if (roomIdOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        String roomId = roomIdOpt.get();
        Optional<WatchRoom> roomOpt = watchTogetherService.getRoom(roomId);

        if (roomOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        WatchRoom room = roomOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getId());
        result.put("hostId", room.getHostId());
        result.put("guestId", room.getGuestId());
        result.put("status", room.getStatus());
        result.put("isHost", room.getHostId().equals(userId));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取用户的邀请历史
     */
    @GetMapping("/invites")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInvites(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        List<Map<String, Object>> invites = watchTogetherService.getInvites(userId);

        return ResponseEntity.ok(ApiResponse.success(invites));
    }

    /**
     * 删除单个邀请
     */
    @DeleteMapping("/invites/{inviteId}")
    public ResponseEntity<ApiResponse<String>> deleteInvite(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String inviteId) {

        String userId = userDetails.getUsername();
        watchTogetherService.deleteInvite(userId, inviteId);

        return ResponseEntity.ok(ApiResponse.success("已删除"));
    }

    /**
     * 标记邀请为已失效
     */
    @PostMapping("/invites/{inviteId}/expire")
    public ResponseEntity<ApiResponse<String>> markInviteExpired(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String inviteId) {

        String userId = userDetails.getUsername();
        watchTogetherService.markInviteExpired(userId, inviteId);

        return ResponseEntity.ok(ApiResponse.success("已标记失效"));
    }

    /**
     * 检查房间是否存在
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkRoom(
            @PathVariable String roomId) {

        Optional<WatchRoom> roomOpt = watchTogetherService.getRoom(roomId);

        if (roomOpt.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(404, "房间不存在"));
        }

        WatchRoom room = roomOpt.get();
        // 检查房间状态是否为 ended
        if ("ended".equals(room.getStatus())) {
            return ResponseEntity.ok(ApiResponse.error(410, "房间已结束"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", room.getId());
        result.put("status", room.getStatus());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 生成邀请 token
     */
    private String generateToken() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            token.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return token.toString();
    }
}
