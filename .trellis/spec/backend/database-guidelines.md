# 数据库规范

> 本项目数据库相关的设计模式、ORM 用法、迁移管理和命名约定。

---

## 概述

- **数据库**: MySQL 8，字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`
- **ORM**: MyBatis-Plus 3.5.15（Spring Boot 4 专用 starter: `mybatis-plus-spring-boot4-starter`）
- **分页插件**: `mybatis-plus-jsqlparser`（从 MyBatis-Plus 3.5.9+ 起分页插件从核心拆出为独立模块）
- **数据库迁移**: Flyway，迁移脚本位于 `src/main/resources/db/migration/`
- **5 张表**: `user`, `folder`, `bookmark`, `staging_item`, `operation_log`

---

## 数据库表结构

### 表清单

| 表名 | 用途 | 实体类 |
|------|------|--------|
| `user` | 用户表 | `src/main/java/com/hlaia/entity/User.java` |
| `folder` | 文件夹表（邻接表模型，无限层级树形结构） | `src/main/java/com/hlaia/entity/Folder.java` |
| `bookmark` | 书签表 | `src/main/java/com/hlaia/entity/Bookmark.java` |
| `staging_item` | 暂存区项表 | `src/main/java/com/hlaia/entity/StagingItem.java` |
| `operation_log` | 操作日志表 | `src/main/java/com/hlaia/entity/OperationLog.java` |

### 字段命名约定

- **表名**: `snake_case`（小写 + 下划线），如 `staging_item`, `operation_log`
- **列名**: `snake_case`，如 `user_id`, `parent_id`, `sort_order`, `created_at`
- **主键**: 统一使用 `id`（BIGINT AUTO_INCREMENT）
- **外键**: `实体名_id`，如 `user_id`, `folder_id`, `parent_id`
- **时间字段**: `created_at`, `updated_at`（DATETIME 类型）
- **Java 实体字段**: `camelCase`，MyBatis-Plus 自动映射（`created_at` <-> `createdAt`）

### 通用字段模式

几乎所有表都包含以下字段（参考 `V1__init_schema.sql`）：

```sql
`id`         BIGINT       NOT NULL AUTO_INCREMENT,
`created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`)
```

- `created_at`: 数据库自动填充（`DEFAULT CURRENT_TIMESTAMP`）
- `updated_at`: 数据库自动更新（`ON UPDATE CURRENT_TIMESTAMP`）
- `operation_log` 表只有 `created_at`，没有 `updated_at`（日志只增不改）
- `staging_item` 表只有 `created_at`，没有 `updated_at`（暂存项创建后不修改）

### 树形结构设计（folder 表）

文件夹使用邻接表模型（Adjacency List）实现无限层级树形结构：

```sql
`parent_id` BIGINT DEFAULT NULL COMMENT 'NULL means root level',
KEY `idx_parent_id` (`parent_id`),
CONSTRAINT `fk_folder_parent` FOREIGN KEY (`parent_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
```

- `parent_id = NULL` 表示顶级文件夹
- `parent_id > 0` 表示父文件夹的 ID
- 外键约束 `ON DELETE CASCADE`：删除父文件夹时自动删除子文件夹

---

## 实体类（Entity）编写规范

### 基本注解模式

每个实体类遵循相同的注解模式（参考 `src/main/java/com/hlaia/entity/User.java`）：

```java
@Data                              // Lombok: 生成 getter/setter/toString/equals/hashCode
@TableName("user")                 // MyBatis-Plus: 指定对应的数据库表名
public class User {

    @TableId(type = IdType.AUTO)   // 主键，自增策略
    private Long id;

    private String username;       // MyBatis-Plus 自动将 camelCase 映射为 snake_case
    // ...
}
```

### 字段类型约定

| 数据库类型 | Java 类型 | 说明 |
|-----------|----------|------|
| BIGINT | `Long` | 主键和外键统一使用包装类型 |
| VARCHAR | `String` | 字符串字段 |
| TINYINT | `Integer` | 状态字段（如 status 0/1），使用 Integer 而非 int |
| INT | `Integer` | 排序序号等 |
| DATETIME | `LocalDateTime` | Java 8 时间类型 |
| TEXT | `String` | 长文本字段 |

### 实体类不包含的注解

- 不使用 `@TableField(exist = false)` 标注虚拟字段
- 不使用 MyBatis-Plus 的自动填充功能（`@TableField(fill = FieldFill.INSERT)`），时间字段由数据库自动管理

---

## Mapper 接口规范

所有 Mapper 接口只继承 `BaseMapper<T>`，不添加自定义方法（参考 `src/main/java/com/hlaia/mapper/UserMapper.java`）：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 不添加自定义方法，所有查询通过 LambdaQueryWrapper 在 Service 层构建
}
```

### 分页插件配置

分页功能通过 `MybatisPlusConfig` 配置（参考 `src/main/java/com/hlaia/config/MyBatisPlusConfig.java`）：

```java
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

