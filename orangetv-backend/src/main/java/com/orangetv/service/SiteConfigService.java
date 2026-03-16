package com.orangetv.service;

import com.orangetv.entity.SiteConfig;
import com.orangetv.repository.SiteConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final SiteConfigRepository siteConfigRepository;

    @Cacheable(value = "config", key = "'site_config'")
    @Transactional(readOnly = true)
    public Map<String, Object> getAllConfig() {
        List<SiteConfig> configs = siteConfigRepository.findAll();
        Map<String, Object> result = new HashMap<>();

        for (SiteConfig config : configs) {
            result.put(config.getConfigKey(), parseValue(config));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        return siteConfigRepository.findByConfigKey(key)
                .map(SiteConfig::getConfigValue)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key, String defaultValue) {
        return siteConfigRepository.findByConfigKey(key)
                .map(SiteConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Boolean getBooleanConfig(String key, Boolean defaultValue) {
        return siteConfigRepository.findByConfigKey(key)
                .map(SiteConfig::getBooleanValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Integer getIntConfig(String key, Integer defaultValue) {
        return siteConfigRepository.findByConfigKey(key)
                .map(SiteConfig::getIntValue)
                .orElse(defaultValue);
    }

    @CacheEvict(value = "config", key = "'site_config'")
    @Transactional
    public void setConfig(String key, String value, String type, String description) {
        SiteConfig config = siteConfigRepository.findByConfigKey(key)
                .orElse(SiteConfig.builder()
                        .configKey(key)
                        .configType(type != null ? type : "string")
                        .build());

        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }

        siteConfigRepository.save(config);
    }

    @CacheEvict(value = "config", key = "'site_config'")
    @Transactional
    public void setConfig(String key, String value) {
        setConfig(key, value, "string", null);
    }

    private Object parseValue(SiteConfig config) {
        if (config.getConfigValue() == null) {
            return null;
        }

        switch (config.getConfigType()) {
            case "boolean":
                return config.getBooleanValue();
            case "number":
                return config.getIntValue();
            case "json":
                try {
                    return new ObjectMapper().readValue(config.getConfigValue(), Object.class);
                } catch (Exception e) {
                    return config.getConfigValue();
                }
            default:
                return config.getStringValue();
        }
    }
}
