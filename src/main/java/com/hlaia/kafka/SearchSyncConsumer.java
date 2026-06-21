package com.hlaia.kafka;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import com.hlaia.entity.Bookmark;
import com.hlaia.entity.Folder;
import com.hlaia.mapper.BookmarkMapper;
import com.hlaia.mapper.FolderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 【Kafka 消费者 —— MySQL 到 Elasticsearch 的数据同步】
 *
 * 核心职责：监听 "search-sync" topic，收到消息后根据操作类型更新 ES 索引。
 * 这就是"Kafka 异步同步"模式的消费者端。
 *
 * 为什么用 Kafka 而不是直接在 Service 中写 ES？
 *   1. 解耦：BookmarkService/FolderService 不需要知道 ES 的存在
 *   2. 可靠：如果 ES 暂时不可用，消息不会丢失，消费者恢复后继续处理
 *   3. 异步：写 MySQL 后立即返回，ES 同步在后台完成，不影响接口响应速度
 *
 * ElasticsearchOperations 是什么？
 *   Spring Data ES 提供的核心接口，用于对 ES 进行 CRUD 操作。
 *   类似于 MyBatis-Plus 的 BaseMapper，但操作的是 ES 而不是 MySQL。
 *   常用方法：save()（创建/更新）、delete()（删除）、search()（搜索）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final BookmarkMapper bookmarkMapper;
    private final FolderMapper folderMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final JsonMapper jsonMapper;

    /**
     * 消费搜索同步消息
     *
     * 消息格式：{"action":"CREATE","type":"bookmark","id":123}
     *
     * 处理逻辑：
     *   CREATE/UPDATE → 从 MySQL 查询最新数据 → 转为 Document → 写入 ES
     *   DELETE → 从 ES 中删除对应文档
     *
     * 为什么 CREATE 和 UPDATE 都是"查 MySQL 再写 ES"？
     *   因为 ES 的 save 方法是 upsert 语义（存在则更新，不存在则创建），
     *   所以无论新增还是更新，统一走"查 MySQL → 写 ES"，保证数据一致。
     */
    @KafkaListener(topics = "search-sync", groupId = "hlaia-nav")
    public void consume(String message) {
        try {
            JsonNode node = jsonMapper.readTree(message);
            String action = node.get("action").asText();
            String type = node.get("type").asText();
            Long id = node.get("id").asLong();

            switch (type) {
                case "bookmark":
                    syncBookmark(action, id);
                    break;
                case "folder":
                    syncFolder(action, id);
                    break;
                default:
                    log.warn("Unknown sync type: {}", type);
            }
        } catch (Exception e) {
            // 同步失败只记录日志，不抛异常（避免 Kafka 无限重试）
            // 生产环境应该把失败消息发到死信队列（DLT），人工处理
            log.error("Failed to process search sync: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步书签到 ES
     *
     * 核心思路：始终以 MySQL 为准（single source of truth）
     *   CREATE/UPDATE → 查 MySQL → 转 Document → save 到 ES
     *   DELETE → 直接从 ES 删除
     */
    private void syncBookmark(String action, Long id) {
        if ("DELETE".equals(action)) {
            elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
            log.info("Deleted bookmark {} from ES", id);
        } else {
            // CREATE 或 UPDATE
            Bookmark bookmark = bookmarkMapper.selectById(id);
            if (bookmark != null) {
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

                // save() 是 upsert：文档存在则更新，不存在则创建
                elasticsearchOperations.save(doc);
                log.info("Synced bookmark {} to ES (action={})", id, action);
            } else {
                // MySQL 中已经查不到了，从 ES 中也删掉
                elasticsearchOperations.delete(id.toString(), BookmarkDocument.class);
                log.info("Bookmark {} not found in MySQL, removed from ES", id);
            }
        }
    }

    /**
     * 同步文件夹到 ES（逻辑和 syncBookmark 一样，只是数据类型不同）
     */
    private void syncFolder(String action, Long id) {
        if ("DELETE".equals(action)) {
            elasticsearchOperations.delete(id.toString(), FolderDocument.class);
            log.info("Deleted folder {} from ES", id);
        } else {
            Folder folder = folderMapper.selectById(id);
            if (folder != null) {
                FolderDocument doc = new FolderDocument();
                doc.setId(folder.getId());
                doc.setUserId(folder.getUserId());
                doc.setParentId(folder.getParentId());
                doc.setName(folder.getName());
                doc.setIcon(folder.getIcon());
                doc.setCreatedAt(folder.getCreatedAt());
                doc.setUpdatedAt(folder.getUpdatedAt());

                elasticsearchOperations.save(doc);
                log.info("Synced folder {} to ES (action={})", id, action);
            } else {
                elasticsearchOperations.delete(id.toString(), FolderDocument.class);
                log.info("Folder {} not found in MySQL, removed from ES", id);
            }
        }
    }
}
