package com.orangetv.repository;

import com.orangetv.entity.Favorite;
import com.orangetv.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserOrderByCreatedAtDesc(User user);

    Page<Favorite> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Favorite> findByUserAndVideoKey(User user, String videoKey);

    Optional<Favorite> findByUserIdAndVideoKey(Long userId, String videoKey);

    boolean existsByUserAndVideoKey(User user, String videoKey);

    void deleteByUserAndVideoKey(User user, String videoKey);

    void deleteAllByUser(User user);

    long countByUser(User user);
}
