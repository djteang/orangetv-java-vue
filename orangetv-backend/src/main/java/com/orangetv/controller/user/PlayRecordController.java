package com.orangetv.controller.user;

import com.orangetv.dto.ApiResponse;
import com.orangetv.dto.user.PlayRecordDto;
import com.orangetv.dto.user.SavePlayRecordRequest;
import com.orangetv.security.SecurityUtils;
import com.orangetv.service.PlayRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/playrecords")
@RequiredArgsConstructor
public class PlayRecordController {

    private final PlayRecordService playRecordService;

    @GetMapping
    public ResponseEntity<?> getPlayRecords(@RequestParam(required = false) String key) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (key != null && !key.isEmpty()) {
            PlayRecordDto record = playRecordService.getPlayRecord(userId, key);
            return ResponseEntity.ok(record);
        }

        Map<String, PlayRecordDto> records = playRecordService.getAllPlayRecords(userId);
        return ResponseEntity.ok(records);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> savePlayRecord(@Valid @RequestBody SavePlayRecordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        playRecordService.savePlayRecord(userId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deletePlayRecord(@RequestParam(required = false) String key) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (key != null && !key.isEmpty()) {
            playRecordService.deletePlayRecord(userId, key);
        } else {
            playRecordService.deleteAllPlayRecords(userId);
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