---

## 查询模式

### 所有查询都在 Service 层用 LambdaQueryWrapper 构建

不写 XML SQL，不写 `@Select` 注解。所有查询条件通过 `LambdaQueryWrapper` 的流式 API 构建。

参考 `src/main/java/com/hlaia/service/BookmarkService.java`：

```java
// 等价于 SQL: WHERE user_id = ? AND folder_id = ? ORDER BY sort_order ASC
List<Bookmark> bookmarks = bookmarkMapper.selectList(
        new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getUserId, userId)
                .eq(Bookmark::getFolderId, folderId)
                .orderByAsc(Bookmark::getSortOrder));
```

### 常用查询操作

| 操作 | LambdaQueryWrapper 方法 | SQL 等价 |
|------|------------------------|---------|
| 等值查询 | `.eq(Folder::getUserId, userId)` | `WHERE user_id = ?` |
| 不等于 | `.ne(User::getId, userId)` | `WHERE id != ?` |
| 大于 | `.gt(StagingItem::getExpireAt, LocalDateTime.now())` | `WHERE expire_at > NOW()` |
| 升序排列 | `.orderByAsc(Bookmark::getSortOrder)` | `ORDER BY sort_order ASC` |
| 降序排列 | `.orderByDesc(User::getCreatedAt)` | `ORDER BY created_at DESC` |
| 计数 | `mapper.selectCount(wrapper)` | `SELECT COUNT(*)` |
| 条件拼接 | `.eq(condition, Folder::getParentId, parentId)` | `condition 为 true 时拼接` |
| IS NULL | `.isNull(Folder::getParentId)` | `WHERE parent_id IS NULL` |
| LIMIT 1 | `.last("LIMIT 1")` | `LIMIT 1` |

### 条件动态拼接

参考 `src/main/java/com/hlaia/service/FolderService.java` 中的 `createFolder` 方法：

```java
// 根据 parentId 是否为 null 动态拼接不同的条件
Long count = folderMapper.selectCount(
        new LambdaQueryWrapper<Folder>()
                .eq(Folder::getUserId, userId)
                .eq(request.getParentId() != null, Folder::getParentId, request.getParentId())
                .isNull(request.getParentId() == null, Folder::getParentId));
```

### 分页查询

参考 `src/main/java/com/hlaia/service/AdminService.java` 中的 `getUserList` 方法：

```java
Page<User> userPage = userMapper.selectPage(
        new Page<>(page, size),
        new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));

// Entity 分页转 DTO 分页
Page<UserResponse> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
result.setRecords(userPage.getRecords().stream().map(this::toUserResponse).collect(Collectors.toList()));
```

### 树形结构构建算法

参考 `src/main/java/com/hlaia/service/FolderService.java` 中的 `getFolderTree` 方法：

1. 查询用户所有文件夹（扁平列表）
2. 用 `Collectors.toMap()` 建立以 ID 为 key 的 Map
3. 遍历所有文件夹，根据 `parentId` 挂载到父节点的 `children` 列表
4. `parentId` 为 null 的加入 roots 列表

