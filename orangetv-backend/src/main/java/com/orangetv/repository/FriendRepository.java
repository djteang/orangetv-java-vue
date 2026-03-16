package com.orangetv.repository;

import com.orangetv.entity.Friend;
import com.orangetv.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends JpaRepository<Friend, Long> {

    List<Friend> findByUser(User user);

    List<Friend> findByUserId(Long userId);

    @Query("SELECT f.friend FROM Friend f WHERE f.user.id = :userId")
    List<User> findFriendsByUserId(@Param("userId") Long userId);

    Optional<Friend> findByUserAndFriend(User user, User friend);

    Optional<Friend> findByUserIdAndFriendId(Long userId, Long friendId);

    boolean existsByUserAndFriend(User user, User friend);

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    void deleteByUserAndFriend(User user, User friend);

    void deleteByUserIdAndFriendId(Long userId, Long friendId);
}
