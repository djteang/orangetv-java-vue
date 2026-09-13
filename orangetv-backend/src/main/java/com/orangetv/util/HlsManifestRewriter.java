package com.orangetv.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

public final class HlsManifestRewriter {
    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\"([^\"]+)\"");
    private HlsManifestRewriter() {}

    public static String proxyUrl(String url, String endpoint, Map<String, String> headers, boolean manifest) {
        try {
            String encoded = Base64.getEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));
            String result = endpoint + (endpoint.contains("?") ? "&url=" : "?url=") + URLEncoder.encode(encoded, StandardCharsets.UTF_8);
            if (!headers.isEmpty()) result += "&headers=" + URLEncoder.encode(Base64.getEncoder().encodeToString(
                    new ObjectMapper().writeValueAsBytes(headers)), StandardCharsets.UTF_8);
            return result + (manifest ? "&manifest=true" : "");
        } catch (Exception e) { throw new IllegalArgumentException("无法生成直播代理地址", e); }
    }

    public static String rewrite(String content, URI base, String endpoint, Map<String, String> headers) {
        StringBuilder result = new StringBuilder();
        boolean variant = false;
        for (String raw : content.replace("\uFEFF", "").split("\\R", -1)) {
            String line = raw.trim();
            if (line.startsWith("#")) {
                boolean nested = line.startsWith("#EXT-X-MEDIA:") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")
                        || line.startsWith("#EXT-X-RENDITION-REPORT:");
                var matcher = URI_ATTRIBUTE.matcher(line);
                StringBuffer replaced = new StringBuffer();
                while (matcher.find()) {
                    String value = replace(matcher.group(1), base, endpoint, headers, nested);
                    matcher.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement("URI=\"" + value + "\""));
                }
                matcher.appendTail(replaced);
                result.append(replaced);
                if (line.startsWith("#EXT-X-STREAM-INF:")) variant = true;
            } else if (!line.isEmpty()) {
                URI resolved = LiveAddress.resolve(base, line);
                boolean nested = variant || resolved.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".m3u8");
                result.append(replace(line, base, endpoint, headers, nested));
                variant = false;
            }
            result.append('\n');
        }
        return result.toString();
    }

    private static String replace(String value, URI base, String endpoint, Map<String, String> headers, boolean manifest) {
        URI resolved = LiveAddress.resolve(base, value);
        if (!"http".equalsIgnoreCase(resolved.getScheme()) && !"https".equalsIgnoreCase(resolved.getScheme())) return value;
        return proxyUrl(resolved.toASCIIString(), endpoint, LiveHeaders.forUrl(headers, base, resolved), manifest);
    }
}
