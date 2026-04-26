# HLAIANavigationBar

书签导航栏 Web 应用 + Chromium 浏览器扩展插件。提供书签文件夹树管理、网页收藏、暂存区快速保存等功能，支持普通用户和管理员两种角色。

## 功能特性

- 书签管理：支持无限层级的文件夹树结构，书签可拖拽排序
- 暂存区：快速保存网页链接，稍后再归类到文件夹
- 浏览器扩展：右键菜单一键收藏当前页面或链接，支持直接选择目标文件夹
- 用户认证：Spring Security + JWT，支持 Access Token / Refresh Token
- 管理员功能：用户管理（查看、禁用）
- 操作日志：AOP 切面记录关键操作
- API 文档：SpringDoc OpenAPI 自动生成，Swagger UI 可视化
- 国际化：前端支持 vue-i18n 多语言

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.0.5, Java 25 |
| ORM | MyBatis-Plus 3.5.15 (mybatis-plus-spring-boot4-starter) |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 (Spring Data Redis) |
| 消息队列 | Kafka (spring-boot-starter-kafka) |
| 搜索引擎 | Elasticsearch (Spring Data Elasticsearch) |
| 认证 | Spring Security + JWT (jjwt 0.12.6) |
| 数据库迁移 | Flyway |
| API 文档 | SpringDoc OpenAPI 3.0.1 |
| HTML 解析 | Jsoup 1.18.3 (提取网页 favicon) |
| 前端 | Vue 3, Vite, Element Plus, Pinia, vue-draggable-plus, vue-i18n |
| 浏览器扩展 | Chromium Manifest V3 |
| 部署 | Docker Compose, Nginx 反向代理 |

## 项目结构

```
HLAIANavigationBar/
├── src/main/java/com/hlaia/
│   ├── common/              # 通用模块（Result, ErrorCode, BusinessException, GlobalExceptionHandler）
│   ├── config/              # 配置类（MyBatisPlusConfig, OpenApiConfig 等）
│   ├── entity/              # 实体类（User, Folder, Bookmark, StagingItem, OperationLog）
│   ├── mapper/              # MyBatis-Plus Mapper 接口
│   ├── dto/                 # 数据传输对象（request / response）
│   ├── service/             # 业务逻辑层
│   ├── controller/          # REST API 控制器
│   ├── security/            # Spring Security + JWT 配置
│   └── aspect/              # AOP 切面（限流、操作日志）
├── src/main/resources/
│   ├── db/migration/        # Flyway 迁移脚本（V1~V4）
│   ├── application-dev.yml  # 开发环境配置（指向 192.168.8.6）
│   └── application-prod.yml # 生产环境配置（Docker 容器名）
├── frontend/                # Vue 3 前端项目
│   ├── src/                 # 前端源码
│   ├── Dockerfile           # 前端多阶段构建 + Nginx
│   ├── nginx.conf           # Nginx 配置（静态文件 + API 反向代理）
│   └── package.json         # 前端依赖
├── extension/               # Chromium 浏览器扩展（Manifest V3）
│   ├── background.js        # Service Worker（右键菜单、API 调用）
│   ├── options/             # 扩展选项页（登录/登出、配置服务器地址）
│   ├── manifest.json        # 扩展清单
│   └── icons/               # 扩展图标
├── docker-compose.yml       # Docker Compose 编排
├── Dockerfile               # 后端多阶段构建
└── pom.xml                  # Maven 依赖配置
```

## 数据库设计

共 5 张表：

| 表名 | 说明 |
|------|------|
| `user` | 用户表，支持 USER / ADMIN 角色 |
| `folder` | 文件夹表，邻接表模型（parent_id）实现无限层级树形结构 |
| `bookmark` | 书签表，关联用户和文件夹 |
| `staging_item` | 暂存区表，带过期时间 |
| `operation_log` | 操作日志表 |

数据库迁移由 Flyway 管理，脚本位于 `src/main/resources/db/migration/`。

## 本地开发

### 环境要求

- JDK 25
- Node.js 20+
- MySQL 8（或连接远程实例）
- Redis 7
- Kafka（可选，部分功能依赖）

### 后端

1. 确保 MySQL 和 Redis 已启动，并在 MySQL 中创建数据库：

```sql
CREATE DATABASE hlaia_nav_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改 `src/main/resources/application-dev.yml` 中的数据库、Redis、Kafka 连接地址（默认指向 192.168.8.6）。

3. 启动后端服务：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

后端默认运行在 `http://localhost:8080`。首次启动时 Flyway 会自动执行迁移脚本创建表结构。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，需要配置代理或将 API 请求指向后端地址。

