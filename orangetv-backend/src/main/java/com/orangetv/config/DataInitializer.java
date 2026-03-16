package com.orangetv.config;

import com.orangetv.entity.User;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${orangetv.admin.username:admin}")
    private String adminUsername;

    @Value("${orangetv.admin.password:admin}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("owner")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("初始化管理员用户: {}", adminUsername);
        } else {
            log.info("管理员用户已存在: {}", adminUsername);
        }
    }
}
