package com.orangetv.service;

import com.orangetv.util.HlsManifestRewriter;
import com.orangetv.util.LiveAddress;
import com.orangetv.util.LiveHeaders;
import java.net.URLEncoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class LiveStreamService {
    private final RestTemplate http;

    public LiveStreamService(@Qualifier("liveRestTemplate") RestTemplate http) {
        this.http = http;
    }

    @FunctionalInterface
    interface Reader<T> { T read(URI url, ClientHttpResponse response, Map<String, String> headers) throws IOException; }
    private record Hop<T>(URI redirect, T value) {}

    private <T> T get(String address, Map<String, String> declaredHeaders, String range, Reader<T> reader) {
        URI url = LiveAddress.httpUri(address);
        Map<String, String> headers = new LinkedHashMap<>(declaredHeaders);
        headers.putIfAbsent("User-Agent", "AptvPlayer/1.4.10");
        for (int redirects = 0; redirects <= 5; redirects++) {
            URI current = url;
            Hop<T> hop = http.execute(current, HttpMethod.GET, request -> {
                headers.forEach(request.getHeaders()::set);
                if (range != null) request.getHeaders().set("Range", range);
            }, response -> {
                if (response.getStatusCode().is3xxRedirection()) {
                    String location = response.getHeaders().getFirst("Location");
                    if (location == null) throw new IOException("直播跳转缺少目标地址");
                    return new Hop<>(LiveAddress.httpUri(LiveAddress.resolve(current, location).toASCIIString()), null);
                }
                return new Hop<>(null, reader.read(current, response, Map.copyOf(headers)));
            });
            if (hop == null) throw new IllegalArgumentException("直播源返回空响应");
            if (hop.redirect() == null) return hop.value();
            URI next = hop.redirect();
            Map<String, String> scoped = LiveHeaders.forUrl(headers, current, next);
            headers.clear();
            headers.putAll(scoped);
            url = next;
        }
        throw new IllegalArgumentException("直播源跳转次数过多");
    }

    public Map<String, Object> resolve(String url, Map<String, String> headers) {
        try {
            return get(url, headers, null, (resolved, response, effectiveHeaders) -> {
                byte[] prefix = response.getBody().readNBytes(1024);
                String contentType = response.getHeaders().getFirst("Content-Type");
                String type = detect(prefix, contentType, resolved);
                if (type.equals("unsupported")) {
                    return Map.<String, Object>of("ok", false, "error", "直播地址未返回可识别的 HLS、FLV、MPEG-TS 或视频内容，可能需要 TVBox 插件解析。");
                }
                return Map.<String, Object>of("ok", true, "url", resolved.toASCIIString(), "type", type);
            });
        } catch (Exception e) {
            return Map.of("ok", false, "error", errorMessage(e), "status", errorStatus(e));
        }
    }

    static String detect(byte[] prefix, String contentType, URI url) {
        String text = new String(prefix, StandardCharsets.UTF_8).replace("\uFEFF", "").stripLeading();
        if (text.startsWith("#EXTM3U")) return "hls";
        if (prefix.length >= 3 && prefix[0] == 'F' && prefix[1] == 'L' && prefix[2] == 'V') return "flv";
        if (prefix.length >= 377 && prefix[0] == 0x47 && prefix[188] == 0x47 && prefix[376] == 0x47) return "mpegts";
        if (prefix.length >= 8 && new String(prefix, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) return "native";
        String mediaType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (text.startsWith("<") || text.startsWith("{") || text.startsWith("[")) return "unsupported";
        if (mediaType.contains("flv")) return "flv";
        if (mediaType.contains("mp2t")) return "mpegts";
        if (mediaType.contains("mpegurl")) return "hls";
        if (mediaType.contains("video/mp4") || mediaType.contains("video/webm") || mediaType.contains("video/ogg")) return "native";
        return "unsupported";
    }

    public void proxy(String url, Map<String, String> headers, boolean manifest,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            get(url, headers, request.getHeader("Range"), (resolved, upstream, effectiveHeaders) -> {
                response.setStatus(upstream.getStatusCode().value());
                response.setHeader("X-Upstream-Url", resolved.toASCIIString());
                response.setHeader("Access-Control-Expose-Headers", "X-Upstream-Url, Content-Range");
                response.setHeader("X-Accel-Buffering", "no");
                response.setHeader("Cache-Control", "no-store");
                if (manifest) {
                    byte[] bytes = upstream.getBody().readNBytes(2 * 1024 * 1024 + 1);
                    if (bytes.length > 2 * 1024 * 1024) throw new IOException("直播清单过大");
                    String content = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
                    if (!content.stripLeading().startsWith("#EXTM3U")) throw new IOException("直播源未返回 HLS 清单");
                    response.setContentType("application/vnd.apple.mpegurl");
                    response.setCharacterEncoding("UTF-8");
                    String endpoint = request.getContextPath() + "/api/live/stream";
                    String token = request.getParameter("token");
                    if (token != null && !token.isBlank()) endpoint += "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
                    response.getWriter().write(HlsManifestRewriter.rewrite(content, resolved, endpoint, effectiveHeaders));
                } else {
                    for (String header : new String[]{"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"}) {
                        String value = upstream.getHeaders().getFirst(header);
                        if (value != null) response.setHeader(header, value);
                    }
                    InputStream body = upstream.getBody();
                    byte[] buffer = new byte[16 * 1024];
                    int length;
                    while ((length = body.read(buffer)) != -1) {
                        response.getOutputStream().write(buffer, 0, length);
                        response.flushBuffer();
                    }
                }
                return Boolean.TRUE;
            });
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(errorStatus(e), errorMessage(e));
            }
        }
    }

    private int errorStatus(Exception error) {
        if (error instanceof HttpStatusCodeException httpError) return httpError.getStatusCode().value();
        if (error instanceof ResourceAccessException) return 504;
        return 502;
    }

    private String errorMessage(Exception error) {
        if (error instanceof HttpStatusCodeException httpError) {
            return "直播源返回 HTTP " + httpError.getStatusCode().value() + "，请检查地址、授权和请求头。";
        }
        if (error instanceof ResourceAccessException) {
            return "服务器无法连接直播源，请检查源是否有效及服务器的 IPv4/IPv6 网络。";
        }
        return "直播源未返回可播放内容，请重试或切换其他频道。";
    }
}
