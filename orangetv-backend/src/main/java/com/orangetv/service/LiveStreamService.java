package com.orangetv.service;

import com.orangetv.util.HlsManifestRewriter;
import com.orangetv.util.LiveAddress;
import com.orangetv.util.LiveHeaders;
import java.net.URLEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
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
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
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
                    if (location == null) throw new IllegalArgumentException("直播跳转缺少目标地址");
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
                String contentType = response.getHeaders().getFirst("Content-Type");
                String type = probeType(response.getBody(), contentType, resolved);
                if (type.equals("unsupported")) {
                    return Map.<String, Object>of("ok", false, "error", "直播地址未返回可识别的 HLS、FLV、MPEG-TS 或视频内容，可能需要 TVBox 插件解析。");
                }
                return Map.<String, Object>of("ok", true, "url", resolved.toASCIIString(), "type", type);
            });
        } catch (Exception e) {
            logFailure("resolve", url, e);
            return Map.of("ok", false, "error", errorMessage(e), "status", errorStatus(e));
        }
    }

    private String probeType(InputStream body, String contentType, URI url) throws IOException {
        byte[] buffer = new byte[1024];
        int size = 0;
        while (size < buffer.length) {
            int count = body.read(buffer, size, buffer.length - size);
            if (count < 0) break;
            if (count == 0) continue;
            size += count;
            byte[] prefix = Arrays.copyOf(buffer, size);
            String type = detect(prefix, contentType, url);
            String text = new String(prefix, StandardCharsets.UTF_8).replace("\uFEFF", "").stripLeading();
            // PHP/连续流可能只先发送少量字节；识别后立即关闭探测连接，不能等待凑满 1 KB。
            if (!type.equals("unsupported") || text.startsWith("<") || text.startsWith("{") || text.startsWith("[")) return type;
        }
        return detect(Arrays.copyOf(buffer, size), contentType, url);
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
                    if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("直播清单过大");
                    String content = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
                    if (!content.stripLeading().startsWith("#EXTM3U")) throw new IllegalArgumentException("直播源未返回 HLS 清单");
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
                logFailure("stream", url, e);
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

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    private void logFailure(String operation, String address, Exception error) {
        String endpoint = "invalid URL";
        try {
            URI uri = LiveAddress.httpUri(address);
            // 保留排查所需的主机、端口和路径，不把签名参数或账号写入日志。
            endpoint = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + uri.getRawPath();
        } catch (IllegalArgumentException ignored) {}
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        log.warn("Live {} failed for {}: {} ({})", operation, endpoint, errorMessage(error), cause.getClass().getSimpleName());
    }

    private String errorMessage(Exception error) {
        if (error instanceof HttpStatusCodeException httpError) {
            return "直播源返回 HTTP " + httpError.getStatusCode().value() + "，请检查地址、授权和请求头。";
        }
        if (error instanceof ResourceAccessException) {
            if (hasCause(error, ConnectTimeoutException.class)) return "服务器连接直播源超时，请检查服务器到直播源的网络和端口是否可达。";
            if (hasCause(error, SocketTimeoutException.class)) return "直播源读取超时，服务器未及时收到直播数据，请重试或检查源站响应。";
            if (hasCause(error, UnknownHostException.class)) return "服务器无法解析直播源域名，请检查服务器 DNS 配置。";
            if (hasCause(error, NoRouteToHostException.class)) return "服务器无法到达直播源，请检查服务器路由、防火墙及 IPv4/IPv6 网络。";
            if (hasCause(error, ConnectException.class)) return "直播源拒绝服务器连接，请检查源站端口是否开放或是否限制服务器 IP。";
            return "服务器无法连接直播源，请检查源是否有效及服务器的 IPv4/IPv6 网络。";
        }
        return "直播源未返回可播放内容，请重试或切换其他频道。";
    }
}
