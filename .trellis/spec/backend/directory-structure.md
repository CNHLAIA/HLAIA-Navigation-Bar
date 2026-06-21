# 目录结构规范

> 本项目后端代码的组织方式和文件命名约定。

---

## 概述

本项目是一个 Spring Boot 4.0.5 + MyBatis-Plus 3.5.15 的单体应用，采用经典的三层架构（Controller -> Service -> Mapper）。所有后端代码位于 `src/main/java/com/hlaia/` 包下，按功能职责分包。

---

## 目录布局

```
src/main/java/com/hlaia/
├── common/              # 通用模块（基础设施层）
│   ├── Result.java           # 统一响应包装类
│   ├── ErrorCode.java        # 错误码枚举
│   ├── BusinessException.java    # 自定义业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理器
├── config/              # 配置类（Spring Bean 配置）
│   ├── SecurityConfig.java    # Spring Security 安全配置
│   ├── MyBatisPlusConfig.java # MyBatis-Plus 分页插件配置
│   ├── CorsConfig.java        # 跨域配置
│   ├── RedisConfig.java       # Redis 配置
│   └── SwaggerConfig.java     # Swagger/OpenAPI 文档配置
├── entity/              # 实体类（对应数据库表）
│   ├── User.java
│   ├── Folder.java
│   ├── Bookmark.java
│   ├── StagingItem.java
│   └── OperationLog.java
├── mapper/              # MyBatis-Plus Mapper 接口（数据访问层 DAO）
│   ├── UserMapper.java
│   ├── FolderMapper.java
│   ├── BookmarkMapper.java
│   ├── StagingItemMapper.java
│   └── OperationLogMapper.java
├── dto/                 # 数据传输对象（Data Transfer Object）
│   ├── request/               # 请求 DTO（前端传入的数据）
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── BookmarkCreateRequest.java
│   │   ├── FolderCreateRequest.java
│   │   └── ...
│   └── response/              # 响应 DTO（返回给前端的数据）
│       ├── AuthResponse.java
│       ├── BookmarkResponse.java
│       ├── FolderTreeResponse.java
│       └── ...
├── service/             # 业务逻辑层
│   ├── AuthService.java       # 认证服务（注册、登录、登出、刷新 Token）
│   ├── UserService.java       # 用户服务（个人资料管理）
│   ├── FolderService.java     # 文件夹服务（树形结构管理）
│   ├── BookmarkService.java   # 书签服务（CRUD + 排序 + 批量操作）
│   ├── StagingService.java    # 暂存区服务
│   └── AdminService.java      # 管理员服务（用户管理）
├── controller/          # REST API 控制器（入口层）
│   ├── AuthController.java        # /api/auth/** 认证接口
│   ├── UserController.java        # /api/user/** 用户资料接口
│   ├── FolderController.java      # /api/folders/** 文件夹接口
│   ├── BookmarkController.java    # /api/bookmarks/** 书签接口
│   ├── StagingController.java     # /api/staging/** 暂存区接口
│   ├── AdminController.java       # /api/admin/** 管理员接口
│   └── ExtensionController.java   # /api/ext/** 浏览器扩展专用接口
├── security/            # Spring Security + JWT 相关
│   ├── JwtTokenProvider.java      # JWT Token 生成与验证
│   ├── JwtAuthFilter.java         # JWT 认证过滤器
│   └── UserDetailsServiceImpl.java # Spring Security 用户加载实现
├── annotation/          # 自定义注解
│   └── RateLimit.java            # 接口限流标记注解
├── aspect/              # AOP 切面
│   ├── RateLimitAspect.java      # 限流切面
│   └── OperationLogAspect.java   # 操作日志切面
├── kafka/               # Kafka 消息生产者和消费者
│   ├── KafkaProducer.java            # 消息生产者
│   ├── OperationLogConsumer.java     # 操作日志消费者
│   ├── StagingCleanupConsumer.java   # 暂存区清理消费者
│   └── IconFetchConsumer.java        # 网站图标获取消费者
├── scheduled/           # 定时任务
│   └── StagingCleanupScheduler.java  # 暂存区过期清理定时任务
└── HlaiaNavigationBarApplication.java  # Spring Boot 启动类
```

---

## 模块组织规范

### 新增功能的文件放置规则

添加新功能时（例如新增"标签"功能），需要创建以下文件：

| 文件类型 | 放置位置 | 命名规范 | 示例 |
|----------|----------|----------|------|
| 实体类 | `entity/` | 名词，与表名对应（首字母大写驼峰） | `Tag.java` |
| Mapper 接口 | `mapper/` | 实体名 + Mapper | `TagMapper.java` |
| 请求 DTO | `dto/request/` | 动作 + 实体名 + Request | `TagCreateRequest.java` |
| 响应 DTO | `dto/response/` | 实体名 + Response | `TagResponse.java` |
| Service 类 | `service/` | 实体名 + Service | `TagService.java` |
| Controller 类 | `controller/` | 实体名 + Controller | `TagController.java` |

### 不创建的文件（本项目约定）

