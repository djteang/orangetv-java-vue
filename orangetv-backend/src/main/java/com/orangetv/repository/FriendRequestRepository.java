package com.orangetv.repository;

import com.orangetv.entity.FriendRequest;
import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    List<FriendRequest> findByToUserAndStatus(User toUser, String status);

    List<FriendRequest> findByToUserIdAndStatus(Long toUserId, String status);

    List<FriendRequest> findByFromUserAndStatus(User fromUser, String status);

    List<FriendRequest> findByFromUserIdAndStatus(Long fromUserId, String status);

    List<FriendRequest> findByToUserId(Long toUserId);

    List<FriendRequest> findByToUserIdOrderByCreatedAtDesc(Long toUserId);

    List<FriendRequest> findByFromUserIdOrderByCreatedAtDesc(Long fromUserId);

    Optional<FriendRequest> findByFromUserAndToUser(User fromUser, User toUser);

    Optional<FriendRequest> findByFromUserIdAndToUserId(Long fromUserId, Long toUserId);

    boolean existsByFromUserAndToUserAndStatus(User fromUser, User toUser, String status);
}
