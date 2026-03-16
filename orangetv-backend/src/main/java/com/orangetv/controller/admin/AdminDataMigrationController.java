package com.orangetv.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.config.SkipWrapper;
import com.orangetv.dto.ApiResponse;
import com.orangetv.entity.Favorite;
import com.orangetv.entity.PlayRecord;
import com.orangetv.entity.SiteConfig;
import com.orangetv.entity.User;
import com.orangetv.repository.FavoriteRepository;
import com.orangetv.repository.PlayRecordRepository;
import com.orangetv.repository.SiteConfigRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/data_migration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class AdminDataMigrationController {

    private final UserRepository userRepository;
    private final PlayRecordRepository playRecordRepository;
    private final FavoriteRepository favoriteRepository;
    private final SiteConfigRepository siteConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;


    @PostMapping("/export")
    @SkipWrapper
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportData(@RequestBody Map<String, String> request) {
        try {
            String password = request.get("password");
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            Map<String, Object> data = new HashMap<>();

            // 用户数据
            List<Map<String, Object>> usersData = new ArrayList<>();
            for (User user : userRepository.findAll()) {
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("username", user.getUsername());
                u.put("password", user.getPassword());
                u.put("role", user.getRole());
                u.put("enabled", user.getEnabled());
                u.put("nickname", user.getNickname());
                usersData.add(u);
            }
            data.put("users", usersData);

            // 配置数据
            List<Map<String, Object>> configsData = new ArrayList<>();
            for (SiteConfig config : siteConfigRepository.findAll()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("key", config.getConfigKey());
                c.put("value", config.getConfigValue());
                c.put("type", config.getConfigType());
                c.put("description", config.getDescription());
                configsData.add(c);
            }
            data.put("configs", configsData);


            // 播放记录
            List<Map<String, Object>> recordsData = new ArrayList<>();
            for (PlayRecord record : playRecordRepository.findAll()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("username", record.getUser().getUsername());
                r.put("videoKey", record.getVideoKey());
                r.put("title", record.getTitle());
                r.put("cover", record.getCover());
                r.put("episodeIndex", record.getEpisodeIndex());
                r.put("episodeName", record.getEpisodeName());
                r.put("progress", record.getProgress());
                r.put("duration", record.getDuration());
                r.put("apiName", record.getApiName());
                recordsData.add(r);
            }
            data.put("playRecords", recordsData);

            // 收藏
            List<Map<String, Object>> favoritesData = new ArrayList<>();
            for (Favorite fav : favoriteRepository.findAll()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("username", fav.getUser().getUsername());
                f.put("videoKey", fav.getVideoKey());
                f.put("title", fav.getTitle());
                f.put("cover", fav.getCover());
                f.put("apiName", fav.getApiName());
                f.put("vodRemarks", fav.getVodRemarks());
                favoritesData.add(f);
            }
            data.put("favorites", favoritesData);

            byte[] jsonBytes = objectMapper.writeValueAsBytes(data);
            byte[] encrypted = encrypt(jsonBytes, password);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orangetv_backup.dat")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(encrypted);
        } catch (Exception e) {
            log.error("导出数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }


    @PostMapping("/import")
    @Transactional
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Void>> importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password) {
        try {
            byte[] encrypted = file.getBytes();
            byte[] jsonBytes = decrypt(encrypted, password);
            Map<String, Object> data = objectMapper.readValue(jsonBytes, Map.class);

            // 清空现有数据（顺序：先子表再父表）
            favoriteRepository.deleteAll();
            playRecordRepository.deleteAll();

            // 导入用户
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) data.get("users");
            Map<String, User> userMap = new HashMap<>();
            if (usersData != null) {
                for (Map<String, Object> u : usersData) {
                    String username = (String) u.get("username");
                    User existing = userRepository.findByUsername(username).orElse(null);
                    if (existing != null) {
                        existing.setPassword((String) u.get("password"));
                        existing.setRole((String) u.get("role"));
                        existing.setEnabled((Boolean) u.getOrDefault("enabled", true));
                        existing.setNickname((String) u.get("nickname"));
                        userMap.put(username, userRepository.save(existing));
                    } else {
                        User newUser = User.builder()
                                .username(username)
                                .password((String) u.get("password"))
                                .role((String) u.getOrDefault("role", "user"))
                                .enabled((Boolean) u.getOrDefault("enabled", true))
                                .nickname((String) u.get("nickname"))
                                .build();
                        userMap.put(username, userRepository.save(newUser));
                    }
                }
            }


            // 导入配置
            List<Map<String, Object>> configsData = (List<Map<String, Object>>) data.get("configs");
            if (configsData != null) {
                for (Map<String, Object> c : configsData) {
                    String key = (String) c.get("key");
                    String value = (String) c.get("value");
                    String type = (String) c.getOrDefault("type", "string");
                    String desc = (String) c.get("description");
                    SiteConfig existing = siteConfigRepository.findByConfigKey(key).orElse(
                            SiteConfig.builder().configKey(key).configType(type).build());
                    existing.setConfigValue(value);
                    existing.setConfigType(type);
                    if (desc != null) existing.setDescription(desc);
                    siteConfigRepository.save(existing);
                }
            }

            // 导入播放记录
            List<Map<String, Object>> recordsData = (List<Map<String, Object>>) data.get("playRecords");
            if (recordsData != null) {
                for (Map<String, Object> r : recordsData) {
                    User user = userMap.get(r.get("username"));
                    if (user == null) continue;
                    PlayRecord record = PlayRecord.builder()
                            .user(user)
                            .videoKey((String) r.get("videoKey"))
                            .title((String) r.get("title"))
                            .cover((String) r.get("cover"))
                            .episodeIndex(r.get("episodeIndex") != null ? ((Number) r.get("episodeIndex")).intValue() : 0)
                            .episodeName((String) r.get("episodeName"))
                            .progress(r.get("progress") != null ? ((Number) r.get("progress")).doubleValue() : 0.0)
                            .duration(r.get("duration") != null ? ((Number) r.get("duration")).doubleValue() : 0.0)
                            .apiName((String) r.get("apiName"))
                            .build();
                    playRecordRepository.save(record);
                }
            }

            // 导入收藏
            List<Map<String, Object>> favoritesData = (List<Map<String, Object>>) data.get("favorites");
            if (favoritesData != null) {
                for (Map<String, Object> f : favoritesData) {
                    User user = userMap.get(f.get("username"));
                    if (user == null) continue;
                    Favorite fav = Favorite.builder()
                            .user(user)
                            .videoKey((String) f.get("videoKey"))
                            .title((String) f.get("title"))
                            .cover((String) f.get("cover"))
                            .apiName((String) f.get("apiName"))
                            .vodRemarks((String) f.get("vodRemarks"))
                            .build();
                    favoriteRepository.save(fav);
                }
            }

            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            log.error("导入数据失败", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "导入失败: " + e.getMessage()));
        }
    }

    // ─── AES 加密/解密 ───────────────────────────────────────────────────

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;

    private byte[] encrypt(byte[] data, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);

        // [16B salt][16B IV][encrypted data]
        ByteBuffer buffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + encrypted.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(encrypted);
        return buffer.array();
    }

    private byte[] decrypt(byte[] data, String password) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] salt = new byte[SALT_LENGTH];
        buffer.get(salt);
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
