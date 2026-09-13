package com.orangetv.service;

import com.orangetv.dto.admin.AdminUserHistoryPage;
import com.orangetv.dto.admin.AdminUserHistoryPage.PlayEntry;
import com.orangetv.dto.admin.AdminUserHistoryPage.SearchEntry;
import com.orangetv.dto.admin.AdminUserHistoryPage.SiteSearchEntry;
import com.orangetv.dto.admin.AdminUserHistoryPage.SitePlayEntry;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.PlayRecordRepository;
import com.orangetv.repository.SearchHistoryRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserHistoryService {

    private final UserRepository userRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final PlayRecordRepository playRecordRepository;

    public AdminUserHistoryPage<SiteSearchEntry> getAllSearchHistory(int page, int size) {
        Pageable pageable = pageRequest(page, size);
        return AdminUserHistoryPage.from(searchHistoryRepository.findAllByOrderByUpdatedAtDesc(pageable)
                .map(record -> new SiteSearchEntry(record.getId(), record.getUser().getUsername(),
                        record.getKeyword(), record.getSearchCount(), record.getCreatedAt(), record.getUpdatedAt())));
    }

    public AdminUserHistoryPage<SitePlayEntry> getAllPlayHistory(int page, int size) {
        Pageable pageable = pageRequest(page, size);
        return AdminUserHistoryPage.from(playRecordRepository.findAllByOrderByUpdatedAtDesc(pageable)
                .map(record -> new SitePlayEntry(record.getId(), record.getUser().getUsername(),
                        record.getTitle(), record.getCover(), record.getApiName(), record.getYear(),
                        record.getEpisodeIndex(), record.getEpisodeName(), record.getTotalEpisodes(),
                        record.getProgress(), record.getDuration(), record.getUpdatedAt())));
    }

    public AdminUserHistoryPage<SearchEntry> getSearchHistory(String username, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        User user = findUser(username);
        return AdminUserHistoryPage.from(searchHistoryRepository.findByUserOrderByUpdatedAtDesc(user, pageable)
                .map(record -> new SearchEntry(record.getId(), record.getKeyword(), record.getSearchCount(),
                        record.getCreatedAt(), record.getUpdatedAt())));
    }

    public AdminUserHistoryPage<PlayEntry> getPlayHistory(String username, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        User user = findUser(username);
        return AdminUserHistoryPage.from(playRecordRepository.findByUserOrderByUpdatedAtDesc(user, pageable)
                .map(record -> new PlayEntry(record.getId(), record.getTitle(), record.getCover(),
                        record.getApiName(), record.getYear(), record.getEpisodeIndex(), record.getEpisodeName(),
                        record.getTotalEpisodes(), record.getProgress(), record.getDuration(), record.getUpdatedAt())));
    }

    private User findUser(String username) {
        if (username == null || username.isBlank()) {
            throw ApiException.badRequest("请指定用户");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw ApiException.badRequest("页码不能小于 0，每页数量必须为 1–50");
        }
        // 仓库按更新时间降序；相同时间再按 ID 排序，分页结果保持稳定。
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
