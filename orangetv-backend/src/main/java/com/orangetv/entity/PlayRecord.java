package com.orangetv.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "play_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PlayRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "video_key", nullable = false)
    private String videoKey;

    @Column(length = 255)
    private String title;

    @Column(length = 500)
    private String cover;

    @Column(name = "episode_index")
    @Builder.Default
    private Integer episodeIndex = 0;

    @Column(name = "episode_name", length = 100)
    private String episodeName;

    @Builder.Default
    private Double progress = 0.0;

    @Builder.Default
    private Double duration = 0.0;

    @Column(name = "api_name", length = 100)
    private String apiName;

    @Column(length = 20)
    private String year;

    @Column(name = "total_episodes")
    private Integer totalEpisodes;

    @Column(name = "search_title", length = 255)
    private String searchTitle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
