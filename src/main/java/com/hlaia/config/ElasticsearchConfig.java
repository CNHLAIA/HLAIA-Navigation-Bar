package com.hlaia.config;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.document.FolderDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

/**
 * 【Elasticsearch 配置类】—— 管理搜索相关的配置
 *
 * Elasticsearch 在 Spring Boot 4.x 中的自动配置：
 *   Spring Boot 的 spring-boot-starter-data-elasticsearch 提供了自动配置，
 *   会自动创建以下 Bean：
 *   - ElasticsearchOperations：用于编程式查询（我们主要用这个）
 *   - ElasticsearchRestTemplate：ElasticsearchOperations 的实现类
 *   - ElasticsearchClient：底层 ES Java 客户端（Rest5Client）
 *
 *   你不需要手动创建这些 Bean，Spring Boot 会根据 application.yml 中的配置自动完成。
 *
 * Spring Data ES 6.0 的重大变化（相比 5.x）：
 *   - 使用新的 Rest5Client 替代旧的 RestClient
 *   - Rest5Client 是 ES 9.x 引入的新客户端，性能更好，API 更简洁
 *
 * @PostConstruct 的作用：
 *   在 Spring 完成依赖注入后自动执行的方法。相当于"应用启动后的初始化钩子"。
 *   这里用来确保 ES 索引在应用启动时就创建好，避免搜索时报 "Index not found"。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 应用启动时自动创建 ES 索引（仅在索引不存在时）
     *
     * 为什么不每次都删除重建？
     *   删除索引会清除所有文档数据，导致每次重启后数据丢失。
     *   正常的重启不应影响已有数据（ES 数据持久化在磁盘上）。
     *   只在索引不存在时创建（首次部署或索引被手动删除后）。
     *
     * 如果 @Field 注解发生了变化怎么办？
     *   需要手动删除索引再重启应用，或者调用 /api/search/reindex 接口。
     *   这属于管理操作，不应在每次启动时自动执行。
     */
    @PostConstruct
    public void createIndices() {
        // IndexOperations 是 ES 索引管理工具，类似 MySQL 的 DDL 操作
        IndexOperations bookmarkOps = elasticsearchOperations.indexOps(BookmarkDocument.class);
        if (!bookmarkOps.exists()) {
            bookmarkOps.createWithMapping();
            log.info("ES 索引已创建: bookmark");
        }

        IndexOperations folderOps = elasticsearchOperations.indexOps(FolderDocument.class);
        if (!folderOps.exists()) {
            folderOps.createWithMapping();
            log.info("ES 索引已创建: folder");
        }
    }
}
