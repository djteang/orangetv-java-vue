package com.orangetv.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Playback headers declared by TVBox and IPTV playlists. */
public final class LiveHeaders {
    private LiveHeaders() {}

    public static Map<String, String> fromJson(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) return result;
        if (!node.isObject()) throw new IllegalArgumentException("直播请求头必须是 JSON 对象");
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) throw new IllegalArgumentException("直播请求头的值必须是字符串");
            put(result, entry.getKey(), entry.getValue().textValue());
        });
        return result;
    }

    public static Map<String, String> read(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return fromJson(ContentDecoder.readJson(json)); }
        catch (Exception e) { throw new IllegalArgumentException("直播请求头格式无效", e); }
    }

    public static Map<String, String> fromEncoded(String encoded) {
        if (encoded == null || encoded.isBlank()) return new LinkedHashMap<>();
        if (encoded.length() > 32768) throw new IllegalArgumentException("直播请求头过长");
        try { return read(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalArgumentException("直播请求头格式无效", e); }
    }

    public static Map<String, String> fromOptions(String options) {
        if (options == null || options.isBlank()) return new LinkedHashMap<>();
        if (options.stripLeading().startsWith("{")) return read(options);
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : options.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                put(result, URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    public static Map<String, String> forUrl(Map<String, String> headers, URI original, URI target) {
        Map<String, String> result = new LinkedHashMap<>(headers);
        if (!origin(original).equalsIgnoreCase(origin(target))) {
            result.keySet().removeIf(key -> key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("Authorization"));
        }
        return result;
    }

    private static String origin(URI uri) {
        int port = uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + ":" + port;
    }

    public static void put(Map<String, String> headers, String name, String value) {
        if (name == null || value == null || value.isBlank()) return;
        String canonical = switch (name.toLowerCase(Locale.ROOT)) {
            case "ua", "user-agent", "http-user-agent" -> "User-Agent";
            case "referer", "referrer", "http-referrer", "http-referer" -> "Referer";
            case "origin", "http-origin" -> "Origin";
            case "cookie", "http-cookie" -> "Cookie";
            case "authorization" -> "Authorization";
            case "accept" -> "Accept";
            case "accept-language" -> "Accept-Language";
            default -> null;
        };
        if (canonical == null) return;
        if (value.length() > 8192 || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("直播请求头包含无效字符或内容过长");
        }
        headers.put(canonical, value.trim());
    }
}