### 构建前端

```bash
cd frontend
npm run build
```

产物输出到 `frontend/dist/` 目录。

## 部署指南

### 前提条件

- 目标服务器已部署 MySQL、Redis、Kafka，且处于同一 Docker 网络（app-network）
- 已安装 Docker 和 Docker Compose

### 部署步骤

1. 克隆仓库到目标服务器。

2. 修改 `src/main/resources/application-prod.yml` 中的数据库密码、JWT 密钥等敏感配置（或通过环境变量注入）。

3. 构建并启动服务：

```bash
docker compose up -d --build
```

Docker Compose 会启动两个容器：

| 容器 | 说明 | 端口 |
|------|------|------|
| `hlaia-nav` | Spring Boot 后端 | 8080（仅内部网络） |
| `hlaia-nav-frontend` | Nginx 前端 + 反向代理 | 13566 对外 |

前端 Nginx 配置：
- 静态文件：`/` -> Vue SPA（history 模式）
- API 代理：`/api` -> `http://hlaia-nav:8080`

部署完成后访问 `http://<服务器IP>:13566` 即可使用。

### Dockerfile 说明

后端和前端均采用多阶段构建：

- **后端 Dockerfile**：Stage 1 使用 `eclipse-temurin:17-jdk-alpine` 编译打包，Stage 2 使用 `eclipse-temurin:17-jre-alpine` 运行
- **前端 Dockerfile**：Stage 1 使用 `node:20-alpine` 执行 `npm run build`，Stage 2 使用 `nginx:alpine` 提供静态文件服务

## 浏览器扩展

### 安装

1. 打开 Chromium 浏览器（Chrome / Edge 等），进入扩展管理页面：
   - Chrome：`chrome://extensions/`
   - Edge：`edge://extensions/`

2. 开启「开发者模式」。

3. 点击「加载已解压的扩展程序」，选择项目的 `extension/` 目录。

4. 扩展安装完成，浏览器工具栏会出现 HLAIA Navigation Bar 图标。

### 配置

1. 右键点击扩展图标，选择「选项」进入设置页。

2. 填写服务器地址，例如：
   - 本地开发：`http://localhost:8080`
   - NAS 部署：`http://192.168.8.6:13566`

3. 输入用户名和密码登录。

### 使用

- 在任意网页上右键，菜单中会出现「收藏到 HLAIA 导航栏」选项。
- 展开后可选择：
  - **保存到暂存区**：快速保存，稍后在 Web 端整理归类
  - **选择文件夹**：直接保存到指定的文件夹（支持多级子菜单）
- 在链接上右键也可直接收藏该链接。
- 文件夹菜单每 5 分钟自动刷新，也可在选项页登录后立即刷新。
- Token 过期时会弹出通知，引导重新登录。

### 扩展权限说明

| 权限 | 用途 |
|------|------|
| `contextMenus` | 注册右键上下文菜单 |
| `storage` | 存储 Token 和服务器配置 |
| `activeTab` | 获取当前标签页标题和 URL |
| `notifications` | 操作结果通知 |
| `host_permissions` | 允许向配置的服务器地址发送请求 |

## API 文档

后端启动后，访问 Swagger UI 查看完整的 API 文档：

- 开发环境：`http://localhost:8080/swagger-ui.html`
- 生产环境：`http://<服务器IP>:13566/swagger-ui.html`

### 主要 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 JWT Token |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/ext/folders/tree` | 获取文件夹树（扩展专用） |
| POST | `/api/ext/bookmarks` | 保存书签到指定文件夹（扩展专用） |
| POST | `/api/ext/staging` | 保存到暂存区（扩展专用） |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- `code === 200` 表示成功
- `code !== 200` 表示业务错误
- HTTP 401 表示 Token 过期或无效

## 配置说明

### 环境变量（生产环境）

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `JWT_SECRET` | JWT 签名密钥 | 内置默认值（生产环境务必修改） |
| `JWT_ACCESS_EXPIRATION` | Access Token 有效期（毫秒） | 86400000（24 小时） |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 有效期（毫秒） | 604800000（7 天） |

### Spring Profiles

| Profile | 用途 |
|---------|------|
| `dev` | 开发环境，连接 192.168.8.6 的各服务，开启 SQL 日志 |
| `prod` | 生产环境，使用 Docker 容器名连接各服务 |
