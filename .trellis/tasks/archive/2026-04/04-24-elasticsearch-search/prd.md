# 集成 Elasticsearch 实现全文搜索

## Goal

在 HLAIANavigationBar 导航栏项目中集成 Elasticsearch，为用户实现书签（title、url、description）和文件夹（name）的全文搜索功能，提升大量书签场景下的查找效率。

## What I already know

* **Bookmark 可搜索字段**: title, url, description
* **Folder 可搜索字段**: name
* **数据隔离**: 所有查询按 userId 隔离，搜索也必须按用户过滤
* **现有技术栈**: Spring Boot 4.0.5, Java 25, MyBatis-Plus 3.5.15, MySQL 8, Redis 7, Kafka
* **无现有搜索**: 后端无任何搜索 endpoint，前端无搜索组件
* **部署**: Docker app-network，飞牛 NAS (192.168.8.6)，前端端口 13566
* **前端**: Vue 3 + Element Plus + Pinia，i18n 已有 search 词条

## Assumptions (temporary)

* Elasticsearch 将作为新的 Docker 容器加入已有的 app-network
* 使用 Spring Data Elasticsearch 与 Spring Boot 4.x 集成
* 搜索结果需要分页
* 需要数据同步机制（MySQL → ES）

## Open Questions

* (无)

## Requirements

* 用户可按关键词搜索自己收藏的书签和文件夹
* 搜索范围: 书签 title/url/description, 文件夹 name
* 搜索结果按 userId 隔离
* 前端搜索入口在导航栏中
* 搜索结果分页展示
* **搜索建议**: 用户输入时实时返回匹配提示（autocomplete）
* **搜索结果高亮**: 匹配的关键词在结果中高亮显示

## Acceptance Criteria

* [ ] 输入关键词可返回匹配的书签和文件夹
* [ ] 搜索结果仅包含当前用户的书签
* [ ] 搜索响应时间 < 200ms
* [ ] 输入时实时显示搜索建议（下拉匹配项）
* [ ] 搜索结果中关键词高亮显示
* [ ] 前端搜索 UI 可用
* [ ] ES 容器可通过 docker-compose 启动
* [ ] 增删改书签/文件夹时 ES 索引自动同步

## Definition of Done

* 后端搜索 API 完成 + 单元测试
* 前端搜索 UI 完成
* Docker 部署配置更新
* 中文学习注释
* CI / 构建通过

## Out of Scope (explicit)

* 搜索历史记录
* 拼音搜索
* 全量数据重建索引（可后续添加）
* 暂存区（StagingItem）搜索
* 管理员搜索（跨用户）
* 中文分词插件（ik_max_word，后续可加）
* 书签批量导入时的 ES 同步（后续可加）

## Decision (ADR-lite)

**搜索范围**: 增强搜索（关键词搜索 + 搜索建议 + 结果高亮）
**数据同步**: Kafka 异步同步 — 企业级事件驱动模式，项目已有 Kafka 基础设施和成熟的 Producer/Consumer 模式
**开发分工**: 后端由用户自行实践（提供详细教程指引），前端由 AI 完成

## Research References

* [`research/spring-data-es-compat.md`](research/spring-data-es-compat.md) — Spring Boot 4.0.5 管理 Spring Data ES 6.0.4，需 ES 9.x 服务器

## Technical Approach

### 依赖与版本（官方验证 2026-04-24）
* 依赖: `spring-boot-starter-data-elasticsearch`（Boot 4.0.5 BOM 管理，无需指定版本）
* Spring Data ES: 6.0.5（当前稳定版，Release Train 2025.1）
* ES 服务器: Elasticsearch 9.2.8（Docker 容器，加入 app-network）
* Java 客户端: elasticsearch-java 9.2.8（Rest5Client，Spring Data ES 6.0 新客户端）

### 数据同步
* 复用现有 `KafkaProducer`，新增同步 topic（如 `bookmark-es-sync`、`folder-es-sync`）
* 新增 `SearchSyncConsumer`，监听 topic 后通过 `ElasticsearchOperations` 更新索引
* 增删改书签/文件夹时，Service 层发 Kafka 消息 → Consumer 消费 → 更新 ES

### 搜索 API
* `GET /api/search?keyword=xxx&page=1&size=20` — 全文搜索（书签+文件夹混合结果）
* `GET /api/search/suggest?keyword=xxx` — 搜索建议（autocomplete）
* 搜索结果高亮通过 ES Highlight API 实现

### 前端
* 导航栏添加搜索输入框
* 搜索结果页面展示匹配书签和文件夹
* 输入时下拉显示搜索建议

## Technical Notes

* Spring Data ES 6.0 使用 Rest5Client（替代旧 RestClient），需注意 API 差异
* ES 9.x Docker 建议分配 1-2GB 堆内存
* 项目已有 Kafka consumer 模式: `IconFetchConsumer`、`StagingCleanupConsumer`、`OperationLogConsumer`
* Bookmark 实体: id, userId, folderId, title, url, description, iconUrl, sortOrder, createdAt, updatedAt
* Folder 实体: id, userId, parentId, name, sortOrder, icon, createdAt, updatedAt
