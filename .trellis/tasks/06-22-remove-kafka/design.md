# Design — 移除 Kafka，重构异步处理

> 配套 `prd.md`。本文聚焦技术决策、数据流、改动清单、兼容性与回滚形态。

## 1. 总体策略

把 4 条 Kafka 链路按"是否真的需要异步"分类替换：

| 原 Kafka 用途 | 新方案 | 异步? | 理由 |
|---|---|---|---|
| 操作日志写入 | `@Async` 直接 `insert` | 是（虚拟线程） | 不阻塞请求；日志失败不影响业务 |
| 暂存区清理 | Scheduler 内 `deleteBatchIds` | 否（定时任务本身就在后台） | 数据已在内存，绕 MQ 多此一举 |
| favicon 抓取 | `@Async` 虚拟线程 | 是 | 外部 HTTP 耗时数秒，必须异步 |
| ES 增量同步 | Service 内同步双写 | 否 | 单体内同步调用最简单可靠；ES 失败不回滚 MySQL |

核心收益：去掉一个重量级中间件 → 部署更简、故障面更小、代码更少，且 4 个功能的行为对外等价。

## 2. 新增组件：异步执行器配置

**新增** `src/main/java/com/hlaia/config/AsyncConfig.java`：

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 基于 Java 25 虚拟线程的异步执行器。
     *
     * 为什么用虚拟线程而不是平台线程池？
     *   1. favicon 抓取是 I/O 密集型（外部 HTTP），虚拟线程在 I/O 等待时让出载体线程，吞吐高
     *   2. 无需手动调参 corePoolSize/maxPoolSize/queueCapacity，避免队列满导致的拒绝策略问题
     *   3. Java 25 虚拟线程已稳定，是 Spring Boot 4 推荐写法
     *
     * 共用一个执行器：操作日志（轻量、高频）和 favicon 抓取（重量、低频）都走虚拟线程，
     * 虚拟线程之间互不阻塞，不需要按任务类型隔离执行器。
     */
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

> 在主类上**不再需要** `@EnableAsync`（已移到 `AsyncConfig`）。若主类原本有 `@EnableScheduling` 保留不动。

**为何不直接给每个 `@Async` 方法配独立执行器**：虚拟线程本身就是"每任务一线程"，操作日志和 favicon 任务特征差异不会互相拖累，共用执行器配置最简。

## 3. 各链路改造详述

### 3.1 操作日志（`OperationLogAspect` + 新 `OperationLogService`）

**问题**：当前 Aspect 直接依赖 `KafkaProducer`。移除 Kafka 后 Aspect 需要一个落库入口。

**改造**：
- 新增 `service/OperationLogService.java`，方法 `@Async void record(Long userId, String action, String target)`，内部 `operationLogMapper.insert(...)`，整个方法体 `try/catch` 吞异常 + `log.warn`。
- `OperationLogAspect` 把字段 `KafkaProducer kafkaProducer` 换成 `OperationLogService operationLogService`，调用改为 `operationLogService.record(userId, action, target)`。
- **注意 `@Async` 自调用失效陷阱**：`@Async` 必须从外部代理调用才生效。Aspect → Service 是跨 Bean 调用，天然走代理，无此问题。

**删除**：`kafka/OperationLogConsumer.java`、`KafkaProducer.sendOperationLog`。

### 3.2 暂存区清理（`StagingCleanupScheduler`）

**改造**（最小改动）：
- 移除 `KafkaProducer kafkaProducer` 字段。
- 把循环里 `kafkaProducer.sendStagingCleanup(item.getId(), item.getUserId())` 改为收集 ID 列表，循环结束后一次 `stagingItemMapper.deleteBatchIds(idList)`。
- 仅在 `!expired.isEmpty()` 时执行删除与日志（保持原日志节流逻辑）。

**为什么用 `deleteBatchIds` 而不是循环 `deleteById`**：单条 SQL `DELETE ... WHERE id IN (...)`，减少 N 次数据库往返。

