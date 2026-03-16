package com.orangetv.repository;

import com.orangetv.entity.Danmu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DanmuRepository extends JpaRepository<Danmu, Long> {

    List<Danmu> findByVideoIdOrderByTimePointAsc(String videoId);

    Page<Danmu> findByVideoIdOrderByTimePointAsc(String videoId, Pageable pageable);

    @Query("SELECT d FROM Danmu d WHERE d.videoId = :videoId AND d.timePoint BETWEEN :start AND :end ORDER BY d.timePoint ASC")
    List<Danmu> findByVideoIdAndTimeRange(@Param("videoId") String videoId, @Param("start") Double start, @Param("end") Double end);

    long countByVideoId(String videoId);

    void deleteByVideoId(String videoId);
}
