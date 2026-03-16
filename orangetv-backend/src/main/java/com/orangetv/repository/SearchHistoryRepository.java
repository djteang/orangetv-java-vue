package com.orangetv.repository;

import com.orangetv.entity.SearchHistory;
import com.orangetv.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByUserOrderByUpdatedAtDesc(User user);

    Page<SearchHistory> findByUserOrderByUpdatedAtDesc(User user, Pageable pageable);

    List<SearchHistory> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<SearchHistory> findTop10ByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<SearchHistory> findByUserAndKeyword(User user, String keyword);

    Optional<SearchHistory> findByUserIdAndKeyword(Long userId, String keyword);

    void deleteByUserAndKeyword(User user, String keyword);

    void deleteAllByUser(User user);

    // 统计方法
    long countByUpdatedAtAfter(LocalDateTime dateTime);

    long countByUpdatedAtBetween(LocalDateTime start, LocalDateTime end);
}
