package com.orangetv.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VideoSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    @Column(name = "api_type", length = 50)
    @Builder.Default
    private String apiType = "cms";

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String categories;

    @Column(name = "ext_config", columnDefinition = "TEXT")
    private String extConfig;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
