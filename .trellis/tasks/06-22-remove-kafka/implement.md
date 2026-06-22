# Implement — 移除 Kafka 执行计划

> 配套 `prd.md` + `design.md`。按顺序执行，每个步骤后跑该步骤的验证命令。

## 执行原则

- **小步、可编译**：每完成一个逻辑组就让项目能编译通过，避免大量改动堆积后无法定位错误。
- **先新增、后切换、最后删除**：先建好替代组件 → 把调用点逐个切过去 → 最后删 Kafka 包。这样任何中断点都能编译。
- **不擅自扩大范围**：只动 design.md 第 4 节列出的文件。spec 更新留到 Phase 3.3。

## 验证命令

| 用途 | 命令 |
|---|---|
| 编译 | `mvn -q compile` |
| 测试 | `mvn -q test` |
| Kafka 残留扫描 | `grep -ri kafka src pom.xml src/main/resources/application-*.yml .env.example` |

> Windows/cmd 下用 `findstr /s /i kafka ...` 替代 grep，或借助 Grep 工具。

## 步骤清单

### 阶段 A：搭建替代骨架（不破坏现状）

- [ ] **A1** 新增 `config/AsyncConfig.java`
  - `@Configuration` + `@EnableAsync`
  - 定义 `AsyncTaskExecutor` Bean，内部 `Executors.newVirtualThreadPerTaskExecutor()` + `TaskExecutorAdapter` 包装
  - 注释说明"为何用虚拟线程"（I/O 密集、免调参、Java 25 推荐）
  - 验证：`mvn -q compile`

- [ ] **A2** 新增 `service/OperationLogService.java`
  - `@Service`，依赖 `OperationLogMapper`
  - 方法 `@Async void record(Long userId, String action, String target)`
  - 内部 `try/catch`，失败 `log.warn`，不抛
  - 字段映射沿用原 `OperationLogConsumer.consume`（含 `has()` 判空、`LocalDateTime.now()`）
  - 验证：`mvn -q compile`

- [ ] **A3** 新增 `service/IconFetchService.java`（从 `IconFetchConsumer` 重构）
  - 把 `IconFetchConsumer` 的全部方法复制过来：`fetchFavicon` / `fetchFromHtml` / `checkUrl` / `isSafeUrl`、静态 `HTTP_CLIENT`
  - 删除 JSON 解析相关 import 和字段 `JsonMapper`
  - 把 `@KafkaListener consume(String message)` 改为 `@Async void fetchAndSaveIcon(Long bookmarkId, String url)`
  - 删除原方法体内的 `jsonMapper.readTree` / `node.get(...)`，直接用入参
  - 注解从 `@Component` 换成 `@Service`，保留 `@Slf4j` `@RequiredArgsConstructor`（移除 `jsonMapper` 字段）
  - 验证：`mvn -q compile`（此时 Consumer 还在，新旧并存，能编译）

- [ ] **A4** 新增 `service/SearchSyncService.java`
  - 从 `SearchSyncConsumer` 提取 `syncBookmark` / `syncFolder` 的核心逻辑
  - 改为公开方法：`upsertBookmark(Long)` / `deleteBookmark(Long)` / `upsertFolder(Long)` / `deleteFolder(Long)`
  - **不在方法内 try/catch**（由监听器统一兜底，见 A5）
  - 保留"查 MySQL → 转 Document → save"与"删除"分支，保留"MySQL 查不到则从 ES 删除"的兜底
  - 验证：`mvn -q compile`

- [ ] **A5** 新增 `event/SearchSyncEvent.java` + `event/SearchSyncEventListener.java`
  - Event：普通类，字段 `String type` / `String action` / `Long id`，加构造器
  - Listener：`@Component`，依赖 `SearchSyncService` + `@Slf4j`
    - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 接收 `SearchSyncEvent`
    - 内部 `try/catch`，按 `type` 路由到 `searchSyncService` 对应方法
    - 失败 `log.warn("ES sync failed after commit ({} {}): {}", ...)`
  - 验证：`mvn -q compile`

> **阶段 A 检查点**：此时所有新组件就位，旧 Kafka 代码未动，项目应能正常编译运行（行为仍是旧 Kafka 链路）。

### 阶段 B：切换调用点（一次切换一处，可中断）

- [ ] **B1** `BookmarkService` 切换
  - 替换依赖：移除 `KafkaProducer` 字段，新增 `IconFetchService iconFetchService`、`ApplicationEventPublisher eventPublisher`
  - 移除 `import com.hlaia.kafka.KafkaProducer`
  - **10 处调用点**（依据 design.md 第 3.4 节表）：
    - favicon：`sendIconFetchTask(id, url)` → `iconFetchService.fetchAndSaveIcon(id, url)`
    - ES 同步：`sendSearchSync(action, "bookmark", id)` → `eventPublisher.publishEvent(new SearchSyncEvent("bookmark", action, id))`
  - 核验所有调用点所在方法都有 `@Transactional`（如有缺失补上，并在 commit message 注明）
  - 验证：`mvn -q compile`

