package com.orangetv.service;

import com.orangetv.entity.Danmu;
import com.orangetv.entity.User;
import com.orangetv.repository.DanmuRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DanmuService {

    private final DanmuRepository danmuRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDanmus(String videoId) {
        List<Danmu> danmus = danmuRepository.findByVideoIdOrderByTimePointAsc(videoId);
        return danmus.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> addDanmu(Long userId, String videoId, String content, Double timePoint, String color, Integer type) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        Danmu danmu = Danmu.builder()
                .videoId(videoId)
                .user(user)
                .content(content)
                .timePoint(timePoint)
                .color(color != null ? color : "#FFFFFF")
                .type(type != null ? type : 0)
                .build();

        danmuRepository.save(danmu);
        return toMap(danmu);
    }

    private Map<String, Object> toMap(Danmu danmu) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", danmu.getId());
        map.put("content", danmu.getContent());
        map.put("time", danmu.getTimePoint());
        map.put("color", danmu.getColor());
        map.put("type", danmu.getType());
        if (danmu.getUser() != null) {
            map.put("user", danmu.getUser().getUsername());
        }
        return map;
    }
}
