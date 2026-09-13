package com.orangetv.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Channel playlists used by M3U subscriptions and TVBox TXT subscriptions. */
public final class LivePlaylistParser {
    private LivePlaylistParser() {}

    public static List<Map<String, Object>> parse(String sourceKey, String content) {
        if (content == null || content.isBlank()) return List.of();
        String trimmed = content.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            try { return parseJson(sourceKey, ContentDecoder.readJson(trimmed)); }
            catch (Exception e) { throw new IllegalArgumentException("JSON 频道列表格式无效", e); }
        }
        if (content.contains("#EXTINF:") || content.strip().startsWith("#EXTM3U")) {
            return M3uParser.parse(sourceKey, content);
        }
        List<Map<String, Object>> channels = new ArrayList<>();
        String group = "无分组";
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int comma = line.indexOf(',');
            if (comma < 1) continue;
            String name = line.substring(0, comma).trim();
            String addresses = line.substring(comma + 1).trim();
            if ("#genre#".equalsIgnoreCase(addresses)) {
                group = name;
                continue;
            }
            if (name.isEmpty()) continue;
            String[] alternatives = addresses.split("#(?=[a-zA-Z][a-zA-Z0-9+.-]*://)");
            for (int i = 0; i < alternatives.length; i++) {
                String[] parts = alternatives[i].split("\\$", 2);
                LiveAddress address = LiveAddress.parse(parts[0].trim());
                String url = address.url();
                if (!isChannelUrl(url)) continue;
                String label = parts.length > 1 ? parts[1].trim() : "";
                String channelName = name;
                if (!label.isEmpty()) channelName += " · " + label;
                else if (alternatives.length > 1) channelName += " · 线路 " + (i + 1);
                Map<String, Object> channel = new HashMap<>();
                channel.put("id", sourceKey + "-" + channels.size());
                channel.put("name", channelName);
                channel.put("url", url);
                if (!address.headers().isEmpty()) channel.put("headers", address.headers());
                channel.put("group", group);
                channel.put("tvgId", null);
                channel.put("logo", null);
                channels.add(channel);
            }
        }
        return channels;
    }

    public static List<Map<String, Object>> parseJson(String sourceKey, JsonNode value) {
        List<Map<String, Object>> channels = new ArrayList<>();
        collectJson(sourceKey, value, "无分组", Map.of(), channels, 0);
        return channels;
    }

    private static void collectJson(String key, JsonNode node, String group, Map<String, String> inherited,
                                    List<Map<String, Object>> channels, int depth) {
        if (node == null || depth > 16) return;
        if (node.isArray()) {
            for (JsonNode child : node) collectJson(key, child, group, inherited, channels, depth + 1);
            return;
        }
        if (!node.isObject()) return;
        Map<String, String> headers = new LinkedHashMap<>(inherited);
        headers.putAll(LiveHeaders.fromJson(node.get("header")));
        headers.putAll(LiveHeaders.fromJson(node.get("headers")));
        LiveHeaders.put(headers, "ua", node.path("ua").asText(""));
        LiveHeaders.put(headers, "referer", node.path("referer").asText(""));
        String channelGroup = node.path("group").asText(group);
        if (node.has("channels")) {
            collectJson(key, node.get("channels"), node.path("name").asText(channelGroup), headers, channels, depth + 1);
            return;
        }
        if (node.has("data") && node.get("data").isArray()) {
            collectJson(key, node.get("data"), channelGroup, headers, channels, depth + 1);
            return;
        }
        String name = node.path("name").asText(node.path("title").asText(""));
        if (name.isBlank()) return;
        JsonNode urls = node.has("urls") ? node.get("urls") : node.get("url");
        if (urls == null) return;
        List<String> addresses = new ArrayList<>();
        if (urls.isArray()) {
            for (JsonNode url : urls) if (url.isTextual()) addresses.add(url.textValue());
        } else if (urls.isTextual()) addresses.add(urls.textValue());
        for (int i = 0; i < addresses.size(); i++) {
            LiveAddress address = LiveAddress.parse(addresses.get(i));
            if (!isChannelUrl(address.url())) continue;
            Map<String, String> merged = new LinkedHashMap<>(headers);
            merged.putAll(address.headers());
            Map<String, Object> channel = new HashMap<>();
            channel.put("id", key + "-" + channels.size());
            channel.put("name", addresses.size() > 1 ? name + " · 线路 " + (i + 1) : name);
            channel.put("url", address.url());
            channel.put("group", channelGroup);
            channel.put("tvgId", node.path("tvgId").asText(null));
            channel.put("logo", node.path("logo").asText(null));
            if (node.hasNonNull("unsupportedReason")) channel.put("unsupportedReason", node.get("unsupportedReason").asText());
            if (!merged.isEmpty()) channel.put("headers", merged);
            channels.add(channel);
        }
    }

    public static boolean looksLikeTxt(String content) {
        String value = content.strip();
        if (value.startsWith("{") || value.startsWith("[") || value.startsWith("//")
                || value.startsWith("/*") || value.startsWith("#EXTM3U")) return false;
        return !parse("", value).isEmpty();
    }

    private static boolean isChannelUrl(String url) {
        try {
            return !url.isBlank() && (URI.create(url).isAbsolute()
                    || url.startsWith("/") || url.startsWith("./") || url.startsWith("../"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
