package com.orangetv.repository;

import com.orangetv.entity.SiteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SiteConfigRepository extends JpaRepository<SiteConfig, Long> {

    Optional<SiteConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
