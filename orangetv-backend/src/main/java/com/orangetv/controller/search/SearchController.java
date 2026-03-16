package com.orangetv.controller.search;

import com.orangetv.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

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
