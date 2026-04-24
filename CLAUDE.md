# CLAUDE.md — HLAIANavigationBar 项目指南

## 项目概述

HLAIANavigationBar 是一个书签导航栏 Web 应用 + Chromium 浏览器扩展插件。用户可以管理书签文件夹树、收藏书签、使用暂存区快速保存网页，管理员可管理用户。

- **仓库**: HLAIANavigationBar
- **主分支**: main
- **部署目标**: 飞牛 NAS（192.168.8.6），Docker 容器，外部端口 13566

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 4.0.5, Java 25, MyBatis-Plus 3.5.15, MySQL 8, Redis 7, Kafka |
| 认证 | Spring Security + JWT (jjwt 0.12.6) |
| 数据库迁移 | Flyway |
| API 文档 | SpringDoc OpenAPI 3.0.1 (Swagger UI) |
| 前端 | Vue 3, Vite, Element Plus, Pinia, vue-draggable-plus |
| 扩展 | Chromium Manifest V3 |
| 部署 | Docker, 已有 app-network |

## 项目结构

```
src/main/java/com/hlaia/
├── common/          # 通用模块（Result, ErrorCode, BusinessException, GlobalExceptionHandler）
├── config/          # 配置类（MyBatisPlusConfig 等）
├── entity/          # 实体类（User, Folder, Bookmark, StagingItem, OperationLog）
├── mapper/          # MyBatis-Plus Mapper 接口
├── dto/             # 数据传输对象（request / response）
├── service/         # 业务逻辑层
├── controller/      # REST API 控制器
├── security/        # Spring Security + JWT 配置
└── aspect/          # AOP 切面（限流、操作日志）
```

## 基础设施

- MySQL / Redis / Kafka / Zookeeper 已部署在飞牛 NAS（192.168.8.6）
- 开发环境配置: `application-dev.yml`（指向 192.168.8.6）
- 生产环境配置: `application-prod.yml`（使用 Docker 容器名 mysql/redis/kafka）
- `docker-compose.yml` 仅包含应用自身容器，加入已有 `app-network`

## 设计文档

- 完整设计规格: `docs/superpowers/specs/2026-04-16-hlaia-navigation-bar-design.md`
- 实施计划: `docs/superpowers/plans/2026-04-16-hlaia-navigation-bar-plan.md`

## 开发约定

### 执行模式

- 开发中使用技能`/using-superpowers`
- **Subagent-Driven Development**: 主会话做编排调度，通过 Agent 工具分派子任务给 subagent 执行实际编码
- 用户在每个 Task 完成后会检查代码，确认后才继续下一个

### 代码风格
- 所有 Java 文件必须添加**中文学习注释**，面向 Java 后端初学者
- 注释应解释：注解的作用、设计模式、为什么这样写（WHY）而不仅是写了什么（WHAT）
- 不写多余的注释，但对初学者不明显的概念要解释
- 前端开发使用技能`/frontend-design`

### Spring Boot 4.x 注意事项

- 包名使用 `jakarta.*` 而非 `javax.*`
- `spring-boot-starter-aop` 已移除，使用 `spring-aspects` 代替
- MyBatis-Plus 3.5.15 分页插件需额外引入 `mybatis-plus-jsqlparser`

### 数据库
- 4 张主表: user, folder, bookmark, staging_item + 1 张日志表: operation_log
- folder 使用邻接表模型（parent_id）实现无限层级树形结构
- Flyway 管理迁移脚本，位于 `src/main/resources/db/migration/`
- 默认管理员通过 CommandLineRunner 在 Task 10 中程序化创建（不在 SQL 中硬编码）

### Git 规范
- 中文提交信息不强制，但鼓励使用清晰的英文 conventional commits
- 不要 force push
- 提交前确认用户意图
