package com.hlaia.service;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

/**
 * 【搜索同步服务】—— 把 MySQL 数据同步到 Elasticsearch（写侧）
 *
 * 取代原 Kafka 链路（Service → KafkaProducer → search-sync Topic → SearchSyncConsumer）。
 * 现在：Service 发 SearchSyncEvent → 事务提交后 SearchSyncEventListener 调本服务 → 写 ES。
 *
 * 为什么不再经过 Kafka？
 *   单体应用中"事务后置事件 + 直接调 ES"比 MQ 链路更简单可靠，少了一个重量级中间件。
 *
 * 为什么本服务不做异常捕获？
 *   ES 写入的兜底统一放在 SearchSyncEventListener 内（事务已提交，不能再回滚 MySQL，
 *   只能 log.warn + 靠 reindex 兜底）。本服务保持职责单一，让异常向上冒泡到监听器。
 *
 * MySQL 是 single source of truth：
 *   所有 upsert 操作都是"查 MySQL 最新值 → 写 ES"，保证 ES 与 MySQL 完全一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncService {

    private final BookmarkMapper bookmarkMapper;
    private final FolderMapper folderMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 同步书签到 ES（CREATE/UPDATE 场景）
     *
     * 查 MySQL 最新值 → 转 BookmarkDocument → save（upsert 语义）。
     * 若 MySQL 中已查不到（并发删除），则从 ES 中也删掉，保证一致。
     */
    public void upsertBookmark(Long id) {
        Bookmark bookmark = bookmarkMapper.selectById(id);
        if (bookmark != null) {
            BookmarkDocument doc = toBookmarkDocument(bookmark);
            // save() 是 upsert：文档存在则更新，不存在则创建
            elasticsearchOperations.save(doc);
            log.info("Synced bookmark {} to ES", id);
        } else {
            // MySQL 中已经查不到了，从 ES 中也删掉
            elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
            log.info("Bookmark {} not found in MySQL, removed from ES", id);
        }
    }

    /** 删除场景：直接从 ES 中删除 */
    public void deleteBookmark(Long id) {
        elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
        log.info("Deleted bookmark {} from ES", id);
    }

    /** 同步文件夹到 ES（逻辑同 upsertBookmark） */
    public void upsertFolder(Long id) {
        Folder folder = folderMapper.selectById(id);
        if (folder != null) {
            FolderDocument doc = toFolderDocument(folder);
            elasticsearchOperations.save(doc);
            log.info("Synced folder {} to ES", id);
        } else {
            elasticsearchOperations.delete(id.toString(), FolderDocument.class);
            log.info("Folder {} not found in MySQL, removed from ES", id);
        }
    }

    /** 删除场景：直接从 ES 中删除 */
    public void deleteFolder(Long id) {
        elasticsearchOperations.delete(id.toString(), FolderDocument.class);
        log.info("Deleted folder {} from ES", id);
    }

    private BookmarkDocument toBookmarkDocument(Bookmark bookmark) {
        BookmarkDocument doc = new BookmarkDocument();
        doc.setId(bookmark.getId());
        doc.setUserId(bookmark.getUserId());
        doc.setFolderId(bookmark.getFolderId());
        doc.setTitle(bookmark.getTitle());
        doc.setUrl(bookmark.getUrl());
        doc.setDescription(bookmark.getDescription());
        doc.setIconUrl(bookmark.getIconUrl());
        doc.setCreatedAt(bookmark.getCreatedAt());
        doc.setUpdatedAt(bookmark.getUpdatedAt());
        return doc;
    }

    private FolderDocument toFolderDocument(Folder folder) {
        FolderDocument doc = new FolderDocument();
        doc.setId(folder.getId());
        doc.setUserId(folder.getUserId());
        doc.setParentId(folder.getParentId());
        doc.setName(folder.getName());
        doc.setIcon(folder.getIcon());
        doc.setCreatedAt(folder.getCreatedAt());
        doc.setUpdatedAt(folder.getUpdatedAt());
        return doc;
    }
}
