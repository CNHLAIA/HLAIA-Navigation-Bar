package com.hlaia.controller;

import com.hlaia.common.Result;
import com.hlaia.dto.response.SearchResponse;
import com.hlaia.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【搜索 API 控制器】—— 提供全文搜索和搜索建议接口
 *
 * API 设计：
 *   GET /api/search?keyword=xxx&page=1&size=20  —— 全文搜索
 *   GET /api/search/suggest?keyword=xxx          —— 搜索建议（Autocomplete）
 *
 * 搜索接口使用 GET 方法（搜索是读操作，不修改数据）。
 * keyword 作为查询参数（?keyword=xxx），page 和 size 有默认值。
 * 使用 @AuthenticationPrincipal Long userId 获取当前用户 ID（与其他 Controller 一致）。
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "全文搜索 API")
public class SearchController {

    private final SearchService searchService;

    /**
     * 全文搜索
     *
     * 请求示例：GET /api/search?keyword=github&page=1&size=20
     * 返回包含书签和文件夹的混合搜索结果，匹配关键词高亮显示。
     */
    @GetMapping
    @Operation(summary = "全文搜索书签和文件夹")
    public Result<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId) {
        SearchResponse result = searchService.search(userId, keyword, page, size);
        return Result.success(result);
    }

    /**
     * 搜索建议（Autocomplete）
     *
     * 请求示例：GET /api/search/suggest?keyword=git
     * 返回最多 10 条匹配建议，用于搜索框输入时的下拉提示。
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索建议（Autocomplete）")
    public Result<List<SearchResponse.SearchItem>> suggest(
            @RequestParam String keyword,
            @AuthenticationPrincipal Long userId) {
        List<SearchResponse.SearchItem> suggestions = searchService.suggest(userId, keyword);
        return Result.success(suggestions);
    }

    /**
     * 全量重建搜索索引
     *
     * 将当前用户的 MySQL 数据全量同步到 ES。
     * 用于首次启用搜索功能时导入已有数据。
     */
    @PostMapping("/reindex")
    @Operation(summary = "全量重建搜索索引")
    public Result<Integer> reindex(@AuthenticationPrincipal Long userId) {
        int count = searchService.reindex(userId);
        return Result.success(count);
    }
}
