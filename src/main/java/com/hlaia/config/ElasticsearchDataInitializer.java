package com.hlaia.config;

import com.hlaia.document.BookmarkDocument;
import com.hlaia.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 【ES 数据初始化器】—— 应用启动时自动检测并导入搜索数据
 *
 * ApplicationRunner 是 Spring Boot 提供的启动钩子：
 *   应用启动完成后自动执行 run() 方法。
 *   类似于 @PostConstruct，但 ApplicationRunner 在所有 Bean 初始化完成后才执行，
 *   而 @PostConstruct 只在当前 Bean 初始化完成后执行。
 *
 * 自动导入策略：
 *   检查 ES 索引是否为空（没有任何文档），如果为空则从 MySQL 全量导入。
 *   这样首次部署或索引重建后会自动填充数据，无需手动调用 reindex。
 *
 * 为什么不每次启动都全量导入？
 *   1. 数据量大时全量导入很慢（影响启动时间）
 *   2. 正常重启不应丢失 ES 数据（ES 数据持久化在磁盘上）
 *   3. 增量同步（Kafka）已经在处理日常数据变更
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchDataInitializer implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchService searchService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 检查 bookmark 索引是否为空
            SearchHits<BookmarkDocument> bookmarkCount = elasticsearchOperations.search(
                    Query.findAll(), BookmarkDocument.class);

            if (bookmarkCount.getTotalHits() == 0) {
                log.info("ES 索引为空，开始自动导入搜索数据...");
                // 这里不能按用户导入（还没有请求上下文），需要导入所有用户的数据
                searchService.reindexAll();
                log.info("自动导入完成");
            } else {
                log.info("ES 索引已有 {} 条书签数据，跳过自动导入", bookmarkCount.getTotalHits());
            }
        } catch (Exception e) {
            log.error("自动导入搜索数据失败: {}", e.getMessage(), e);
        }
    }
}
