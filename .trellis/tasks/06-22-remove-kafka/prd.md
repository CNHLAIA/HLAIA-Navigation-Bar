# 移除 Kafka 依赖，重构异步处理逻辑

## Goal

彻底移除项目中的 Kafka 依赖及其相关代码，将原本依赖 Kafka 的 4 处异步逻辑替换为更轻量、更适合单体应用规模的处理方式。目标是在不损失功能的前提下，降低部署复杂度、缩小故障面、减少代码膨胀。

## Background

当前项目（书签导航栏 Web 应用 + 浏览器扩展）以单体方式部署在飞牛 NAS 上，Producer 与 Consumer 运行在同一个 JVM 内。Kafka 在项目里承担 4 个职责：

1. **操作日志**（`OperationLogAspect` → `operation-log` topic → `OperationLogConsumer`）：消费者最终写入与业务相同的 MySQL，绕 Kafka 无收益。
2. **暂存区过期清理**（`StagingCleanupScheduler` → `staging-cleanup` topic → `StagingCleanupConsumer`）：Scheduler 已把过期记录 `selectList` 拉进内存，再逐条发 Kafka 让消费者 `deleteById`，纯属冗余跳转。
3. **favicon 异步抓取**（`BookmarkService` → `bookmark-icon-fetch` topic → `IconFetchConsumer`）：唯一真正需要异步（外部 HTTP 请求），但单机异步即可。
4. **MySQL → ES 增量同步**（`search-sync` topic → `SearchSyncConsumer`）：解耦合理，但单体应用可用同步双写替代。

引入 Kafka 的真实成本：多一个重量级中间件（Kafka + KRaft/Zookeeper）、多一份 YAML 配置、5 个胶水类、消费者组/offset/积压/死信的运维心智负担、Kafka 单点故障会同时打挂 4 个功能。对个人/小团队导航栏而言属过度设计。

## Requirements

### 功能需求（行为等价，不丢功能）

- **R1 操作日志**：Controller 操作仍被记录到 `operation_log` 表，且日志写入失败不得影响业务请求。改用 `@Async` 异步直接 `insert`。
- **R2 暂存区清理**：过期 `staging_item` 仍被定期清理。Scheduler 直接批量删除（`deleteBatchIds`），不再发消息。
- **R3 favicon 抓取**：创建书签后仍异步抓取网站图标并回填 `icon_url`。改用**虚拟线程**（`@Async` + 虚拟线程执行器），保留原有 SSRF 防护与多策略抓取逻辑。
- **R4 ES 搜索同步**：书签/文件夹的增删改后 ES 索引保持同步。改用 Service 内**同步双写**：成功提交 MySQL 后直接调 `elasticsearchOperations.save/delete`，ES 写入失败仅记日志、不回滚 MySQL（最终一致由现有 `reindexAll` / `/api/search/reindex` 兜底）。
- **R5 全文搜索功能不变**：`SearchService.search/suggest/reindex/reindexAll` 行为对外不变。

### 工程需求

- **R6 彻底移除 Kafka**：
  - 删除 `src/main/java/com/hlaia/kafka/` 整个包（`KafkaProducer` + 4 个 Consumer）。
  - 移除 `pom.xml` 中 `spring-boot-starter-kafka`、`spring-kafka-test`。
  - 移除 `application-dev.yml` / `application-prod.yml` 中的 `spring.kafka.*` 配置块。
  - 移除 `.env.example`、`README.md`、`AGENTS.md` 中 Kafka 相关内容（技术栈表、部署说明）。
  - 移除 Docker Compose 中的 Kafka 服务定义（若存在）。
- **R7 引入异步执行器**：新增一个基于虚拟线程的 `ThreadPoolTaskExecutor`（或 `TaskExecutor`），供操作日志与 favicon 抓取共用；在主类上加 `@EnableAsync`。
- **R8 测试可运行**：移除 Kafka 后，`mvn test` 仍通过（H2 + 关闭 ES 索引初始化的环境下）。

### 约束

- **C1 不改 API 契约**：Controller 的请求/响应格式、URL 不变。
- **C2 不改数据库 schema**：`operation_log`、`staging_item`、`bookmark`、`folder` 表结构不变，无需新增 Flyway 迁移。
- **C3 不改 ES 索引结构**：`BookmarkDocument` / `FolderDocument` 字段不变，`ElasticsearchConfig` / `ElasticsearchDataInitializer` 保留。
- **C4 注释风格延续现有约定**：解释 WHY（为什么同步双写、为什么虚拟线程），不写废话注释。

## Acceptance Criteria

- [ ] AC1 全仓 `grep -ri kafka` 仅在 `.trellis/tasks/06-22-remove-kafka/` 任务目录内有命中，源码与配置中无残留。
- [ ] AC2 `pom.xml` 不再包含 `spring-boot-starter-kafka` / `spring-kafka-test`。
- [ ] AC3 `application-*.yml` 不再包含 `spring.kafka` 段。
- [ ] AC4 操作日志：调用任意 Controller 业务接口后，`operation_log` 表新增一条记录；Kafka 不可用的前提已不存在，但日志写入失败仍被 `catch` 吞掉、不影响业务。
- [ ] AC5 暂存区清理：构造一条 `expire_at <= now()` 的 `staging_item`，等待 Scheduler 触发后该记录被直接删除，无消息中间件参与。
- [ ] AC6 favicon：创建一条外网 URL 的书签（如 `https://www.baidu.com`），响应立即返回；一段时间后 `bookmark.icon_url` 被异步回填（或保持 null，由前端 Google Favicon 服务兜底，与现状一致）。
- [ ] AC7 ES 同步：创建/更新/删除书签后，`SearchService.search` 能立即查到对应变更（同步双写应比原 Kafka 方案更"实时"）。
- [ ] AC8 `mvn -q -DskipTests=false test` 通过。
- [ ] AC9 `mvn -q compile` 通过，无 import 残留导致的编译错误。
- [ ] AC10 README / AGENTS.md / .env.example 中 Kafka 相关说明已同步更新或移除。

## Out of Scope

- 不评估/移除 Redis（Redis 在限流、Token 缓存中有明确用途，不在本次范围）。
- 不评估/移除 Elasticsearch（已决策保留）。
- 不重构 `SearchService` 的查询逻辑本身，仅替换数据同步通道。
- 不引入新中间件（如 RabbitMQ / 本地事件表）作为 Kafka 替代——这正是要避免的复杂度。
