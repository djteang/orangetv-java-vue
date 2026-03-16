package com.orangetv.service;

import com.orangetv.dto.user.PlayRecordDto;
import com.orangetv.dto.user.SavePlayRecordRequest;
import com.orangetv.entity.PlayRecord;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.PlayRecordRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayRecordService {

    private final PlayRecordRepository playRecordRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, PlayRecordDto> getAllPlayRecords(Long userId) {
        List<PlayRecord> records = playRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        // 相同标题只保留播放进度最大的那条（去除不同片源的重复记录）
        Map<String, PlayRecord> titleToRecord = new LinkedHashMap<>();
        for (PlayRecord record : records) {
            String titleKey = buildTitleKey(record);
            PlayRecord existing = titleToRecord.get(titleKey);
            if (existing == null || progressRatio(record) > progressRatio(existing)) {
                titleToRecord.put(titleKey, record);
            }
        }

        // 按 updatedAt 降序重新排列（LinkedHashMap 按插入顺序，先排序再插入）
        Map<String, PlayRecordDto> result = new LinkedHashMap<>();
        titleToRecord.values().stream()
                .sorted(Comparator.comparing(PlayRecord::getUpdatedAt).reversed())
                .forEach(r -> result.put(r.getVideoKey(), toDto(r)));

        return result;
    }

    /** 标题去重用的 key：优先用 searchTitle，否则用 title，均转小写 trim */
    private String buildTitleKey(PlayRecord record) {
        String key = (record.getSearchTitle() != null && !record.getSearchTitle().isBlank())
                ? record.getSearchTitle()
                : record.getTitle();
        return key != null ? key.trim().toLowerCase() : "";
    }

    /** 播放进度比例（0~1），用于比较哪条进度更大 */
    private double progressRatio(PlayRecord record) {
        if (record.getDuration() == null || record.getDuration() <= 0) return 0;
        return record.getProgress() != null ? record.getProgress() / record.getDuration() : 0;
    }

    @Transactional(readOnly = true)
    public PlayRecordDto getPlayRecord(Long userId, String key) {
        return playRecordRepository.findByUserIdAndVideoKey(userId, key)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void savePlayRecord(Long userId, SavePlayRecordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        PlayRecord record = playRecordRepository.findByUserIdAndVideoKey(userId, request.getKey())
                .orElse(PlayRecord.builder()
                        .user(user)
                        .videoKey(request.getKey())
                        .build());

        PlayRecordDto dto = request.getEffectiveRecord();
        record.setTitle(dto.getTitle());
        record.setCover(dto.getCover());
        record.setApiName(dto.getSourceName());
        record.setEpisodeIndex(dto.getIndex());
        record.setProgress(dto.getPlayTime());
        record.setDuration(dto.getTotalTime());
        record.setYear(dto.getYear());
        record.setTotalEpisodes(dto.getTotalEpisodes());
        record.setSearchTitle(dto.getSearchTitle());

        playRecordRepository.save(record);
    }

    @Transactional
    public void deletePlayRecord(Long userId, String key) {
        playRecordRepository.findByUserIdAndVideoKey(userId, key)
                .ifPresent(playRecordRepository::delete);
    }

    @Transactional
    public void deleteAllPlayRecords(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        playRecordRepository.deleteAllByUser(user);
    }

    private PlayRecordDto toDto(PlayRecord record) {
        return PlayRecordDto.builder()
                .key(record.getVideoKey())
                .title(record.getTitle())
                .cover(record.getCover())
                .sourceName(record.getApiName())
                .index(record.getEpisodeIndex())
                .playTime(record.getProgress())
                .totalTime(record.getDuration())
                .year(record.getYear())
                .totalEpisodes(record.getTotalEpisodes())
                .searchTitle(record.getSearchTitle())
                .saveTime(record.getUpdatedAt() != null ?
                        record.getUpdatedAt().toEpochSecond(ZoneOffset.UTC) * 1000 : null)
                .build();
    }
}