**删除**：`kafka/StagingCleanupConsumer.java`、`KafkaProducer.sendStagingCleanup`。

### 3.3 favicon 抓取（`IconFetchConsumer` → `IconFetchService`）

**改造**：
- 把 `kafka/IconFetchConsumer.java` 重构为 `service/IconFetchService.java`。
  - 类上加 `@Service`。
  - 原 `consume(String message)`（解析 JSON → 抓取 → 回填）改为 `@Async void fetchAndSaveIcon(Long bookmarkId, String url)`，直接接收强类型参数（不再需要 JSON 拼接/解析）。
  - **保留全部** `fetchFavicon` / `fetchFromHtml` / `checkUrl` / `isSafeUrl` 逻辑与 SSRF 防护不变。
  - 保留静态 `HTTP_CLIENT`。
- `BookmarkService.createBookmark` 把 `kafkaProducer.sendIconFetchTask(bookmark.getId(), bookmark.getUrl())` 改为 `iconFetchService.fetchAndSaveIcon(bookmark.getId(), bookmark.getUrl())`。
- `BookmarkService` 移除 `KafkaProducer` 依赖，新增 `IconFetchService` 依赖。

**删除**：`KafkaProducer.sendIconFetchTask`、原 JSON 拼接代码。

### 3.4 ES 同步双写（核心改动，影响面最大）

**改造思路**：把 `SearchSyncConsumer` 里 `syncBookmark` / `syncFolder` 的"按 ID 查 MySQL → 写 ES"逻辑，提升为一个可被 Service 同步调用的组件。

**新增** `service/SearchSyncService.java`（或直接并入 `SearchService`，二选一，见下方决策）：

```java
@Service
@RequiredArgsConstructor
public class SearchSyncService {
    private final BookmarkMapper bookmarkMapper;
    private final FolderMapper folderMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    /** 书签增改后调用：查 MySQL 最新值 → upsert 到 ES。删除场景调 deleteBookmark。 */
    public void upsertBookmark(Long id) { ... }
    public void deleteBookmark(Long id) { ... }
    public void upsertFolder(Long id) { ... }
    public void deleteFolder(Long id) { ... }
}
```

**调用点改造**（把原来发 Kafka 的地方改成同步调用）：

| 文件 | 原调用 | 新调用 |
|---|---|---|
| `BookmarkService.createBookmark` | `sendSearchSync("CREATE","bookmark",id)` | `searchSyncService.upsertBookmark(id)` |
| `BookmarkService.updateBookmark` | `sendSearchSync("UPDATE",...)` | `searchSyncService.upsertBookmark(id)` |
| `BookmarkService.deleteBookmark` | `sendSearchSync("DELETE",...)` | `searchSyncService.deleteBookmark(id)` |
| `BookmarkService` 批量排序/移动文件夹内 | `sendSearchSync(...)` 多处 | 对应 `upsertBookmark` |
| `FolderService.create/update/delete/move` | `sendSearchSync("...", "folder", id)` | `upsertFolder` / `deleteFolder` |

**ES 写入失败的容错策略**（重要）：
- `SearchSyncService` 每个方法内部 `try/catch` Exception，失败仅 `log.warn`，**不向上抛**。
- 这样保证：MySQL 已提交的事务不会因为 ES 故障而回滚——业务正常，搜索暂时不一致。
- 最终一致性兜底：`ElasticsearchDataInitializer`（启动时索引空则全量导入）+ `/api/search/reindex` 手动重建。

**事务边界 —— 采用 `@TransactionalEventListener(AFTER_COMMIT)` 方案**：

这是本任务的关键技术决策。直接在 `@Transactional` 方法内调 ES 会导致"MySQL 事务回滚但 ES 已写入脏数据"的不一致窗口。采用 Spring 官方的事务事件机制彻底消除该窗口。