- [ ] **B2** `FolderService` 切换
  - 同 B1，依赖替换 + 移除 KafkaProducer import
  - 4 处 `sendSearchSync(..., "folder", ...)` → `publishEvent(new SearchSyncEvent("folder", ...))`
  - 核验 `@Transactional`（`createFolder` / `updateFolder` / `deleteFolder` / 移动方法）
  - 验证：`mvn -q compile`

- [ ] **B3** `OperationLogAspect` 切换
  - 依赖：`KafkaProducer` → `OperationLogService`
  - 调用：`kafkaProducer.sendOperationLog(userId, action, target)` → `operationLogService.record(userId, action, target)`
  - 移除 Kafka import，调整注释（"通过 @Async 异步落库"替换"通过 Kafka 异步发送"）
  - 验证：`mvn -q compile`

- [ ] **B4** `StagingCleanupScheduler` 切换
  - 移除 `KafkaProducer` 字段及 import
  - 改逻辑：收集 `expired` 的 id 列表 → 循环结束后 `stagingItemMapper.deleteBatchIds(idList)`
  - 调整注释（"直接批量删除"替换"通过 Kafka 发送清理消息"）
  - 验证：`mvn -q compile`

> **阶段 B 检查点**：所有业务调用点都已切换，`KafkaProducer` 此刻应该**没有任何调用方**。验证：`grep -rn "kafkaProducer\." src/main/java/` 应无命中。

### 阶段 C：删除 Kafka 代码与依赖

- [ ] **C1** 删除整个 `src/main/java/com/hlaia/kafka/` 包（5 个文件）
  - 删除前确认上一检查点通过（无调用方）
  - 验证：`mvn -q compile`

- [ ] **C2** `pom.xml` 移除两个依赖
  - `spring-boot-starter-kafka`
  - `spring-kafka-test`（在 test scope）
  - 验证：`mvn -q compile`

- [ ] **C3** 配置文件清理
  - `application-dev.yml`：删除 `spring.kafka` 整段
  - `application-prod.yml`：删除 `spring.kafka` 整段
  - `.env.example`：删除所有 `KAFKA_*` 变量及注释
  - 验证：文件可读，YAML 缩进正确

- [ ] **C4** 检查 Docker/部署文件
  - 查找仓库根的 `docker-compose*.yml`、`Dockerfile`
  - 若含 kafka 服务定义 → 删除（compose 文件中相关 service 段）
  - 若无则跳过

### 阶段 D：测试与静态检查

- [ ] **D1** 扫描测试代码
  - `grep -ri "kafka\|KafkaTemplate\|@KafkaListener" src/test/`
  - 若有专门 Kafka 测试 → 删除或改造（极可能没有，原项目未发现 KafkaTest 类）
  - 验证：无残留引用

- [ ] **D2** 全量 Kafka 残留扫描
  - `grep -ri kafka src pom.xml src/main/resources/application-*.yml .env.example`
  - 预期：仅 `.trellis/tasks/06-22-remove-kafka/` 命中（任务文档自身）
  - 验证：满足 AC1

- [ ] **D3** 编译 + 测试
  - `mvn -q compile`
  - `mvn -q test`
  - 验证：两者均通过（AC8、AC9）

### 阶段 E：文档同步

- [ ] **E1** `README.md`
  - 技术栈表删除 Kafka 行
  - 架构/部署章节移除 Kafka 描述
  - 新增简短说明："异步处理采用虚拟线程（@Async）；MySQL→ES 同步采用事务提交后事件双写"
  - 若有 docker-compose 启动命令含 kafka，同步移除

- [ ] **E2** `AGENTS.md`
  - 技术栈表删除 Kafka
  - 项目结构注释（若提及 `kafka/` 包）相应更新

- [ ] **E3** spec 更新留到 Phase 3.3（`trellis-update-spec`），不在本提交内处理

## 回滚点

| 阶段 | 中断时状态 | 回滚方式 |
|---|---|---|
| A 完成 | 新旧并存，行为不变 | 删除新增文件即可 |
| B 进行中 | 部分调用点切换 | `git checkout` 对应 Service 文件 |
| B 完成 | 调用点全切，Kafka 代码悬空 | 删除新增文件 + 还原调用点 |
| C 完成 | Kafka 已移除 | `git revert` 此批次提交 |

## Review Gates

- **Gate 1（阶段 A 后）**：编译通过 + 新组件独立可测
- **Gate 2（阶段 B 后）**：`grep kafkaProducer` 无调用方命中
- **Gate 3（阶段 D 后）**：AC1/AC8/AC9 全绿
- **Gate 4（提交前最终 2.2 检查）**：跨层一致性——确认 `event/`、`service/`、`config/` 三个包的改动彼此一致，无孤儿引用
