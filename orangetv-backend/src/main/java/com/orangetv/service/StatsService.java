package com.orangetv.service;

import com.orangetv.repository.SearchHistoryRepository;
import com.orangetv.repository.UserRepository;
import com.orangetv.repository.VideoSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final VideoSourceRepository videoSourceRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 总用户数
        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        // 今日新增用户
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStart);
        stats.put("todayNewUsers", todayNewUsers);

        // 总搜索次数
        long totalSearches = searchHistoryRepository.count();
        stats.put("totalSearches", totalSearches);

        // 今日搜索次数
        long todaySearches = searchHistoryRepository.countByUpdatedAtAfter(todayStart);
        stats.put("todaySearches", todaySearches);

        // 视频源数量
        long totalVideoSources = videoSourceRepository.count();
        stats.put("totalVideoSources", totalVideoSources);

        // 7日用户增长趋势
        List<Map<String, Object>> userTrend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            long count = userRepository.countByCreatedAtBetween(dayStart, dayEnd);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("count", count);
            userTrend.add(dayData);
        }
        stats.put("userTrend", userTrend);

        // 7日搜索趋势
        List<Map<String, Object>> searchTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            long count = searchHistoryRepository.countByUpdatedAtBetween(dayStart, dayEnd);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.format(formatter));
            dayData.put("count", count);
            searchTrend.add(dayData);
        }
        stats.put("searchTrend", searchTrend);

        return stats;
    }
}
