package com.orangetv.service;

import com.orangetv.entity.LiveSource;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.LiveSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSourceService {

    private final LiveSourceRepository liveSourceRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllSources() {
        return liveSourceRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LiveSource> getEnabledSources() {
        return liveSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void addSource(String name, String sourceKey, String url, String epgUrl, String userAgent) {
        // 检查 key 是否已存在
        if (liveSourceRepository.existsBySourceKey(sourceKey)) {
            throw ApiException.conflict("直播源 KEY 已存在");
        }

        LiveSource source = LiveSource.builder()
                .name(name)
                .sourceKey(sourceKey)
                .url(url)
                .epgUrl(epgUrl)
                .userAgent(userAgent)
                .enabled(true)
                .sortOrder(liveSourceRepository.findAll().size())
                .build();

        liveSourceRepository.save(source);
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void editSource(Long id, String name, String sourceKey, String url, String epgUrl, String userAgent) {
        LiveSource source = liveSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("直播源不存在"));

        // 如果修改了 key，检查新 key 是否已存在
        if (sourceKey != null && !sourceKey.equals(source.getSourceKey())) {
            if (liveSourceRepository.existsBySourceKey(sourceKey)) {
                throw ApiException.conflict("直播源 KEY 已存在");
            }
            source.setSourceKey(sourceKey);
        }

        if (name != null) source.setName(name);
        if (url != null) source.setUrl(url);
        source.setEpgUrl(epgUrl); // 允许设置为 null
        source.setUserAgent(userAgent); // 允许设置为 null

        liveSourceRepository.save(source);
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void deleteSource(Long id) {
        liveSourceRepository.deleteById(id);
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void enableSource(Long id) {
        LiveSource source = liveSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("直播源不存在"));
        source.setEnabled(true);
        liveSourceRepository.save(source);
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void disableSource(Long id) {
        LiveSource source = liveSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("直播源不存在"));
        source.setEnabled(false);
        liveSourceRepository.save(source);
    }

    @CacheEvict(value = "live", allEntries = true)
    @Transactional
    public void updateChannelCount(Long id, int count) {
        LiveSource source = liveSourceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("直播源不存在"));
        source.setChannelCount(count);
        liveSourceRepository.save(source);
    }

    @Transactional(readOnly = true)
    public LiveSource getBySourceKey(String sourceKey) {
        return liveSourceRepository.findBySourceKey(sourceKey)
                .orElseThrow(() -> ApiException.notFound("直播源不存在"));
    }

    private Map<String, Object> toMap(LiveSource source) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", source.getId());
        map.put("key", source.getSourceKey());
        map.put("name", source.getName());
        map.put("url", source.getUrl());
        map.put("epg", source.getEpgUrl());
        map.put("ua", source.getUserAgent());
        map.put("channelCount", source.getChannelCount());
        map.put("enabled", source.getEnabled());
        map.put("disabled", !source.getEnabled());
        map.put("from", "custom");
        return map;
    }
}
