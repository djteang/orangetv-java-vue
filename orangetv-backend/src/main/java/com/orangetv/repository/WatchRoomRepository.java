package com.orangetv.repository;

import com.orangetv.entity.WatchRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchRoomRepository extends JpaRepository<WatchRoom, String> {

    /**
     * 查找用户作为主持人的活跃房间
     */
    List<WatchRoom> findByHostIdAndStatusIn(String hostId, List<String> statuses);

    /**
     * 查找用户作为嘉宾的活跃房间
     */
    List<WatchRoom> findByGuestIdAndStatusIn(String guestId, List<String> statuses);

    /**
     * 查找用户参与的所有活跃房间（作为主持人或嘉宾）
     */
    @Query("SELECT r FROM WatchRoom r WHERE (r.hostId = :userId OR r.guestId = :userId) AND r.status IN :statuses")
    List<WatchRoom> findByUserIdAndStatusIn(@Param("userId") String userId, @Param("statuses") List<String> statuses);

    /**
     * 清理过期房间（超过指定时间的等待中房间）
     */
    @Query("SELECT r FROM WatchRoom r WHERE r.status = 'waiting' AND r.createdAt < :expireTime")
    List<WatchRoom> findExpiredWaitingRooms(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 清理已结束的旧房间
     */
    @Query("SELECT r FROM WatchRoom r WHERE r.status = 'ended' AND r.updatedAt < :expireTime")
    List<WatchRoom> findOldEndedRooms(@Param("expireTime") LocalDateTime expireTime);
}
