package com.orangetv.dto.admin;

import java.util.List;

public record ConfigFilePreview(String content, String format, int channelCount, int unsupportedChannelCount,
                                int videoSources, int liveSources, List<String> warnings) {}
