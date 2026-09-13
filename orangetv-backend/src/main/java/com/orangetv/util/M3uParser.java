package com.orangetv.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class M3uParser {
    private M3uParser() {}

    public static List<Map<String, Object>> parse(String sourceKey, String content) {
        List<Map<String, Object>> channels = new ArrayList<>();
        if (content == null || content.isBlank()) return channels;
        String name = null;
        String logo = null;
        String group = null;
        String tvgId = null;
        String unsupportedReason = null;
        Map<String, String> defaults = new LinkedHashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("#EXTM3U")) readHeaderAttributes(defaults, line);
            if (line.startsWith("#EXTINF:")) {
                headers = new LinkedHashMap<>(defaults);
                readHeaderAttributes(headers, line);
                unsupportedReason = null;
                tvgId = attribute(line, "tvg-id");
                name = attribute(line, "tvg-name");
                if (name == null || name.isBlank()) {
                    int comma = titleSeparator(line);
                    name = comma < 0 ? null : line.substring(comma + 1).trim();
                }
                logo = attribute(line, "tvg-logo");
                group = attribute(line, "group-title");
                if (group == null || group.isBlank()) group = "无分组";
            } else if (line.startsWith("#EXTVLCOPT:")) {
                String option = line.substring("#EXTVLCOPT:".length());
                int equals = option.indexOf('=');
                if (equals > 0) LiveHeaders.put(headers, option.substring(0, equals), option.substring(equals + 1));
            } else if (line.startsWith("#EXTHTTP:")) {
                headers.putAll(LiveHeaders.read(line.substring("#EXTHTTP:".length())));
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=")
                    || line.startsWith("#KODIPROP:inputstream.adaptive.manifest_headers=")) {
                headers.putAll(LiveHeaders.fromOptions(line.substring(line.indexOf('=') + 1)));
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                unsupportedReason = "该频道需要 DRM 播放授权，当前网页播放器不支持此配置。";
            } else if (line.startsWith("#EXTGRP:")) {
                group = line.substring("#EXTGRP:".length()).trim();
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null && !name.isBlank()) {
                LiveAddress address = LiveAddress.parse(line);
                headers.putAll(address.headers());
                Map<String, Object> channel = new HashMap<>();
                channel.put("id", sourceKey + "-" + channels.size());
                channel.put("tvgId", tvgId);
                channel.put("name", name);
                channel.put("url", address.url());
                if (!headers.isEmpty()) channel.put("headers", Map.copyOf(headers));
                if (unsupportedReason != null) channel.put("unsupportedReason", unsupportedReason);
                channel.put("logo", logo);
                channel.put("group", group);
                channels.add(channel);
                name = null;
                logo = null;
                group = null;
                tvgId = null;
            }
        }
        return channels;
    }

    private static void readHeaderAttributes(Map<String, String> headers, String line) {
        for (String name : List.of("http-user-agent", "user-agent", "http-referrer", "http-referer", "referer", "http-origin", "http-cookie")) {
            LiveHeaders.put(headers, name, attribute(line, name));
        }
    }

    public static String attribute(String line, String name) {
        var matcher = Pattern.compile("(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*([\"'])(.*?)\\1")
                .matcher(line);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static int titleSeparator(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quote != 0) {
                if (character == quote) quote = 0;
            } else if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == ',') {
                return i;
            }
        }
        return -1;
    }
}
