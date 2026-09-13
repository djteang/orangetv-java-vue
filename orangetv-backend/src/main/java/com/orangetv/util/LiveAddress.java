package com.orangetv.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public record LiveAddress(String url, Map<String, String> headers) {
    public static LiveAddress parse(String value) {
        String url = unwrapLink(value == null ? "" : value.trim());
        Map<String, String> headers = new LinkedHashMap<>();
        int options = url.indexOf('|');
        if (options >= 0) {
            headers.putAll(LiveHeaders.fromOptions(url.substring(options + 1)));
            url = url.substring(0, options).trim();
        }
        return new LiveAddress(unwrapSubscription(url), headers);
    }

    public static String unwrapLink(String value) {
        if (value.startsWith("[") && value.endsWith(")")) {
            int marker = value.indexOf("](");
            if (marker > 0) return value.substring(marker + 2, value.length() - 1).trim();
        }
        return value;
    }

    private static String unwrapSubscription(String url) {
        if (!url.startsWith("proxy://")) return url;
        String options = url.substring("proxy://".length()).replaceFirst("^\\?", "");
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : options.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0) values.put(part.substring(0, equals), part.substring(equals + 1));
        }
        if (!"live".equals(values.get("do")) || !values.containsKey("ext")) return url;
        String ext = URLDecoder.decode(values.get("ext").replace("+", "%2B"), StandardCharsets.UTF_8);
        if (ext.startsWith("http://") || ext.startsWith("https://")) return ext;
        try {
            String decoded = new String(Base64.getDecoder().decode(ext), StandardCharsets.UTF_8);
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded;
        } catch (IllegalArgumentException ignored) {}
        return url;
    }

    public static URI httpUri(String url) {
        URI uri = URI.create(url);
        if ((! "http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getRawAuthority() == null) throw new IllegalArgumentException("直播地址必须使用 HTTP 或 HTTPS");
        return uri;
    }

    public static URI resolve(URI base, String reference) {
        // URI.resolve treats a query-only reference as a directory; HLS keeps the current path.
        if (reference.startsWith("?")) {
            String value = base.toASCIIString().split("[?#]", 2)[0];
            return URI.create(value + reference);
        }
        return base.resolve(reference);
    }

    public static String decodeProxyUrl(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded;
        } catch (IllegalArgumentException ignored) {}
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
