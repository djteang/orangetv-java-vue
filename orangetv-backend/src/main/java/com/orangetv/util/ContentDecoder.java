package com.orangetv.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes JSON or M3U content, including Base58 and Base64 subscriptions.
 */
@Slf4j
public class ContentDecoder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ContentDecoder() {}

    /**
     * Attempt to decode the content:
     * 1. If already JSON or M3U, return as-is
     * 2. Try Base58 decode → if result is JSON or M3U, return it
     * 3. Try Base64 decode → if result is JSON or M3U, return it
     * 4. Return raw content as fallback
     */
    public static String tryDecode(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String trimmed = normalize(content);

        // 1. Already JSON or M3U
        if (isSupportedContent(trimmed)) {
            return trimmed;
        }

        // 2. Try Base58
        try {
            String decoded = normalize(Base58.decodeToString(trimmed));
            if (isSupportedContent(decoded)) {
                log.debug("Content decoded from Base58");
                return decoded;
            }
        } catch (Exception e) {
            // Not Base58, continue
        }

        // 3. Try Base64
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            String decoded = normalize(new String(bytes, StandardCharsets.UTF_8));
            if (isSupportedContent(decoded)) {
                log.debug("Content decoded from Base64");
                return decoded;
            }
        } catch (Exception e) {
            // Not Base64, continue
        }

        // 4. Fallback: return raw
        return content;
    }

    private static String normalize(String content) {
        String result = content.strip();
        return result.startsWith("\uFEFF") ? result.substring(1).strip() : result;
    }

    private static boolean isSupportedContent(String str) {
        return str.startsWith("#EXTM3U") || isValidJson(str) || LivePlaylistParser.looksLikeTxt(str);
    }

    public static JsonNode readJson(String content) throws JsonProcessingException {
        return objectMapper.reader()
                .with(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .with(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(normalize(content));
    }

    private static boolean isValidJson(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            return readJson(str) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
