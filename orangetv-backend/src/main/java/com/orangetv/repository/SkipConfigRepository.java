package com.orangetv.repository;

import com.orangetv.entity.SkipConfig;
import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkipConfigRepository extends JpaRepository<SkipConfig, Long> {

    List<SkipConfig> findByUser(User user);

    List<SkipConfig> findByUserId(Long userId);

    Optional<SkipConfig> findByUserAndVideoKey(User user, String videoKey);

    Optional<SkipConfig> findByUserIdAndVideoKey(Long userId, String videoKey);

    void deleteByUserAndVideoKey(User user, String videoKey);

    void deleteAllByUser(User user);
}
