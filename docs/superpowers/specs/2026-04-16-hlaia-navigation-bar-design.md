# HLAIANavigationBar 系统设计文档

## 1. 项目概述

**HLAIANavigationBar** 是一个现代化的网页导航管理系统，类似浏览器书签（收藏夹），支持无限层级文件夹、标签页卡片管理、拖拽排序、暂存区等功能。项目包含一个独立的 Web 应用和一个 Chromium 浏览器扩展插件。

### 产品形态

- **Web 应用（主体）**：独立网站，用户在此管理文件夹和标签页
- **浏览器扩展（辅助）**：右键菜单"收藏到导航栏"，支持存到文件夹或暂存区

### 暂存区

暂存区与文件夹独立，特点：
- 无子文件夹结构
- 标签块默认有效期 1 天，过期自动删除
- 可单独设置每个标签块的有效时间

---

## 2. 技术栈

### 后端

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 17 | 开发语言 |
| Spring Boot | 4.0.5 | 应用框架 |
| Spring Security | (Boot 管理) | 认证与授权 |
| JWT (jjwt) | 最新 | Token 签发与验证 |
| MyBatis-Plus | 最新 | ORM 框架，简化 CRUD |
| MySQL | 8.x | 关系型数据库 |
| Redis | 7.x | 缓存（JWT 黑名单、暂存区缓存、接口限流） |
| Kafka | 最新 | 消息队列（异步图标抓取、过期清理、操作日志） |
| Flyway | (Boot 管理) | 数据库版本迁移 |
| Knife4j | 最新 | API 文档生成（Swagger 增强） |
| Spring Cache | (Boot 管理) | 声明式缓存抽象，配合 Redis |
| AOP | (Boot 管理) | 自定义注解限流、操作日志 |
| Spring Validation | (Boot 管理) | 请求参数校验 |
| Docker | - | 容器化部署 |

### 前端

| 技术 | 用途 |
|---|---|
| Vue 3 | 前端框架 |
| Vite | 构建工具 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| vue-draggable-plus | 拖拽排序 |
| Axios | HTTP 请求 |

### 浏览器扩展

| 技术 | 用途 |
|---|---|
| Manifest V3 | Chrome 最新扩展标准 |
| Service Worker | 后台脚本（右键菜单注册） |

---

## 3. 系统架构

```
┌─────────────────────────────────────────────────────┐
│                     用户浏览器                        │
│  ┌──────────────┐           ┌───────────────────┐   │
│  │  Vue 3 Web   │           │ Chrome Extension  │   │
│  │  Application │           │ (Manifest V3)     │   │
│  │              │           │                   │   │
│  │  - 左右分栏   │           │ - 右键菜单         │   │
│  │  - 文件夹树   │           │ - 收藏到文件夹     │   │
│  │  - 标签页卡片 │           │ - 保存到暂存区     │   │
│  └──────┬───────┘           └────────┬──────────┘   │
│         │  HTTP + JWT Token          │               │
└─────────┼────────────────────────────┼───────────────┘
          │                            │
          ▼                            ▼
┌─────────────────────────────────────────────────────┐
│                  Spring Boot 后端                     │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐ │
│  │Controller│→ │ Service  │→ │  MyBatis-Plus     │ │
│  │  (REST)  │  │ (业务逻辑)│  │  (Mapper/DAO)     │ │
│  └──────────┘  └────┬─────┘  └────────┬──────────┘ │
│                     │                 │              │
│  ┌──────────┐  ┌────▼─────┐  ┌───────▼──────────┐ │
│  │Security  │  │  Kafka   │  │     MySQL         │ │
│  │+ JWT     │  │ Producer │  │                   │ │
│  └──────────┘  └────┬─────┘  └──────────────────┘ │
│                     │                                │
│  ┌──────────┐  ┌────▼─────┐  ┌──────────────────┐ │
│  │AOP 限流   │  │ Kafka    │  │  Redis           │ │
│  │+ 注解     │  │ Consumer │  │  - JWT 黑名单     │ │
│  └──────────┘  └──────────┘  │  - 暂存区缓存     │ │
│                               │  - 限流计数器     │ │
│  ┌──────────────────────┐    └──────────────────┘ │
│  │ Knife4j (API 文档)    │                         │
│  └──────────────────────┘                         │
│  ┌──────────────────────┐                         │
│  │ Flyway (数据库迁移)   │                         │
│  └──────────────────────┘                         │
│  ┌──────────────────────┐                         │
│  │ 统一异常 + 参数校验   │                         │
│  └──────────────────────┘                         │
└─────────────────────────────────────────────────────┘
```

