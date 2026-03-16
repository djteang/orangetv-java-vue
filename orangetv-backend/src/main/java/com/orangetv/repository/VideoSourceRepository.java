package com.orangetv.repository;

import com.orangetv.entity.VideoSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoSourceRepository extends JpaRepository<VideoSource, Long> {

    List<VideoSource> findByEnabledTrueOrderBySortOrderAsc();

    List<VideoSource> findAllByOrderBySortOrderAsc();

    Optional<VideoSource> findByName(String name);

    boolean existsByName(String name);

    boolean existsByApiUrl(String apiUrl);

    Optional<VideoSource> findByApiUrl(String apiUrl);
}