- **不创建 Service 接口 + Impl 实现类**：Service 直接用具体类（`@Service`），不写接口。因为项目规模不大，不需要多实现。
- **不创建 XML Mapper 文件**：所有查询通过 MyBatis-Plus 的 `LambdaQueryWrapper` 在 Service 层构建，不写自定义 SQL XML。
- **不创建独立的 util 包**：工具方法放在对应的类中（如 `toResponse()` 是 Service 的 private 方法）。

---

## 命名约定

### Java 类命名

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| 实体类 | 名词，与数据库表名对应 | `User`, `Folder`, `Bookmark`, `StagingItem`, `OperationLog` |
| Mapper 接口 | 实体名 + `Mapper` | `UserMapper`, `FolderMapper` |
| Service 类 | 实体名/功能名 + `Service` | `AuthService`, `FolderService`, `AdminService` |
| Controller 类 | 实体名/功能名 + `Controller` | `AuthController`, `AdminController` |
| 请求 DTO | 动作/实体名 + `Request` | `LoginRequest`, `FolderCreateRequest`, `BatchDeleteRequest` |
| 响应 DTO | 实体名/内容 + `Response` | `AuthResponse`, `FolderTreeResponse`, `UserResponse` |
| 配置类 | 功能名 + `Config` | `SecurityConfig`, `MyBatisPlusConfig` |
| AOP 切面 | 功能名 + `Aspect` | `RateLimitAspect`, `OperationLogAspect` |
| 自定义注解 | 功能名（名词） | `@RateLimit` |

### 方法命名（Controller 层）

Controller 方法名使用简短的动词，与 HTTP 方法语义对应：

| HTTP 方法 | Controller 方法名 | 示例 |
|-----------|-------------------|------|
| GET | `list`, `getTree`, `getProfile` | `list()`, `getTree()`, `getProfile()` |
| POST | `create`, `add`, `register`, `login` | `create()`, `addBookmark()` |
| PUT | `update`, `sort`, `move`, `banUser` | `update()`, `sort()` |
| DELETE | `delete`, `batchDelete` | `delete()`, `batchDelete()` |

### 方法命名（Service 层）

Service 方法名更具体，描述业务动作：

| 模式 | 示例 |
|------|------|
| 查询 | `getFolderTree()`, `getBookmarksByFolder()`, `getProfile()` |
| 创建 | `createBookmark()`, `createFolder()`, `register()` |
| 更新 | `updateBookmark()`, `updateExpiry()`, `changePassword()` |
| 删除 | `deleteBookmark()`, `deleteFolder()`, `batchDelete()` |
| 特殊操作 | `sortBookmarks()`, `moveFolder()`, `moveToFolder()`, `banUser()` |
| 私有辅助 | `getBookmarkForUser()`, `toResponse()`, `toTreeResponse()` |

---

## 分层职责

### Controller 层 -- 只做请求转发

Controller 层的职责严格限制为：
1. 接收 HTTP 请求（`@RequestBody`, `@PathVariable`, `@RequestParam`）
2. 调用 Service 方法
3. 用 `Result.success()` 包装返回值
4. 返回 `Result<T>` 类型

Controller **不包含**业务逻辑。参考 `src/main/java/com/hlaia/controller/BookmarkController.java`：

```java
@PostMapping("/bookmarks")
public Result<BookmarkResponse> create(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody BookmarkCreateRequest request) {
    return Result.success(bookmarkService.createBookmark(userId, request));
}
```

### Service 层 -- 业务逻辑核心

Service 层负责：
1. 业务逻辑处理（权限校验、排序计算、树形结构构建）
2. 调用 Mapper 进行数据库操作
3. Entity <-> DTO 转换（通过 private 的 `toResponse()` 方法）
4. 事务管理（`@Transactional`）
5. 调用 KafkaProducer 发送异步消息

### Mapper 层 -- 纯数据访问

Mapper 接口只继承 `BaseMapper<T>`，不添加自定义方法。所有查询条件在 Service 层用 `LambdaQueryWrapper` 构建。

---

## API 路径设计

| 模块 | 基础路径 | 权限 |
|------|----------|------|
| 认证 | `/api/auth/**` | 公开（permitAll） |
| 用户资料 | `/api/user/**` | 需登录（authenticated） |
| 文件夹 | `/api/folders/**` | 需登录（authenticated） |
| 书签 | `/api/bookmarks/**` | 需登录（authenticated） |
| 暂存区 | `/api/staging/**` | 需登录（authenticated） |
| 管理员 | `/api/admin/**` | 仅管理员（hasRole("ADMIN")） |
| 浏览器扩展 | `/api/ext/**` | 需登录（authenticated） |
| API 文档 | `/swagger-ui/**`, `/v3/api-docs/**` | 公开（permitAll） |

---

## 依赖注入方式

统一使用 **构造器注入**（`final` 字段 + `@RequiredArgsConstructor`），不使用 `@Autowired` 字段注入。

```java
@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final BookmarkMapper bookmarkMapper;
    private final KafkaProducer kafkaProducer;
    // ...
}
```

参考文件：
- `src/main/java/com/hlaia/service/BookmarkService.java`
- `src/main/java/com/hlaia/controller/BookmarkController.java`
- `src/main/java/com/hlaia/aspect/RateLimitAspect.java`