---

## 4. 数据库设计

### 4.1 用户表 (user)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | 密码（BCrypt 加密） |
| email | VARCHAR(100) | 邮箱（可选） |
| role | ENUM('USER','ADMIN') | 角色，默认 USER |
| status | TINYINT | 状态：0 正常，1 封禁 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 4.2 文件夹表 (folder)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 所属用户 |
| parent_id | BIGINT FK | 父文件夹 ID（NULL 为根目录） |
| name | VARCHAR(100) | 文件夹名称 |
| sort_order | INT | 排序序号（支持拖拽排序） |
| icon | VARCHAR(50) | 文件夹图标（可选） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**树形结构设计**：采用 `parent_id` 邻接表模式，通过递归查询或内存构建树。支持无限层级。

### 4.3 标签页表 (bookmark)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 所属用户 |
| folder_id | BIGINT FK | 所属文件夹 |
| title | VARCHAR(255) | 网页标题 |
| url | VARCHAR(2048) | 网页 URL |
| description | VARCHAR(500) | 描述（可选） |
| icon_url | VARCHAR(500) | 网站图标 URL |
| sort_order | INT | 排序序号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 4.4 暂存区表 (staging_item)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 所属用户 |
| title | VARCHAR(255) | 网页标题 |
| url | VARCHAR(2048) | 网页 URL |
| icon_url | VARCHAR(500) | 网站图标 URL |
| expire_at | DATETIME | 过期时间（默认创建时间 +1 天） |
| created_at | DATETIME | 创建时间 |

---

## 5. API 设计

### 5.1 认证模块

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录，返回 JWT |
| POST | `/api/auth/logout` | 登出，JWT 加入 Redis 黑名单 |
| POST | `/api/auth/refresh` | 刷新 Token |

### 5.2 文件夹模块

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/folders/tree` | 获取当前用户的完整文件夹树 |
| POST | `/api/folders` | 创建文件夹 |
| PUT | `/api/folders/{id}` | 更新文件夹（重命名、图标） |
| DELETE | `/api/folders/{id}` | 删除文件夹（级联删除子文件夹和标签页） |
| PUT | `/api/folders/sort` | 批量更新排序（拖拽排序后） |
| PUT | `/api/folders/{id}/move` | 移动文件夹到新父级（拖拽移动） |

### 5.3 标签页模块

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/folders/{folderId}/bookmarks` | 获取文件夹下的标签页列表 |
| POST | `/api/bookmarks` | 创建标签页 |
| PUT | `/api/bookmarks/{id}` | 更新标签页 |
| DELETE | `/api/bookmarks/{id}` | 删除标签页 |
| PUT | `/api/bookmarks/sort` | 批量更新排序 |
| POST | `/api/bookmarks/batch-delete` | 批量删除 |
| POST | `/api/bookmarks/batch-copy` | 批量复制链接 |

### 5.4 暂存区模块

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/staging` | 获取暂存区列表 |
| POST | `/api/staging` | 添加到暂存区 |
| PUT | `/api/staging/{id}` | 修改过期时间 |
| DELETE | `/api/staging/{id}` | 删除暂存项 |
| POST | `/api/staging/{id}/move-to-folder` | 从暂存区移到文件夹 |

### 5.5 管理员模块

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users` | 获取用户列表 |
| GET | `/api/admin/users/{userId}/folders/tree` | 查看用户导航栏 |
| DELETE | `/api/admin/folders/{id}` | 删除用户文件夹 |
| PUT | `/api/admin/users/{userId}/ban` | 封禁用户 |
| PUT | `/api/admin/users/{userId}/unban` | 解封用户 |

