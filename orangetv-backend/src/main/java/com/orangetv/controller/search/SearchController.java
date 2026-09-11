package com.orangetv.controller.search;

import com.orangetv.service.SearchService;
import com.orangetv.service.SearchStreamService;
import com.orangetv.exception.ApiException;
import com.orangetv.config.SkipWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchStreamService searchStreamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SkipWrapper
    public ResponseEntity<SseEmitter> searchStream(@RequestParam String q) {
        if (q.isBlank() || q.length() > 200) throw ApiException.badRequest("请输入有效的搜索关键词");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(searchStreamService.search(q.trim()));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        Map<String, Object> results = searchService.search(q);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/one")
    public ResponseEntity<Map<String, Object>> searchOne(
            @RequestParam String q,
            @RequestParam String resourceId) {
        Map<String, Object> results = searchService.searchOne(q, resourceId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/resources")
    public ResponseEntity<List<Map<String, Object>>> getResources() {
        return ResponseEntity.ok(searchService.getResources());
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam String q) {
        return ResponseEntity.ok(searchService.getSuggestions(q));
    }

    @GetMapping("/detail/{source}/{id}")
    public ResponseEntity<Map<String, Object>> getVideoDetail(
            @PathVariable String source,
            @PathVariable String id) {
        Map<String, Object> result = searchService.getVideoDetail(source, id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/play-url/{source}/{id}/{episode}")
    public ResponseEntity<String> getPlayUrl(
            @PathVariable String source,
            @PathVariable String id,
            @PathVariable int episode) {
        String url = searchService.getPlayUrl(source, id, episode);
        return ResponseEntity.ok(url);
    }
}
