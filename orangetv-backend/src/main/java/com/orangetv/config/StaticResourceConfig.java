package com.orangetv.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @PostConstruct
    public void init() throws IOException {
        // Create upload directories on startup
        Path chatUploadDir = Paths.get(uploadPath, "chat").toAbsolutePath();
        Files.createDirectories(chatUploadDir);
        log.info("Upload directory initialized: {}", chatUploadDir);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolutePath = Paths.get(uploadPath).toAbsolutePath();
        String resourceLocation = "file:" + absolutePath.toString().replace("\\", "/") + "/";
        log.info("Configuring static resource handler: /uploads/** -> {}", resourceLocation);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}
