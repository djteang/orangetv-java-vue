package com.orangetv.repository;

import com.orangetv.entity.PlayRecord;
import com.orangetv.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayRecordRepository extends JpaRepository<PlayRecord, Long> {

    List<PlayRecord> findByUserOrderByUpdatedAtDesc(User user);

    Page<PlayRecord> findByUserOrderByUpdatedAtDesc(User user, Pageable pageable);

    List<PlayRecord> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Page<PlayRecord> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Optional<PlayRecord> findByUserAndVideoKey(User user, String videoKey);

    Optional<PlayRecord> findByUserIdAndVideoKey(Long userId, String videoKey);

    boolean existsByUserAndVideoKey(User user, String videoKey);

    void deleteByUserAndVideoKey(User user, String videoKey);

    void deleteAllByUser(User user);
}
