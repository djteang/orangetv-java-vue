package com.orangetv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.entity.LiveSource;
import com.orangetv.entity.VideoSource;
import com.orangetv.repository.LiveSourceRepository;
import com.orangetv.repository.VideoSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigFileSyncService {

    private final VideoSourceRepository videoSourceRepository;
    private final LiveSourceRepository liveSourceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Parse the config JSON and sync api_site → video_sources, lives → live_sources.
     */
    @CacheEvict(value = {"config", "live"}, allEntries = true)
    @Transactional
    public void syncFromConfigFile(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(configJson);
        } catch (Exception e) {
            log.warn("Config file is not valid JSON, skipping sync: {}", e.getMessage());
            return;
        }

        syncVideoSources(root);
        syncLiveSources(root);
    }

    private void syncVideoSources(JsonNode root) {
        JsonNode apiSite = root.get("api_site");
        if (apiSite == null || !apiSite.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = apiSite.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();

            String api = getTextOrNull(node, "api");
            if (api == null || api.isBlank()) {
                continue;
            }

            String name = getTextOrNull(node, "name");
            if (name == null || name.isBlank()) {
                name = entry.getKey();
            }

            String detail = getTextOrNull(node, "detail");

            VideoSource existing = videoSourceRepository.findByApiUrl(api).orElse(null);
            if (existing != null) {
                existing.setName(name);
                if (detail != null) {
                    existing.setExtConfig(detail);
                }
                videoSourceRepository.save(existing);
                log.debug("Updated video source: {}", name);
            } else {
                VideoSource source = VideoSource.builder()
                        .name(name)
                        .apiUrl(api)
                        .apiType("cms")
                        .enabled(true)
                        .sortOrder((int) videoSourceRepository.count())
                        .extConfig(detail)
                        .build();
                videoSourceRepository.save(source);
                log.info("Created video source from config: {}", name);
            }
        }
    }

    private void syncLiveSources(JsonNode root) {
        JsonNode lives = root.get("lives");
        if (lives == null || !lives.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = lives.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();

            String url = getTextOrNull(node, "url");
            if (url == null || url.isBlank()) {
                continue;
            }

            String name = getTextOrNull(node, "name");
            if (name == null || name.isBlank()) {
                name = entry.getKey();
            }

            String epg = getTextOrNull(node, "epg");

            LiveSource existing = liveSourceRepository.findByUrl(url).orElse(null);
            if (existing != null) {
                existing.setName(name);
                if (epg != null) {
                    existing.setEpgUrl(epg);
                }
                liveSourceRepository.save(existing);
                log.debug("Updated live source: {}", name);
            } else {
                LiveSource source = LiveSource.builder()
                        .name(name)
                        .url(url)
                        .epgUrl(epg)
                        .enabled(true)
                        .sortOrder((int) liveSourceRepository.count())
                        .build();
                liveSourceRepository.save(source);
                log.info("Created live source from config: {}", name);
            }
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
