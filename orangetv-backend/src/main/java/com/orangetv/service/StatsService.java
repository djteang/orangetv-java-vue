package com.orangetv.service;

import com.orangetv.util.AppTime;
import com.orangetv.repository.PlayRecordRepository;
import com.orangetv.repository.SearchHistoryRepository;
import com.orangetv.repository.UserRepository;
import com.orangetv.repository.VideoSourceRepository;
import com.orangetv.repository.LiveSourceRepository;
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
    private final PlayRecordRepository playRecordRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final LiveSourceRepository liveSourceRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDate today = AppTime.today();

        // 总用户数
        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        // 今日新增用户
        LocalDateTime todayStart = today.atStartOfDay();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStart);
        stats.put("todayNewUsers", todayNewUsers);

        // 与管理面板的「近 7 日登录」口径保持一致。
        stats.put("activeUsers", userRepository.countByLastLoginAtGreaterThanEqual(todayStart.minusDays(6)));

        // 观影记录按保存的影片进度统计，续播只更新原记录。
        stats.put("totalPlayRecords", playRecordRepository.count());
        stats.put("todayPlayRecords", playRecordRepository
                .countByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(todayStart, todayStart.plusDays(1)));

        // 总搜索次数
        long totalSearches = searchHistoryRepository.count();
        stats.put("totalSearches", totalSearches);

        // 今日搜索次数
        long todaySearches = searchHistoryRepository.countByUpdatedAtAfter(todayStart);
        stats.put("todaySearches", todaySearches);

        // 视频源数量
        long totalVideoSources = videoSourceRepository.count();
        stats.put("totalVideoSources", totalVideoSources);
        stats.put("totalLiveSources", liveSourceRepository.count());

        // 7日用户增长趋势
        List<Map<String, Object>> userTrend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
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
            LocalDate date = today.minusDays(i);
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
