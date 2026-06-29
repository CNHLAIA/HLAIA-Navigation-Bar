# NavigationBar 项目指南

## 项目概述

NavigationBar 是一个书签导航栏 Web 应用 + Chromium 浏览器扩展插件。用户可以管理书签文件夹树、收藏书签、使用暂存区快速保存网页，管理员可管理用户。


## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 4.0.5, Java 25, MyBatis-Plus 3.5.15, MySQL 8, Redis 7 |
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

## 开发约定

### 代码风格
- 注释应解释：注解的作用、设计模式、为什么这样写（WHY）而不仅是写了什么（WHAT）
- 不写多余的注释
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

### 环境变量

- 本机 java25 环境变量详见 @AGENTS.local.md 。(如果存在)

<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
