package com.orangetv.repository;

import com.orangetv.entity.UserEnabledApi;
import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEnabledApiRepository extends JpaRepository<UserEnabledApi, Long> {

    List<UserEnabledApi> findByUser(User user);

    List<UserEnabledApi> findByUserId(Long userId);

    List<UserEnabledApi> findByUserAndEnabled(User user, Boolean enabled);

    Optional<UserEnabledApi> findByUserAndApiKey(User user, String apiKey);

    Optional<UserEnabledApi> findByUserIdAndApiKey(Long userId, String apiKey);

    boolean existsByUserAndApiKey(User user, String apiKey);

    void deleteByUserAndApiKey(User user, String apiKey);
}
