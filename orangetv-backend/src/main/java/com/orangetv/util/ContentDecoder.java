package com.orangetv.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Tries to decode content that may be raw JSON, Base58 encoded, or Base64 encoded.
 */
@Slf4j
public class ContentDecoder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ContentDecoder() {}

    /**
     * Attempt to decode the content:
     * 1. If already valid JSON, return as-is
     * 2. Try Base58 decode → if result is valid JSON, return it
     * 3. Try Base64 decode → if result is valid JSON, return it
     * 4. Return raw content as fallback
     */
    public static String tryDecode(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String trimmed = content.trim();

        // 1. Already valid JSON
        if (isValidJson(trimmed)) {
            return trimmed;
        }

        // 2. Try Base58
        try {
            String decoded = Base58.decodeToString(trimmed);
            if (isValidJson(decoded)) {
                log.debug("Content decoded from Base58");
                return decoded;
            }
        } catch (Exception e) {
            // Not Base58, continue
        }

        // 3. Try Base64
        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            if (isValidJson(decoded)) {
                log.debug("Content decoded from Base64");
                return decoded;
            }
        } catch (Exception e) {
            // Not Base64, continue
        }

        // 4. Fallback: return raw
        return content;
    }

    private static boolean isValidJson(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            objectMapper.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
