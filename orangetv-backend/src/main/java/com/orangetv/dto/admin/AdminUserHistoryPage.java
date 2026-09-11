package com.orangetv.dto.admin;

import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserHistoryPage<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> AdminUserHistoryPage<T> from(Page<T> page) {
        return new AdminUserHistoryPage<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public record SearchEntry(Long id, String keyword, Integer searchCount,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record PlayEntry(Long id, String title, String cover, String sourceName, String year,
                            Integer episodeIndex, String episodeName, Integer totalEpisodes,
                            Double progress, Double duration, LocalDateTime updatedAt) {}
}