### 5.6 扩展专用

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ext/folders/tree` | 扩展获取文件夹树（用于右键菜单子菜单） |
| POST | `/api/ext/bookmarks` | 扩展快速添加标签页 |
| POST | `/api/ext/staging` | 扩展添加到暂存区 |

---

## 6. Kafka 消息设计

### Topic 1: bookmark-icon-fetch

- **生产者**：创建标签页时发送
- **消费者**：异步抓取网页 favicon/icon
- **消息体**：`{ "bookmarkId": 123, "url": "https://..." }`

### Topic 2: staging-cleanup

- **生产者**：定时任务（Spring Scheduled）扫描即将过期的暂存项
- **消费者**：执行删除并清理 Redis 缓存
- **消息体**：`{ "stagingItemId": 456, "userId": 1 }`

### Topic 3: operation-log

- **生产者**：AOP 切面记录所有写操作
- **消费者**：持久化操作日志（管理员可查）
- **消息体**：`{ "userId": 1, "action": "DELETE_FOLDER", "target": "folder:5", "timestamp": "..." }`

---

## 7. Redis 使用设计

| Key 模式 | 用途 | TTL |
|---|---|---|
| `jwt:blacklist:{token}` | JWT 登出黑名单 | 与 Token 剩余过期时间一致 |
| `user:staging:{userId}` | 暂存区列表缓存 | 30 分钟 |
| `ratelimit:{userId}:{api}` | 接口限流计数器 | 按限流窗口（如 1 分钟） |
| `user:info:{userId}` | 用户基本信息缓存 | 1 小时 |

---

## 8. UI 设计

### 布局：左右分栏（方案 A）

```
┌──────────────────────────────────────────────────┐
│  顶栏：Logo + 搜索框 + 用户头像/设置              │
├──────────┬───────────────────────────────────────┤
│          │                                       │
│  文件夹树  │  内容区                                │
│          │                                       │
│  📁 开发  │  ┌─ 子文件夹行 ─────────────────────┐ │
│    📁 Java │  │ 📁 Java学习  📁 前端工具  📁 数据库 │ │
│    📁 前端 │  └─────────────────────────────────┘ │
│  📁 设计  │                                       │
│  📁 工具  │  ┌─ 标签页卡片网格 ─────────────────┐ │
│          │  │ [卡片] [卡片] [卡片]               │ │
│  ──────  │  │ [卡片] [卡片] [卡片]               │ │
│  ⏳ 暂存区 │  └─────────────────────────────────┘ │
│   (3项)   │                                       │
│          │  底部：多选工具栏（批量删除/复制链接）     │
└──────────┴───────────────────────────────────────┘
```

### 配色方案

- 深色主题为主（#1a1a2e 背景，#16213e 面板）
- 主色调：紫色系（#6c5ce7 / #a29bfe）
- 标签页卡片背景：#1a1a2e，边框 #2a2a4a
- 暂存区：金色标识（#ffc107）

### 交互

- **拖拽排序**：文件夹树内拖拽调整层级和顺序；卡片区域拖拽调整顺序
- **多选**：按住 Ctrl/Shift 点击卡片，出现底部操作栏
- **批量操作**：批量删除、批量复制链接
- **右键菜单**：文件夹和标签页右键有编辑、删除、移动等选项

---

## 9. 浏览器扩展设计

### 功能

1. **右键菜单**：
   - 一级菜单："收藏到 HLAIA 导航栏"
   - 二级菜单：列出用户文件夹 + "保存到暂存区"
2. **认证**：用户在扩展选项页登录，获取 JWT Token 存储在 chrome.storage
3. **API 调用**：通过 background Service Worker 调用后端 API

### 权限需求

- `contextMenus`：右键菜单
- `storage`：存储 Token
- `activeTab`：获取当前标签页信息
- `host_permissions`：调用后端 API

---

## 10. V1 功能范围

### V1 包含

- 用户注册/登录（JWT）
- 文件夹 CRUD + 无限层级树形结构
- 文件夹拖拽排序 + 层级移动
- 标签页 CRUD + 拖拽排序
- 多选 + 批量删除 + 批量复制链接
- 暂存区（添加、查看、设置过期时间、移到文件夹、自动过期清理）
- 管理员：查看用户导航栏、删除用户文件夹、封禁/解封用户
- 浏览器扩展：右键收藏到文件夹/暂存区
- Redis 缓存 + 限流
- Kafka 异步处理
- Knife4j API 文档
- Docker 部署

### V1 不包含（后续版本）

- 标签页搜索
- 导入/导出书签
- 分享文件夹给其他用户
- 标签页快照/缩略图
- 多主题切换
- 移动端适配

---

## 11. 项目目录结构

```
HLAIANavigationBar/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/hlaia/
│   │   ├── HlaiaNavigationBarApplication.java
│   │   ├── config/                   # 配置类
│   │   │   ├── SecurityConfig.java   # Spring Security + JWT
│   │   │   ├── RedisConfig.java      # Redis 序列化配置
│   │   │   ├── KafkaConfig.java      # Kafka 配置
│   │   │   ├── CorsConfig.java       # 跨域配置
│   │   │   └── SwaggerConfig.java    # Knife4j 配置
│   │   ├── common/                   # 通用模块
│   │   │   ├── Result.java           # 统一响应体
│   │   │   ├── GlobalExceptionHandler.java  # 全局异常处理
│   │   │   └── ErrorCode.java        # 错误码枚举
│   │   ├── annotation/               # 自定义注解
│   │   │   └── RateLimit.java        # 限流注解
│   │   ├── aspect/                   # AOP 切面
│   │   │   ├── RateLimitAspect.java  # 限流切面
│   │   │   └── OperationLogAspect.java  # 操作日志切面
│   │   ├── security/                 # 安全模块
│   │   │   ├── JwtTokenProvider.java # JWT 签发/验证
│   │   │   ├── JwtAuthFilter.java    # JWT 过滤器
│   │   │   └── UserDetailsServiceImpl.java
│   │   ├── controller/               # 控制器
│   │   │   ├── AuthController.java
│   │   │   ├── FolderController.java
│   │   │   ├── BookmarkController.java
│   │   │   ├── StagingController.java
│   │   │   ├── AdminController.java
│   │   │   └── ExtensionController.java
│   │   ├── service/                  # 业务逻辑
│   │   │   ├── AuthService.java
│   │   │   ├── FolderService.java
│   │   │   ├── BookmarkService.java
│   │   │   ├── StagingService.java
│   │   │   └── AdminService.java
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   │   ├── UserMapper.java
│   │   │   ├── FolderMapper.java
│   │   │   ├── BookmarkMapper.java
│   │   │   └── StagingItemMapper.java
│   │   ├── entity/                   # 数据库实体
│   │   │   ├── User.java
│   │   │   ├── Folder.java
│   │   │   ├── Bookmark.java
│   │   │   └── StagingItem.java
│   │   ├── dto/                      # 数据传输对象
│   │   │   ├── request/              # 请求 DTO
│   │   │   └── response/             # 响应 DTO
│   │   └── kafka/                    # Kafka 消费者
│   │       ├── IconFetchConsumer.java
│   │       ├── StagingCleanupConsumer.java
│   │       └── OperationLogConsumer.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── db/migration/             # Flyway 迁移脚本
│   │       ├── V1__init_schema.sql
│   │       └── V2__init_admin.sql
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── App.vue
│   │   ├── main.js
│   │   ├── router/index.js
│   │   ├── stores/                   # Pinia stores
│   │   │   ├── auth.js
│   │   │   ├── folder.js
│   │   │   └── bookmark.js
│   │   ├── api/                      # API 调用封装
│   │   │   ├── request.js            # Axios 实例 + 拦截器
│   │   │   ├── auth.js
│   │   │   ├── folder.js
│   │   │   ├── bookmark.js
│   │   │   └── staging.js
│   │   ├── views/                    # 页面
│   │   │   ├── LoginView.vue
│   │   │   ├── RegisterView.vue
│   │   │   ├── MainView.vue          # 主页面（左右分栏）
│   │   │   ├── StagingView.vue       # 暂存区
│   │   │   └── admin/                # 管理员页面
│   │   │       ├── UserListView.vue
│   │   │       └── UserDetailView.vue
│   │   ├── components/               # 组件
│   │   │   ├── FolderTree.vue        # 文件夹树
│   │   │   ├── BookmarkGrid.vue      # 标签页卡片网格
│   │   │   ├── BookmarkCard.vue      # 单个标签页卡片
│   │   │   ├── StagingList.vue       # 暂存区列表
│   │   │   ├── BatchToolbar.vue      # 批量操作工具栏
│   │   │   └── FolderBreadcrumb.vue  # 面包屑导航
│   │   └── utils/
│   │       └── auth.js               # Token 管理
│   ├── Dockerfile
│   ├── package.json
│   └── vite.config.js
│
├── extension/                        # Chromium 浏览器扩展
│   ├── manifest.json
│   ├── background.js                 # Service Worker
│   ├── options/                      # 选项页（登录设置）
│   │   ├── options.html
│   │   └── options.js
│   └── icons/                        # 扩展图标
│
├── docker-compose.yml                # MySQL + Redis + Kafka 一键启动
└── docs/                             # 文档
```

---

## 12. 部署方案

使用 Docker Compose 一键部署：

- **mysql** 容器：MySQL 8.x，数据持久化到 Docker Volume
- **redis** 容器：Redis 7.x
- **kafka** 容器：Kafka（含 Zookeeper 或 KRaft 模式）
- **backend** 容器：Spring Boot 应用
- **frontend** 容器：Nginx 托管 Vue 静态文件 + 反向代理 API
