package com.orangetv.repository;

import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%")
    List<User> searchByKeyword(@Param("keyword") String keyword);

    List<User> findByRole(String role);

    @Query("SELECT u FROM User u JOIN u.groups g WHERE g.name = :groupName")
    List<User> findByGroupName(@Param("groupName") String groupName);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.groups WHERE u.username = :username")
    Optional<User> findByUsernameWithGroups(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.enabledApis WHERE u.id = :id")
    Optional<User> findByIdWithEnabledApis(@Param("id") Long id);

    List<User> findByUsernameIn(Collection<String> usernames);

    // 统计方法
    long countByCreatedAtAfter(LocalDateTime dateTime);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
