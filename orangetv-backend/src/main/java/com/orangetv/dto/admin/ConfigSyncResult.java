package com.orangetv.dto.admin;

import java.util.List;

public record ConfigSyncResult(int videoSources, int liveSources, List<String> warnings) {
    public ConfigSyncResult(int videoSources, int liveSources) {
        this(videoSources, liveSources, List.of());
    }
}
