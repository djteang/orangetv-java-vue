package com.orangetv.service;

import com.orangetv.entity.SearchHistory;
import com.orangetv.entity.User;
import com.orangetv.exception.ApiException;
import com.orangetv.repository.SearchHistoryRepository;
import com.orangetv.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    private static final int MAX_HISTORY_SIZE = 20;

    @Transactional(readOnly = true)
    public List<String> getSearchHistory(Long userId) {
        return searchHistoryRepository.findTop10ByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(SearchHistory::getKeyword)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addSearchHistory(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));

        SearchHistory history = searchHistoryRepository.findByUserIdAndKeyword(userId, keyword.trim())
                .orElse(SearchHistory.builder()
                        .user(user)
                        .keyword(keyword.trim())
                        .searchCount(0)
                        .build());

        history.setSearchCount(history.getSearchCount() + 1);
        searchHistoryRepository.save(history);
    }

    @Transactional
    public void deleteSearchHistory(Long userId, String keyword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        searchHistoryRepository.deleteByUserAndKeyword(user, keyword);
    }

    @Transactional
    public void deleteAllSearchHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        searchHistoryRepository.deleteAllByUser(user);
    }
}
