package com.orangetv.controller.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangetv.dto.ApiResponse;
import com.orangetv.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminCategoryController {

    private final SiteConfigService siteConfigService;
    private final ObjectMapper objectMapper;

    private static final String CONFIG_KEY = "custom_categories";

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> handleCategory(@RequestBody Map<String, Object> request) {
        try {
            String action = (String) request.get("action");
            if (action == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "缺少 action 参数"));
            }

            List<Map<String, Object>> categories = loadCategories();

            switch (action) {
                case "add": {
                    Map<String, Object> category = Map.of(
                            "name", request.getOrDefault("name", ""),
                            "type", request.getOrDefault("type", "movie"),
                            "query", request.getOrDefault("query", ""),
                            "disabled", false,
                            "from", "custom"
                    );
                    categories.add(new java.util.LinkedHashMap<>(category));
                    break;
                }
                case "edit": {
                    Object rawIndex = request.get("index");
                    if (!(rawIndex instanceof Number number) || number.doubleValue() != number.intValue()
                            || number.intValue() < 0 || number.intValue() >= categories.size()) {
                        return ResponseEntity.badRequest().body(ApiResponse.error(400, "分类不存在"));
                    }
                    Map<String, Object> category = categories.get(number.intValue());
                    if (!"custom".equals(category.get("from"))) {
                        return ResponseEntity.badRequest().body(ApiResponse.error(400, "只能编辑自定义分类"));
                    }
                    String name = request.get("name") instanceof String value ? value.trim() : "";
                    String type = request.get("type") instanceof String value ? value : "";
                    String query = request.get("query") instanceof String value ? value.trim() : "";
                    if (name.isEmpty() || query.isEmpty() || !List.of("movie", "tv").contains(type)) {
                        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请填写分类名称、有效类型和搜索关键词"));
                    }
                    category.put("name", name);
                    category.put("type", type);
                    category.put("query", query);
                    break;
                }
                case "delete": {
                    int index = ((Number) request.get("index")).intValue();
                    if (index >= 0 && index < categories.size()) {
                        Map<String, Object> cat = categories.get(index);
                        if (!"custom".equals(cat.get("from"))) {
                            return ResponseEntity.badRequest().body(ApiResponse.error(400, "只能删除自定义分类"));
                        }
                        categories.remove(index);
                    }
                    break;
                }
                case "enable": {
                    int index = ((Number) request.get("index")).intValue();
                    if (index >= 0 && index < categories.size()) {
                        categories.get(index).put("disabled", false);
                    }
                    break;
                }
                case "disable": {
                    int index = ((Number) request.get("index")).intValue();
                    if (index >= 0 && index < categories.size()) {
                        categories.get(index).put("disabled", true);
                    }
                    break;
                }
                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error(400, "未知操作: " + action));
            }

            saveCategories(categories);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(500, "操作失败: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> loadCategories() throws Exception {
        String json = siteConfigService.getConfigValue(CONFIG_KEY);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private void saveCategories(List<Map<String, Object>> categories) throws Exception {
        String json = objectMapper.writeValueAsString(categories);
        siteConfigService.setConfig(CONFIG_KEY, json, "json", "自定义分类配置");
    }
}
