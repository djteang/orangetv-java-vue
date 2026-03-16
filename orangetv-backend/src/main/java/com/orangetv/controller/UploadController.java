package com.orangetv.controller;

import com.orangetv.dto.ApiResponse;
import com.orangetv.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @PostMapping("/chat-image")
    public ApiResponse<Map<String, String>> uploadChatImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("文件不能为空");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("只支持图片文件");
        }

        // Limit file size (5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw ApiException.badRequest("图片大小不能超过5MB");
        }

        try {
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;

            // Get absolute path for upload directory
            Path uploadDir = Paths.get(uploadPath, "chat").toAbsolutePath();
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(filename);

            log.info("Uploading file to: {}", filePath);

            // Use Files.copy instead of transferTo for better compatibility
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("File uploaded successfully: {}", filePath);

            // Return access URL
            String url = "/uploads/chat/" + filename;
            return ApiResponse.success(Map.of("url", url));
        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage(), e);
            throw ApiException.internal("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 从 URL 下载图片并保存到 uploads
     */
    @PostMapping("/download-image")
    public ApiResponse<Map<String, String>> downloadImage(@RequestBody Map<String, String> request) {
        String imageUrl = request.get("url");
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw ApiException.badRequest("URL 不能为空");
        }

        try {
            // Download image from URL
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw ApiException.badRequest("无法下载图片: HTTP " + response.statusCode());
            }

            // Determine file extension from URL or content-type
            String extension = ".jpg";
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("png")) {
                extension = ".png";
            } else if (contentType.contains("gif")) {
                extension = ".gif";
            } else if (contentType.contains("webp")) {
                extension = ".webp";
            } else if (imageUrl.contains(".")) {
                String urlExt = imageUrl.substring(imageUrl.lastIndexOf("."));
                if (urlExt.matches("\\.(jpg|jpeg|png|gif|webp)(\\?.*)?")) {
                    extension = urlExt.replaceAll("\\?.*", "");
                }
            }

            String filename = UUID.randomUUID().toString() + extension;

            // Save to uploads directory
            Path uploadDir = Paths.get(uploadPath, "backgrounds").toAbsolutePath();
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(filename);

            log.info("Downloading image from {} to {}", imageUrl, filePath);

            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Image downloaded successfully: {}", filePath);

            // Return access URL
            String url = "/uploads/backgrounds/" + filename;
            return ApiResponse.success(Map.of("url", url));
        } catch (IOException | InterruptedException e) {
            log.error("Failed to download image: {}", e.getMessage(), e);
            throw ApiException.internal("图片下载失败: " + e.getMessage());
        }
    }
}