机制：
1. Service 在事务内 `applicationEventPublisher.publishEvent(new SearchSyncEvent(type, id))`
2. Spring 暂存事件，**不立即处理**
3. 事务成功提交后 → 触发 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 监听器
4. 监听器调用 `SearchSyncService.upsert/delete` 写 ES
5. 若事务回滚 → 事件**被丢弃**，ES 不写入，天然一致

新增组件：
- `event/SearchSyncEvent.java`：普通 POJO（继承 `ApplicationEvent` 或用普通类 + `@EventListener`，本项目用普通 record/类即可，Spring 4.x 支持任意事件类型），字段 `String type` ("bookmark"/"folder")、`String action` ("CREATE"/"UPDATE"/"DELETE")、`Long id`。
- `event/SearchSyncEventListener.java`：
  ```java
  @Component
  @RequiredArgsConstructor
  public class SearchSyncEventListener {
      private final SearchSyncService searchSyncService;

      @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
      public void onSearchSync(SearchSyncEvent event) {
          // 监听器内仍需 try/catch：此时 MySQL 已提交，ES 失败不能再回滚
          try {
              switch (event.getType()) {
                  case "bookmark" -> handleBookmark(event);
                  case "folder"  -> handleFolder(event);
              }
          } catch (Exception e) {
              log.warn("ES sync failed after commit ({} {}): {}", event.getType(), event.getId(), e.getMessage());
          }
      }
  }
  ```

调用点改法（以 createBookmark 为例）：
```java
// 原: kafkaProducer.sendSearchSync("CREATE", "bookmark", bookmark.getId());
eventPublisher.publishEvent(new SearchSyncEvent("bookmark", "CREATE", bookmark.getId()));
```

两个必须满足的前提（implement 阶段逐一核验）：
1. **所有调用点必须在 `@Transactional` 方法内**——否则 `@TransactionalEventListener` 默认会丢弃事件。已知 `BookmarkService.createBookmark/updateBookmark/deleteBookmark` 标了 `@Transactional`；`FolderService` 各方法需核验，未标的需补上（这是数据一致性要求，本就应补）。
2. **`SearchSyncService` 内部不再重复 try/catch**（监听器已统一兜底），保持职责单一。

> 注：若未来出现"非事务方法也想同步 ES"的场景，可在监听器注解上加 `fallbackExecution = true`，但本任务不需要——所有调用点都在事务内。

**关于 SearchSyncService 是否并入 SearchService**：
- 决策：**独立成 `SearchSyncService`**。理由：`SearchService` 职责是"读"（查询/重建），`SearchSyncService` 职责是"写"（增量同步），分开后 BookmarkService/FolderService 只依赖写侧，依赖更清晰。

**删除**：`kafka/SearchSyncConsumer.java`、`KafkaProducer.sendSearchSync`。

## 4. 完整删除清单

### 源码（整体删除）
- `src/main/java/com/hlaia/kafka/KafkaProducer.java`
- `src/main/java/com/hlaia/kafka/OperationLogConsumer.java`
- `src/main/java/com/hlaia/kafka/SearchSyncConsumer.java`
- `src/main/java/com/hlaia/kafka/StagingCleanupConsumer.java`
- `src/main/java/com/hlaia/kafka/IconFetchConsumer.java`
- 整个 `kafka/` 包随之消失

### 源码（改造，非删除）
- `aspect/OperationLogAspect.java`：换依赖
- `scheduled/StagingCleanupScheduler.java`：换批量删除
- `service/BookmarkService.java`：6 处调用点
- `service/FolderService.java`：4 处调用点

### 新增源码
- `config/AsyncConfig.java`（虚拟线程执行器 + `@EnableAsync`）
- `service/OperationLogService.java`（`@Async` 落库）
- `service/IconFetchService.java`（从 Consumer 重构而来）
- `service/SearchSyncService.java`（从 Consumer 重构而来）
- `event/SearchSyncEvent.java`（事务事件载体）
- `event/SearchSyncEventListener.java`（`@TransactionalEventListener(AFTER_COMMIT)`，调用 SearchSyncService）

