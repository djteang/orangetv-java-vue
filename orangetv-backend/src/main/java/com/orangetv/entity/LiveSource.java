package com.orangetv.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "live_sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LiveSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 直播源名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 直播源唯一标识 KEY
     */
    @Column(name = "source_key", nullable = false, unique = true, length = 50)
    private String sourceKey;

    /**
     * M3U 地址
     */
    @Column(nullable = false, length = 500)
    private String url;

    /**
     * 节目单地址（选填）
     */
    @Column(name = "epg_url", length = 500)
    private String epgUrl;

    /**
     * 自定义 User-Agent（选填）
     */
    @Column(name = "user_agent", length = 200)
    private String userAgent;

    /**
     * 频道数量（缓存）
     */
    @Column(name = "channel_count")
    @Builder.Default
    private Integer channelCount = 0;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 排序顺序
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
