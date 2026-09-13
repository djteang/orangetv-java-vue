package com.orangetv.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orangetv.dto.admin.ConfigFilePreview;
import com.orangetv.dto.admin.ConfigSyncResult;
import com.orangetv.entity.LiveSource;
import com.orangetv.entity.VideoSource;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.LiveSourceRepository;
import com.orangetv.repository.VideoSourceRepository;
import com.orangetv.util.ContentDecoder;
import com.orangetv.util.M3uParser;
import com.orangetv.util.LivePlaylistParser;
import com.orangetv.util.LiveAddress;
import com.orangetv.util.LiveHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConfigFileSyncService {
    private final VideoSourceRepository videoSourceRepository;
    private final LiveSourceRepository liveSourceRepository;
    private final ObjectMapper objectMapper;
    private final SiteConfigService siteConfigService;

    /** Validate downloaded content without changing any sources. M3U subscriptions become lives entries. */
    public ConfigFilePreview prepareConfigFile(String content, String subscriptionUrl) {
        String decoded = ContentDecoder.tryDecode(content);
        String normalized = decoded == null ? "" : decoded.strip();
        if (normalized.startsWith("\uFEFF")) normalized = normalized.substring(1).strip();
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("订阅内容为空，请检查订阅地址");
        }
        if (normalized.startsWith("#EXTM3U") || LivePlaylistParser.looksLikeTxt(normalized)) {
            return preparePlaylist(normalized, subscriptionUrl);
        }
        List<String> warnings = new ArrayList<>();
        JsonNode root = readConfig(normalized, subscriptionUrl, warnings);
        int channels = 0;
        int unsupported = 0;
        for (JsonNode source : root.path("lives")) {
            for (JsonNode channel : source.path("channels")) {
                channels++;
                try {
                    LiveAddress.httpUri(channel.path("url").asText());
                    if (channel.hasNonNull("unsupportedReason")) unsupported++;
                }
                catch (IllegalArgumentException e) { unsupported++; }
            }
        }
        return new ConfigFilePreview(root.toPrettyString(), "json", channels, unsupported,
                sourceCount(root, "api_site"), sourceCount(root, "lives"), List.copyOf(warnings));
    }

    private ConfigFilePreview preparePlaylist(String content, String subscriptionUrl) {
        if (subscriptionUrl == null || subscriptionUrl.isBlank()) {
            throw ApiException.badRequest("导入频道列表时请同时填写原始 HTTP/HTTPS 订阅地址");
        }
        URI playlistUri = requireHttpUrl(subscriptionUrl.trim(), "频道订阅地址");
        String url = playlistUri.toASCIIString();
        if (Pattern.compile("(?m)^\\s*#EXT-X-(?:TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE|I-FRAME-STREAM-INF|MEDIA):")
                .matcher(content).find()) {
            throw ApiException.badRequest("该地址是单个频道的 HLS 播放清单，请使用包含频道名称的 M3U 订阅列表");
        }
        String key = subscriptionKey(url);
        var channels = LivePlaylistParser.parse(key, content);
        if (channels.isEmpty()) {
            throw ApiException.badRequest("频道列表中未识别到频道，请检查频道名称和播放地址");
        }
        int unsupported = 0;
        for (var channel : channels) {
            try {
                URI streamUri = playlistUri.resolve((String) channel.get("url"));
                if (!isHttp(streamUri)) unsupported++;
            } catch (IllegalArgumentException e) {
                unsupported++;
            }
        }

        String path = playlistUri.getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1).replaceFirst("(?i)\\.m3u8?$", "");
        if (name.isBlank()) name = "直播订阅";
        if (name.length() > 100) name = name.substring(0, 100);
        var root = objectMapper.createObjectNode();
        var source = root.putObject("lives").putObject(key);
        source.put("name", name);
        source.put("url", url);
        source.put("channelCount", channels.size());
        String header = content.split("\\R", 2)[0];
        String epg = M3uParser.attribute(header, "x-tvg-url");
        if (epg == null || epg.isBlank()) epg = M3uParser.attribute(header, "url-tvg");
        if (epg != null && !epg.isBlank()) {
            // A source has one EPG URL; use the first when the playlist declares several.
            try {
                String epgUrl = playlistUri.resolve(epg.split(",")[0].trim()).toString();
                requireHttpUrl(epgUrl, "节目单地址");
                source.put("epg", epgUrl);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("M3U 中的节目单地址无效");
            }
        }
        return new ConfigFilePreview(root.toPrettyString(), content.startsWith("#EXTM3U") ? "m3u" : "txt",
                channels.size(), unsupported, 0, 1, List.of());
    }

    /** Persist configuration and imported sources in the same transaction. */
    @CacheEvict(value = {"config", "live", "epg"}, allEntries = true)
    @Transactional
    public ConfigSyncResult saveConfigFile(String configFile, String subscriptionUrl, Boolean autoUpdate) {
        ConfigSyncResult result = new ConfigSyncResult(0, 0);
        if (configFile != null) {
            String canonical = "";
            if (!configFile.isBlank()) {
                String playlistUrl = subscriptionUrl != null ? subscriptionUrl
                        : siteConfigService.getConfigValue("config_subscription_url");
                ConfigFilePreview preview = prepareConfigFile(configFile, playlistUrl);
                canonical = preview.content();
                ConfigSyncResult synced = syncFromConfigFile(canonical);
                result = new ConfigSyncResult(synced.videoSources(), synced.liveSources(), preview.warnings());
            }
            siteConfigService.setConfig("config_file", canonical, "string", "配置文件内容");
        }
        if (subscriptionUrl != null) {
            String url = subscriptionUrl.trim();
            if (!url.isEmpty()) requireHttpUrl(url, "订阅地址");
            siteConfigService.setConfig("config_subscription_url", url, "string", "配置订阅 URL");
        }
        if (autoUpdate != null) {
            siteConfigService.setConfig("config_subscription_auto_update", autoUpdate.toString(), "boolean", "配置订阅自动更新");
        }
        return result;
    }

    @CacheEvict(value = {"config", "live", "epg"}, allEntries = true)
    @Transactional
    public ConfigSyncResult syncFromConfigFile(String configJson) {
        JsonNode root = readConfig(configJson);
        return new ConfigSyncResult(syncVideoSources(root.get("api_site")), syncLiveSources(root.get("lives")));
    }

    private JsonNode readConfig(String content) {
        return readConfig(content, null, new ArrayList<>());
    }

    private JsonNode readConfig(String content, String subscriptionUrl, List<String> warnings) {
        JsonNode root;
        try {
            root = ContentDecoder.readJson(content);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw ApiException.badRequest("配置格式无效：请提供合法的 JSON 配置、M3U 或 TXT 频道列表");
        }
        if (root != null && root.isArray()) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.putArray("lives").addObject().put("name", "JSON 直播").set("channels", root);
            root = wrapper;
        }
        if (root == null || !root.isObject()) {
            throw ApiException.badRequest("JSON 配置必须是对象，视频源放在 api_site 中，直播源放在 lives 中");
        }
        if (root.has("channels") && !root.has("lives")) {
            ObjectNode source = ((ObjectNode) root).deepCopy();
            ((ObjectNode) root).putArray("lives").add(source);
        }
        if (root.path("sites").isArray() && !root.path("sites").isEmpty()) {
            warnings.add("本次导入 lives 中的直播配置。sites 中的 TVBox 点播/插件源未启用；CSP/JAR、drpy 和加密 ext 需要对应的插件运行服务，不能直接作为网页视频源。");
        }
        normalizeLiveSources((ObjectNode) root, subscriptionUrl, warnings);
        validateSources(root.get("api_site"), false);
        validateSources(root.get("lives"), true);
        return root;
    }

    private int sourceCount(JsonNode root, String section) {
        JsonNode sources = root.get(section);
        return sources != null && sources.isObject() ? sources.size() : 0;
    }

    private String subscriptionKey(String url) {
        return "live_" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private void normalizeLiveSources(ObjectNode root, String subscriptionUrl, List<String> warnings) {
        JsonNode lives = root.get("lives");
        if (lives == null || lives.isNull()) return;
        ObjectNode result = objectMapper.createObjectNode();
        if (lives.isArray()) {
            for (int i = 0; i < lives.size(); i++) {
                String label = "lives[" + i + "]";
                ObjectNode source = normalizeLiveSource(lives.get(i), subscriptionUrl, warnings, label);
                if (source == null) continue;
                String key = text(source, "key", 50, label);
                if (key == null) key = subscriptionKey(source.get("url").asText());
                if (result.has(key)) throw ApiException.badRequest(label + " 的源标识重复");
                result.set(key, source);
            }
        } else if (lives.isObject()) {
            var fields = lives.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                ObjectNode source = normalizeLiveSource(entry.getValue(), subscriptionUrl, warnings, "lives." + entry.getKey());
                if (source != null) result.set(entry.getKey(), source);
            }
        } else if (lives.isTextual()) {
            ObjectNode source = normalizeLiveSource(lives, subscriptionUrl, warnings, "lives");
            if (source != null) result.set(subscriptionKey(source.path("url").asText()), source);
        } else {
            throw ApiException.badRequest("lives 必须是直播源对象、数组或列表地址");
        }
        root.set("lives", result);
    }

    private ObjectNode normalizeLiveSource(JsonNode value, String subscriptionUrl, List<String> warnings, String label) {
        if (!value.isObject() && !value.isTextual()) throw ApiException.badRequest(label + " 必须是直播源对象或地址");
        ObjectNode source = value.isTextual() ? objectMapper.createObjectNode().put("url", value.asText()) : value.deepCopy();
        Map<String, String> headers;
        try {
            headers = LiveHeaders.fromJson(source.get("header"));
            headers.putAll(LiveHeaders.fromJson(source.get("headers")));
            LiveHeaders.put(headers, "ua", source.path("ua").asText(""));
            LiveHeaders.put(headers, "referer", source.path("referer").asText(""));
        } catch (IllegalArgumentException e) { throw ApiException.badRequest(label + "：" + e.getMessage()); }
        String url = text(source, "url", 2000, label);
        if (source.has("channels")) {
            // Older TVBox configs wrap a remote TXT subscription in one proxy:// channel.
            var declared = LivePlaylistParser.parseJson("preview",
                    source.deepCopy().without(List.of("header", "headers", "ua", "referer")));
            JsonNode first = source.path("channels").path(0).path("urls").path(0);
            if (declared.size() == 1 && first.isTextual() && first.asText().startsWith("proxy://")
                    && declared.get(0).get("url").toString().startsWith("http")) {
                url = declared.get(0).get("url").toString();
                Object channelHeaders = declared.get(0).get("headers");
                if (channelHeaders instanceof Map<?, ?> map) {
                    map.forEach((key, header) -> LiveHeaders.put(headers, key.toString(), header.toString()));
                }
                source.remove("channels");
            } else {
                if (declared.isEmpty()) throw ApiException.badRequest(label + " 中没有可识别的频道");
                for (var channel : declared) {
                    String address = channel.get("url").toString();
                    try {
                        URI uri = URI.create(address);
                        if (!uri.isAbsolute()) {
                            if (subscriptionUrl == null || subscriptionUrl.isBlank()) throw ApiException.badRequest("内置频道使用相对地址时需填写原始订阅 URL");
                            uri = LiveAddress.resolve(requireHttpUrl(subscriptionUrl.trim(), "订阅地址"), address);
                        }
                        channel.put("url", uri.toASCIIString());
                        Object logo = channel.get("logo");
                        if (logo instanceof String path && !path.isBlank() && subscriptionUrl != null && !subscriptionUrl.isBlank()) {
                            channel.put("logo", LiveAddress.resolve(URI.create(subscriptionUrl), LiveAddress.unwrapLink(path)).toASCIIString());
                        }
                    } catch (IllegalArgumentException e) { throw ApiException.badRequest(label + " 中的频道地址无效"); }
                }
                source.set("channels", objectMapper.valueToTree(declared));
                source.put("channelCount", declared.size());
                if (url == null || !url.startsWith("inline://")) {
                    // Keep the source identity when an inline subscription updates its channels.
                    url = "inline://" + subscriptionKey((subscriptionUrl == null ? "" : subscriptionUrl) + "#" + label);
                }
            }
        }
        if (url == null) throw ApiException.badRequest(label + " 缺少 url 或 channels，请提供频道列表");
        LiveAddress address;
        try { address = LiveAddress.parse(url); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest(label + "：" + e.getMessage()); }
        headers.putAll(address.headers());
        url = address.url();
        if (url.startsWith("proxy://") || url.startsWith("csp_") || url.startsWith("clan://")) {
            warnings.add(label + " 依赖 TVBox 客户端解析，已跳过；需要提供解析后的 HTTP/HLS/FLV 地址。");
            return null;
        }
        source.put("url", source.has("channels") ? url : resolveConfigUrl(url, subscriptionUrl, label + " 地址"));
        source.remove("header");
        source.set("headers", objectMapper.valueToTree(headers));
        if (headers.containsKey("User-Agent")) source.put("ua", headers.get("User-Agent"));
        String epg = text(source, "epg", 500, label);
        if (epg != null) {
            if (epg.contains("{") || epg.contains("}")) {
                source.remove("epg");
                String warning = "按频道查询的 EPG 模板暂不支持，已跳过其节目单地址；目前支持 XMLTV 节目单。";
                if (!warnings.contains(warning)) warnings.add(warning);
            } else {
                source.put("epg", resolveConfigUrl(epg, subscriptionUrl, label + " 节目单地址"));
            }
        }
        return source;
    }

    private String resolveConfigUrl(String value, String subscriptionUrl, String label) {
        try {
            URI uri = URI.create(LiveAddress.unwrapLink(value));
            if (!uri.isAbsolute()) {
                if (subscriptionUrl == null || subscriptionUrl.isBlank()) {
                    throw ApiException.badRequest(label + " 使用了相对路径，请填写原始订阅 URL");
                }
                uri = LiveAddress.resolve(requireHttpUrl(subscriptionUrl.trim(), "订阅地址"), uri.toString());
            }
            return requireHttpUrl(uri.toString(), label).toASCIIString();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(label + " 格式无效");
        }
    }

    private void validateSources(JsonNode entries, boolean live) {
        if (entries == null || entries.isNull()) return;
        String section = live ? "lives" : "api_site";
        if (!entries.isObject()) throw ApiException.badRequest(section + " 必须是以源标识为键的对象");
        Set<String> urls = new HashSet<>();
        var fields = entries.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String label = section + "." + entry.getKey();
            JsonNode node = entry.getValue();
            if (!node.isObject()) throw ApiException.badRequest(label + " 必须是对象");
            if (entry.getKey().isBlank() || (live && entry.getKey().length() > 50)) {
                throw ApiException.badRequest(label + " 的源标识不能为空，直播源标识不能超过 50 个字符");
            }
            String url = text(node, live ? "url" : "api", 500, label);
            if (url == null || url.isBlank()) throw ApiException.badRequest(label + " 缺少" + (live ? " url" : " api") + " 地址");
            if (!(live && node.has("channels") && url.startsWith("inline://"))) requireHttpUrl(url, label + " 地址");
            if (!urls.add(url)) throw ApiException.badRequest(label + " 的地址在配置中重复，请保留一个源");
            String name = sourceName(entry);
            if (name.length() > 100) throw ApiException.badRequest(label + " 的名称不能超过 100 个字符");
            if (live) {
                String epg = text(node, "epg", 500, label);
                if (epg != null && !epg.isBlank()) requireHttpUrl(epg, label + " 节目单地址");
                text(node, "ua", 200, label);
                JsonNode count = node.get("channelCount");
                if (count != null && !count.isNull()
                        && (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0)) {
                    throw ApiException.badRequest(label + " 的 channelCount 必须是非负整数");
                }
            } else {
                text(node, "detail", Integer.MAX_VALUE, label);
            }
        }
    }

    private int syncVideoSources(JsonNode entries) {
        if (entries == null || entries.isNull()) return 0;
        int count = 0;
        var fields = entries.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode node = entry.getValue();
            String api = text(node, "api", 500, entry.getKey());
            VideoSource source = videoSourceRepository.findByApiUrl(api).orElse(null);
            if (source == null) {
                source = VideoSource.builder().apiUrl(api).apiType("cms").enabled(true)
                        .sortOrder((int) videoSourceRepository.count()).build();
            }
            source.setName(sourceName(entry));
            if (node.has("detail")) source.setExtConfig(text(node, "detail", Integer.MAX_VALUE, entry.getKey()));
            videoSourceRepository.save(source);
            count++;
        }
        return count;
    }

    private int syncLiveSources(JsonNode entries) {
        if (entries == null || entries.isNull()) return 0;
        int count = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = entries.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode node = entry.getValue();
            String url = text(node, "url", 500, entry.getKey());
            LiveSource byKey = liveSourceRepository.findBySourceKey(entry.getKey()).orElse(null);
            LiveSource byUrl = liveSourceRepository.findByUrl(url).orElse(null);
            if (byKey != null && byUrl != null && !byKey.getId().equals(byUrl.getId())) {
                throw ApiException.conflict("直播源 " + entry.getKey() + " 的地址已被其他直播源使用，请检查配置");
            }
            LiveSource source = byKey != null ? byKey : byUrl;
            if (source == null) {
                source = LiveSource.builder().sourceKey(entry.getKey()).enabled(true)
                        .sortOrder((int) liveSourceRepository.count()).build();
            }
            if (!url.equals(source.getUrl())) source.setChannelCount(0);
            source.setName(sourceName(entry));
            source.setUrl(url);
            source.setRequestHeaders(node.has("headers") ? node.get("headers").toString() : null);
            source.setChannelConfig(node.has("channels") ? node.get("channels").toString() : null);
            if (node.has("epg")) source.setEpgUrl(text(node, "epg", 500, entry.getKey()));
            if (node.has("ua")) source.setUserAgent(text(node, "ua", 200, entry.getKey()));
            if (node.hasNonNull("channelCount")) source.setChannelCount(node.get("channelCount").intValue());
            liveSourceRepository.save(source);
            count++;
        }
        return count;
    }

    private String sourceName(Map.Entry<String, JsonNode> entry) {
        String name = text(entry.getValue(), "name", 100, entry.getKey());
        return name == null || name.isBlank() ? entry.getKey() : name;
    }

    private String text(JsonNode node, String field, int maxLength, String label) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw ApiException.badRequest(label + "." + field + " 必须是字符串");
        String result = value.textValue().trim();
        if (result.length() > maxLength) {
            throw ApiException.badRequest(label + "." + field + " 不能超过 " + maxLength + " 个字符");
        }
        return result.isEmpty() ? null : result;
    }

    public static URI requireHttpUrl(String url, String label) {
        try {
            if (url != null && url.length() <= 500) {
                URI uri = URI.create(url);
                if (isHttp(uri) && uri.getRawAuthority() != null) {
                    if (uri.getHost() == null) {
                        URL parsed = uri.toURL();
                        String host = IDN.toASCII(parsed.getHost(), IDN.USE_STD3_ASCII_RULES);
                        String authority = (parsed.getUserInfo() == null ? "" : parsed.getUserInfo() + "@")
                                + host + (parsed.getPort() < 0 ? "" : ":" + parsed.getPort());
                        String suffix = url.substring(url.indexOf("://") + 3 + uri.getRawAuthority().length());
                        uri = URI.create(uri.getScheme() + "://" + authority + suffix);
                    }
                    if (uri.getHost() != null && uri.getPort() <= 65535 && uri.toASCIIString().length() <= 500) return uri;
                }
            }
        } catch (IllegalArgumentException | MalformedURLException ignored) {
            // Return an actionable validation error rather than a URI parser exception.
        }
        throw ApiException.badRequest(label + " 必须是有效的 HTTP/HTTPS 地址，且不能超过 500 个字符");
    }

    private static boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }
}
