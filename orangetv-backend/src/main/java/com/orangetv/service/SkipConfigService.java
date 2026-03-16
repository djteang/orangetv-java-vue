package com.orangetv.service;

import com.orangetv.dto.user.SaveSkipConfigRequest;
import com.orangetv.dto.user.SkipConfigDto;
import com.orangetv.entity.SkipConfig;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.SkipConfigRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkipConfigService {

    private final SkipConfigRepository skipConfigRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, SkipConfigDto> getAllSkipConfigs(Long userId) {
        List<SkipConfig> configs = skipConfigRepository.findByUserId(userId);
        Map<String, SkipConfigDto> result = new LinkedHashMap<>();

        for (SkipConfig config : configs) {
            result.put(config.getVideoKey(), toDto(config));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public SkipConfigDto getSkipConfig(Long userId, String key) {
        return skipConfigRepository.findByUserIdAndVideoKey(userId, key)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void saveSkipConfig(Long userId, SaveSkipConfigRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        SkipConfig config = skipConfigRepository.findByUserIdAndVideoKey(userId, request.getKey())
                .orElse(SkipConfig.builder()
                        .user(user)
                        .videoKey(request.getKey())
                        .build());

        SkipConfigDto dto = request.getEffectiveConfig();
        config.setSkipIntro(dto.getIntroTime() != null ? dto.getIntroTime() : 0);
        config.setSkipOutro(dto.getOutroTime() != null ? dto.getOutroTime() : 0);

        skipConfigRepository.save(config);
    }

    @Transactional
    public void deleteSkipConfig(Long userId, String key) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        skipConfigRepository.deleteByUserAndVideoKey(user, key);
    }

    @Transactional
    public void deleteAllSkipConfigs(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        skipConfigRepository.deleteAllByUser(user);
    }

    private SkipConfigDto toDto(SkipConfig config) {
        return SkipConfigDto.builder()
                .key(config.getVideoKey())
                .enable(true)
                .introTime(config.getSkipIntro())
                .outroTime(config.getSkipOutro())
                .build();
    }
}