---

## 事务管理

### @Transactional 使用场景

| 场景 | 是否需要 @Transactional | 示例 |
|------|------------------------|------|
| 单次 insert | 加上（保持风格一致） | `createBookmark()` |
| 先查后改（selectById + updateById） | 需要 | `updateBookmark()`, `banUser()` |
| 多次写操作（循环 update） | 需要 | `sortBookmarks()`, `batchDelete()` |
| 跨表操作（insert + delete） | 必须 | `moveToFolder()` |
| 纯查询 | 不需要 | `getBookmarksByFolder()`, `getFolderTree()` |

参考 `src/main/java/com/hlaia/service/StagingService.java` 中的 `moveToFolder` 方法 -- 创建书签 + 删除暂存项两步操作必须在同一事务中。

---

## 数据库迁移（Flyway）

### 迁移脚本位置

`src/main/resources/db/migration/`

### 命名规范

```
V{序号}__{描述}.sql
```

| 文件 | 内容 |
|------|------|
| `V1__init_schema.sql` | 创建所有 5 张表 + 索引 + 外键 |
| `V2__init_admin.sql` | 管理员初始化说明（实际通过 CommandLineRunner 程序化创建） |
| `V3__add_nickname_to_user.sql` | 给 user 表添加 nickname 字段 |

### 迁移脚本编写规范

1. 所有建表语句使用 `CREATE TABLE IF NOT EXISTS`
2. 字符集统一 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`
3. 存储引擎 `InnoDB`
4. 添加 COMMENT 注释说明字段含义
5. 为外键字段创建索引（`KEY idx_xxx`）
6. 外键约束使用 `ON DELETE CASCADE`
7. 默认管理员不在 SQL 中硬编码，通过 CommandLineRunner 程序化创建

参考 `src/main/resources/db/migration/V1__init_schema.sql` 中 `bookmark` 表的完整定义：

```sql
CREATE TABLE IF NOT EXISTS `bookmark` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT        NOT NULL,
    `folder_id`   BIGINT        NOT NULL,
    `title`       VARCHAR(255)  NOT NULL,
    `url`         VARCHAR(2048) NOT NULL,
    `description` VARCHAR(500)  DEFAULT NULL,
    `icon_url`    VARCHAR(500)  DEFAULT NULL,
    `sort_order`  INT           NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_folder_id` (`folder_id`),
    UNIQUE KEY `uk_bookmark_user_url` (`user_id`, `url`(500)),
    CONSTRAINT `fk_bookmark_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bookmark_folder` FOREIGN KEY (`folder_id`) REFERENCES `folder` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 数据隔离模式

所有业务数据通过 `userId` 实现用户间数据隔离。Service 层通过 `getXXXForUser()` 私有方法校验数据归属：

```java
// 参考 src/main/java/com/hlaia/service/BookmarkService.java
private Bookmark getBookmarkForUser(Long userId, Long bookmarkId) {
    Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
    if (bookmark == null || !bookmark.getUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND);
    }
    return bookmark;
}
```

注意：权限不足时返回"不存在"而非"无权限"，防止信息泄露（模糊响应安全策略）。

---

## 删除策略

当前使用**物理删除**（`deleteById`, `deleteBatchIds`），不使用逻辑删除。

- `AdminService` 和 `FolderService` 的删除操作直接调用 `mapper.deleteById(id)`
- `BookmarkService` 的批量删除使用 `mapper.deleteBatchIds(ids)`

---

## 常见错误

1. **忘记配置分页插件**: 没有注册 `PaginationInnerInterceptor`，`selectPage` 会返回全部数据
2. **字段类型用 int 而非 Integer**: 数据库字段可能为 NULL，Java 必须用包装类型
3. **使用 `javax.*` 而非 `jakarta.*`**: Spring Boot 4.x 使用 `jakarta` 命名空间
4. **在 Mapper 接口写自定义 SQL**: 所有查询应在 Service 层用 `LambdaQueryWrapper` 构建
