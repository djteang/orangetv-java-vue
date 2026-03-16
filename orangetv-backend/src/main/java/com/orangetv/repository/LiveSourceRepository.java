package com.orangetv.repository;

import com.orangetv.entity.LiveSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LiveSourceRepository extends JpaRepository<LiveSource, Long> {

    List<LiveSource> findByEnabledTrueOrderBySortOrderAsc();

    List<LiveSource> findAllByOrderBySortOrderAsc();

    Optional<LiveSource> findByName(String name);

    Optional<LiveSource> findBySourceKey(String sourceKey);

    boolean existsByName(String name);

    boolean existsBySourceKey(String sourceKey);

    boolean existsByUrl(String url);

    Optional<LiveSource> findByUrl(String url);
}
