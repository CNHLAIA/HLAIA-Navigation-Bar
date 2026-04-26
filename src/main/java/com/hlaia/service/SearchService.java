package com.hlaia.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import com.hlaia.dto.response.SearchResponse;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 【搜索业务逻辑层】—— 处理全文搜索的核心逻辑
 *
 * ElasticsearchOperations 的两种查询方式：
 *   1. NativeQuery（本类使用）：类似 SQL，用 Java 对象构建 ES 查询，灵活但需要了解 ES 语法
 *   2. CriteriaQuery：更高级的抽象，类似 MyBatis-Plus 的 QueryWrapper，但功能较少
 *
 * 搜索策略：分别搜索书签索引和文件夹索引，合并结果返回。
 *   为什么不合并成一个索引？因为书签和文件夹的字段不同，分开索引更清晰。
 *
 * ES 9.x Java 客户端 (co.elastic.clients) 的类型要求：
 *   - FieldValue：ES 中所有字段值的包装类型，类似 Java 的 Optional。
 *     term 查询的 value() 方法不接受原始 long/String，必须用 FieldValue 包装。
 *     例：value(4L) ❌  →  value(FieldValue.of(fv -> fv.longValue(4L))) ✅
 *   - List<String>：fields() 方法只接受 List，不接受可变参数。
 *     例：fields("a","b") ❌  →  fields(List.of("a","b")) ✅
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final BookmarkMapper bookmarkMapper;
    private final FolderMapper folderMapper;

    /**
     * 全文搜索 —— 搜索书签和文件夹
     *
     * NativeQuery 中的 bool 查询：
     *   must：必须匹配（类似 SQL 的 AND），参与相关性评分
     *   filter：过滤条件（精确匹配），不参与评分，ES 会缓存结果提升性能
     *   multiMatch：在多个字段中搜索关键词
     *
     * 为什么 userId 用 filter 而不是 must？
     *   userId 是固定值（当前用户），不是搜索关键词，不应影响相关性排序。
     *   filter 不参与评分且会被缓存，查询更快。
     */
    public SearchResponse search(Long userId, String keyword, int page, int size) {
        List<SearchResponse.SearchItem> allItems = new ArrayList<>();
        long totalBookmarks = 0;
        long totalFolders = 0;

        // ============ 搜索书签 ============
        NativeQuery bookmarkQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> bool
                        .must(m -> m.multiMatch(mm -> mm
                                .query(keyword)
                                .fields(List.of("title", "url", "description"))))
                        .filter(f -> f.term(t -> t.field("userId")
                                .value(FieldValue.of(fv -> fv.longValue(userId)))))))
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        SearchHits<BookmarkDocument> bookmarkHits = elasticsearchOperations.search(bookmarkQuery, BookmarkDocument.class);
        totalBookmarks = bookmarkHits.getTotalHits();

        for (SearchHit<BookmarkDocument> hit : bookmarkHits.getSearchHits()) {
            BookmarkDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("bookmark");
            item.setId(doc.getId());
            item.setTitle(doc.getTitle());
            item.setUrl(doc.getUrl());
            item.setDescription(doc.getDescription());
            item.setIcon(doc.getIconUrl());
            item.setFolderId(doc.getFolderId());
            allItems.add(item);
        }

        // ============ 搜索文件夹 ============
        NativeQuery folderQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> bool
                        .must(m -> m.multiMatch(mm -> mm.query(keyword).fields(List.of("name"))))
                        .filter(f -> f.term(t -> t.field("userId")
                                .value(FieldValue.of(fv -> fv.longValue(userId)))))))
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        SearchHits<FolderDocument> folderHits = elasticsearchOperations.search(folderQuery, FolderDocument.class);
        totalFolders = folderHits.getTotalHits();

        for (SearchHit<FolderDocument> hit : folderHits.getSearchHits()) {
            FolderDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("folder");
            item.setId(doc.getId());
            item.setTitle(doc.getName());
            item.setIcon(doc.getIcon());
            allItems.add(item);
        }

        // ============ 构建响应 ============
        SearchResponse response = new SearchResponse();
        response.setItems(allItems);
        response.setTotal(totalBookmarks + totalFolders);
        response.setPage(page);
        response.setSize(size);
        return response;
    }

    /**
     * 搜索建议（Autocomplete）
     *
     * 和全文搜索的区别：用户正在输入时实时返回匹配的标题，显示在下拉列表中。
     * 只返回少量结果（最多 10 条），不需要高亮。
     */
    public List<SearchResponse.SearchItem> suggest(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        List<SearchResponse.SearchItem> suggestions = new ArrayList<>();

        // 搜索书签标题和 URL
        NativeQuery bookmarkQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> bool
                        .must(m -> m.multiMatch(mm -> mm.query(keyword).fields(List.of("title", "url"))))
                        .filter(f -> f.term(t -> t.field("userId")
                                .value(FieldValue.of(fv -> fv.longValue(userId)))))))
                .withMaxResults(5)
                .build();

        SearchHits<BookmarkDocument> bookmarkHits = elasticsearchOperations.search(bookmarkQuery, BookmarkDocument.class);
        for (SearchHit<BookmarkDocument> hit : bookmarkHits.getSearchHits()) {
            BookmarkDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("bookmark");
            item.setId(doc.getId());
            item.setTitle(doc.getTitle());
            item.setUrl(doc.getUrl());
            item.setIcon(doc.getIconUrl());
            item.setFolderId(doc.getFolderId());
            suggestions.add(item);
        }

        // 搜索文件夹名称
        NativeQuery folderQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(bool -> bool
                        .must(m -> m.multiMatch(mm -> mm.query(keyword).fields(List.of("name"))))
                        .filter(f -> f.term(t -> t.field("userId")
                                .value(FieldValue.of(fv -> fv.longValue(userId)))))))
                .withMaxResults(5)
                .build();

        SearchHits<FolderDocument> folderHits = elasticsearchOperations.search(folderQuery, FolderDocument.class);
        for (SearchHit<FolderDocument> hit : folderHits.getSearchHits()) {
            FolderDocument doc = hit.getContent();
            SearchResponse.SearchItem item = new SearchResponse.SearchItem();
            item.setType("folder");
            item.setId(doc.getId());
            item.setTitle(doc.getName());
            item.setIcon(doc.getIcon());
            suggestions.add(item);
        }

        return suggestions;
    }

    /**
     * 全量重建索引 —— 将 MySQL 中指定用户的所有书签和文件夹同步到 ES
     *
     * 使用场景：
     *   1. 首次集成 ES 时，已有大量数据需要导入
     *   2. ES 数据与 MySQL 不一致时，手动触发全量同步修复
     *
     * 为什么不直接在 SearchSyncConsumer 里全量同步？
     *   SearchSyncConsumer 处理的是增量同步（每次只同步一条变更），
     *   全量同步是一次性操作，放在这里更合理。
     */
    public int reindex(Long userId) {
        int count = 0;

        // 同步书签
        List<Bookmark> bookmarks = bookmarkMapper.selectList(
                new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getUserId, userId));
        if (!bookmarks.isEmpty()) {
            List<BookmarkDocument> docs = bookmarks.stream().map(b -> {
                BookmarkDocument doc = new BookmarkDocument();
                doc.setId(b.getId());
                doc.setUserId(b.getUserId());
                doc.setFolderId(b.getFolderId());
                doc.setTitle(b.getTitle());
                doc.setUrl(b.getUrl());
                doc.setDescription(b.getDescription());
                doc.setIconUrl(b.getIconUrl());
                doc.setCreatedAt(b.getCreatedAt());
                doc.setUpdatedAt(b.getUpdatedAt());
                return doc;
            }).toList();
            elasticsearchOperations.save(docs);
            count += docs.size();
        }

        // 同步文件夹
        List<Folder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>().eq(Folder::getUserId, userId));
        if (!folders.isEmpty()) {
            List<FolderDocument> docs = folders.stream().map(f -> {
                FolderDocument doc = new FolderDocument();
                doc.setId(f.getId());
                doc.setUserId(f.getUserId());
                doc.setParentId(f.getParentId());
                doc.setName(f.getName());
                doc.setIcon(f.getIcon());
                doc.setCreatedAt(f.getCreatedAt());
                doc.setUpdatedAt(f.getUpdatedAt());
                return doc;
            }).toList();
            elasticsearchOperations.save(docs);
            count += docs.size();
        }

        log.info("全量重建索引完成，用户ID: {}，同步 {} 条数据", userId, count);
        return count;
    }

    /**
     * 全量重建索引 —— 导入所有用户的数据
     *
     * 和 reindex(Long userId) 的区别：
     *   reindex(userId) 只导入指定用户的数据，用于手动触发（需要登录态）。
     *   reindexAll() 导入所有用户的数据，用于应用启动时自动导入（没有用户上下文）。
     *
     * 为什么需要两个方法？
     *   应用启动时（ApplicationRunner）还没有用户登录，无法获取 userId，
     *   所以需要一个不带 userId 参数的方法来导入所有数据。
     */
    public int reindexAll() {
        int count = 0;

        // 同步所有书签 —— selectList(null) 表示不加任何条件，查询全部
        List<Bookmark> bookmarks = bookmarkMapper.selectList(null);
        if (!bookmarks.isEmpty()) {
            List<BookmarkDocument> docs = bookmarks.stream().map(b -> {
                BookmarkDocument doc = new BookmarkDocument();
                doc.setId(b.getId());
                doc.setUserId(b.getUserId());
                doc.setFolderId(b.getFolderId());
                doc.setTitle(b.getTitle());
                doc.setUrl(b.getUrl());
                doc.setDescription(b.getDescription());
                doc.setIconUrl(b.getIconUrl());
                doc.setCreatedAt(b.getCreatedAt());
                doc.setUpdatedAt(b.getUpdatedAt());
                return doc;
            }).toList();
            elasticsearchOperations.save(docs);
            count += docs.size();
        }

        // 同步所有文件夹
        List<Folder> folders = folderMapper.selectList(null);
        if (!folders.isEmpty()) {
            List<FolderDocument> docs = folders.stream().map(f -> {
                FolderDocument doc = new FolderDocument();
                doc.setId(f.getId());
                doc.setUserId(f.getUserId());
                doc.setParentId(f.getParentId());
                doc.setName(f.getName());
                doc.setIcon(f.getIcon());
                doc.setCreatedAt(f.getCreatedAt());
                doc.setUpdatedAt(f.getUpdatedAt());
                return doc;
            }).toList();
            elasticsearchOperations.save(docs);
            count += docs.size();
        }

        log.info("全量重建索引完成，同步 {} 条数据", count);
        return count;
    }
}