### 构建与配置
- `pom.xml`：删除 `spring-boot-starter-kafka`、`spring-kafka-test`
- `application-dev.yml`：删除 `spring.kafka` 段
- `application-prod.yml`：删除 `spring.kafka` 段
- `.env.example`：删除 `KAFKA_*` 变量
- 若存在 `docker-compose*.yml` 中的 kafka 服务：删除

### 文档
- `README.md`：技术栈/架构/部署章节移除 Kafka 描述，新增"异步处理（虚拟线程 + 同步双写）"说明
- `AGENTS.md`：技术栈表把 Kafka 删除
- `.trellis/spec/backend/logging-guidelines.md`：当前以 `KafkaProducer.sendOperationLog` 为示例，需改写为 `OperationLogService.record`（属 spec 更新，Phase 3.3 处理）

## 5. 不变项（明确边界）

- `entity/OperationLog`、`OperationLogMapper` 不变
- `entity/StagingItem`、`StagingItemMapper` 不变
- `entity/Bookmark` / `Folder`、对应 Mapper 不变
- `document/BookmarkDocument` / `FolderDocument` 不变
- `config/ElasticsearchConfig`、`config/ElasticsearchDataInitializer` 不变
- `service/SearchService` 的 `search/suggest/reindex/reindexAll` 逻辑不变
- 所有 Controller、DTO 不变
- 数据库 schema 不变，**不需要**新增 Flyway 迁移脚本

## 6. 兼容性与风险

| 风险 | 评估 | 缓解 |
|---|---|---|
| ES 写入失败导致搜索不一致 | 低（已用事件机制隔离）。事务已提交但 ES 写失败 | 监听器内 `try/catch` + `log.warn`；`reindexAll` 启动兜底；`/api/search/reindex` 手动修复 |
| 调用点不在事务内导致事件被丢弃 | 中。`@TransactionalEventListener` 默认要求事务上下文 | implement 阶段逐一核验 10 个调用点都有 `@Transactional`，缺失的补上 |
| `@Async` 自调用失效 | 已规避（Aspect→Service、BookmarkService→IconFetchService 均跨 Bean） | 代码审查确认 |
| 虚拟线程 `synchronized` 阻蔽（Java 25 已大幅缓解） | 低。`IconFetchService` 内部无重型 `synchronized` 块 | 不需要特别处理 |
| 删除 Consumer 后被遗漏的 import | 低 | AC1 grep + AC9 编译验证 |
| 测试中 Kafka 类被引用 | 中。需检查 `src/test/` 是否有 Kafka 相关测试 | 实施时 `grep` 检查；若无专门 Kafka 测试则无需改动 |

## 7. 回滚形态

- 本任务所有改动都在一个分支内。若上线后发现问题，`git revert` 整个合并提交即可回到 Kafka 版本。
- Kafka 已部署的实例在回滚期间仍可运行；本任务不涉及 Kafka 集群本身的销毁操作（由部署侧自行决定何时下线 Kafka 容器）。

## 8. 验证策略

- **编译**：`mvn -q compile` 无 Kafka import 残留。
- **测试**：`mvn -q test`（H2 + `app.elasticsearch.init-indices=false`，沿用现有测试 profile）。
- **静态检查**：`grep -ri kafka src/ pom.xml application*.yml .env.example README.md AGENTS.md` 仅任务目录命中。
- **功能手测**（部署后或本地起 MySQL/Redis/ES）：
  1. 登录后调任意书签接口 → 查 `operation_log` 表有新记录
  2. 插入过期 `staging_item` → 等 1 分钟被 Scheduler 清掉
  3. 新建外网书签 → 几秒后 `icon_url` 回填
  4. 新建/改/删书签 → `GET /api/search?keyword=...` 立即可见
