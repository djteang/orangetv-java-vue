package com.orangetv.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 共同观影房间实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "watch_rooms")
public class WatchRoom {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "host_id", nullable = false)
    private String hostId;

    @Column(name = "guest_id")
    private String guestId;

    @Column(name = "video_title")
    private String videoTitle;

    @Column(name = "video_source")
    private String videoSource;

    @Column(name = "video_id")
    private String videoId;

    @Column(name = "episode_index")
    private Integer episodeIndex;

    @Column(name = "video_url", length = 1024)
    private String videoUrl;

    @Column(name = "video_cover", length = 512)
    private String videoCover;

    /**
     * 房间状态: waiting, active, ended
     */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "waiting";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
