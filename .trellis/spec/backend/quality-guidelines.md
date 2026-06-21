# 代码质量规范

> 本项目的代码风格要求、中文学习注释规范、必须遵循的模式和禁止的模式。

---

## 概述

本项目是一个**教学项目**，开发者是 Java 后端初学者，因此代码质量标准以**可读性和学习价值**为首要目标。所有 Java 文件必须添加中文学习注释，帮助理解注解的作用、设计模式和"为什么这样写"。

---

## 中文学习注释规范（最重要）

### 核心原则

每个 Java 文件必须包含**中文学习注释**，面向 Java 后端初学者。注释应解释：

| 解释内容 | 优先级 | 示例 |
|---------|--------|------|
| 注解的作用 | 必须 | `@Service 注解：告诉 Spring 这是一个业务逻辑类` |
| 设计模式 | 必须 | `统一响应格式、工厂模式创建 Result` |
| 为什么这样写（WHY） | 必须 | `为什么返回"不存在"而不是"无权限"？防止信息泄露` |
| 核心概念解释 | 推荐 | `什么是泛型 <T>？什么是 ORM？什么是 AOP？` |
| 算法思路 | 推荐 | `树形结构构建算法（两次遍历法）` |

### 注释风格

参考 `src/main/java/com/hlaia/common/Result.java`：

```java
/**
 * 【统一响应包装类】—— 所有 API 接口的返回值都使用这个类包装
 *
 * 什么是泛型 <T>？
 *   Result<T> 中的 T 是类型参数，表示 data 字段可以是任何类型。
 *   例如：Result<User> 的 data 是 User 对象，Result<List<Bookmark>> 的 data 是书签列表。
 *   这样一个类就能适配所有接口的返回值类型。
 *
 * 为什么要统一响应格式？
 *   前端只需要按同一种格式解析所有接口的返回值，代码更简洁、更不容易出错。
 */
@Data
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;
    // ...
}
```

### 注释模板

每个类的类级注释应包含：

```java
/**
 * 【类名简述】—— 类的职责/用途
 *
 * 核心概念解释（如果是初学者不熟悉的）
 *
 * 使用场景/业务流程
 *
 * 关键注解说明
 */
```

### 不需要的注释

- **不需要**对每一行代码都加注释
- **不需要**解释显而易见的代码（如 `return 0;`）
- **不需要**重复方法名已表达的信息
- **不写多余的注释**，但对初学者不明显的概念要解释

---

## 必须遵循的模式（Required Patterns）

### 1. 统一响应包装

所有 Controller 方法返回 `Result<T>`：

```java
// 查询 -- 带数据
return Result.success(service.getList(userId));

// 创建 -- 带数据
return Result.success(service.create(userId, request));

// 删除/更新 -- 无数据
return Result.success();
```

### 2. 构造器注入（不用 @Autowired）

```java
@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final BookmarkMapper bookmarkMapper;
    private final KafkaProducer kafkaProducer;
}
```

### 3. Service 层权限校验模式

每个 Service 都有一个 `getXXXForUser()` 私有方法，校验资源归属：

```java
private Bookmark getBookmarkForUser(Long userId, Long bookmarkId) {
    Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
    if (bookmark == null || !bookmark.getUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND);
    }
    return bookmark;
}
```

参考文件：
- `src/main/java/com/hlaia/service/BookmarkService.java` -- `getBookmarkForUser()`
- `src/main/java/com/hlaia/service/FolderService.java` -- `getFolderForUser()`
- `src/main/java/com/hlaia/service/StagingService.java` -- `getStagingItemForUser()`

### 4. Entity <-> DTO 手动转换

使用 private 的 `toResponse()` 方法手动转换，**不使用 BeanUtils.copyProperties**：

```java
private BookmarkResponse toResponse(Bookmark bookmark) {
    BookmarkResponse dto = new BookmarkResponse();
    dto.setId(bookmark.getId());
    dto.setTitle(bookmark.getTitle());
    // ...
    return dto;
}
```

参考 `src/main/java/com/hlaia/service/AdminService.java` 中的 `toUserResponse` 方法注释，解释了为什么手动转换比 BeanUtils 更好。

### 5. @Transactional 加在 Service 层

事务注解只加在 Service 层的方法上，Controller 层不加。

### 6. Swagger/OpenAPI 注解

Controller 类使用 `@Tag`，每个方法使用 `@Operation`：

```java
@RestController
@RequestMapping("/api/bookmarks")
@Tag(name = "Bookmarks", description = "Bookmark management APIs")
public class BookmarkController {

    @PostMapping
    @Operation(summary = "Create a bookmark")
    public Result<BookmarkResponse> create(...) { }
}
```

### 7. Lombok 注解组合

| 注解 | 用途 | 使用位置 |
|------|------|---------|
| `@Data` | Entity、DTO 类 | Entity、Request DTO、Response DTO |
| `@Getter` | 只需要 getter 的类 | ErrorCode 枚举 |
| `@AllArgsConstructor` | 有全参构造需求的类 | ErrorCode 枚举 |
| `@RequiredArgsConstructor` | 依赖注入的类 | Service、Controller、Aspect、Component |
| `@Builder` | 需要链式创建的类 | AuthResponse |
| `@Slf4j` | 需要日志的类 | KafkaProducer、Consumer |

---

## 禁止的模式（Forbidden Patterns）

### 1. 禁止使用 @Autowired 字段注入

```java
// 禁止
@Autowired
private BookmarkMapper bookmarkMapper;

// 正确
@RequiredArgsConstructor
public class BookmarkService {
    private final BookmarkMapper bookmarkMapper;
}
```

### 2. 禁止使用 javax.* 包名

Spring Boot 4.x 使用 `jakarta.*`：

```java
// 禁止
import javax.validation.constraints.NotBlank;

// 正确
import jakarta.validation.constraints.NotBlank;
```

### 3. 禁止在 Controller 中写业务逻辑

Controller 只做三件事：接收请求、调用 Service、包装 Result。

### 4. 禁止在 Controller 中 try-catch 业务异常

所有异常由 GlobalExceptionHandler 统一处理。

### 5. 禁止在 Service 接口中写自定义 SQL

不写 XML Mapper，不写 `@Select` 注解。所有查询用 `LambdaQueryWrapper`。

### 6. 禁止返回 Entity 给前端

必须通过 `toResponse()` 转为 DTO，确保：
- `password` 等敏感字段不会泄露
- Entity 结构变化不影响 API 契约
- 只返回前端需要的字段

### 7. 禁止使用 spring-boot-starter-aop

Spring Boot 4.x 已移除 `spring-boot-starter-aop`，使用 `spring-aspects` 代替（已在 pom.xml 中配置）。

### 8. 禁止用 int 基本类型定义可能为 NULL 的字段

数据库字段可能为 NULL 时，Java 必须用包装类型（`Integer`, `Long`），不能用基本类型（`int`, `long`）。

---

## 测试要求

### 当前测试状态

项目目前只有一个基础的集成测试（`src/test/java/com/hlaia/HlaiaNavigationBarApplicationTests.java`）：

```java
@SpringBootTest
class HlaiaNavigationBarApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

### 测试依赖

参考 `pom.xml` 中的测试依赖：

- `spring-boot-starter-test` -- Spring Boot 测试框架
- `spring-security-test` -- Spring Security 测试支持
- `spring-kafka-test` -- Kafka 测试支持（嵌入式 Kafka）
- `h2` -- 内存数据库，用于测试环境

### 测试配置

`src/test/resources/application.yml` 使用与主应用相同的端口和 profile 配置。

---

## 代码审查清单

审查新代码时检查以下项目：

### 结构检查
- [ ] 新文件放在正确的包中（entity/mapper/dto/service/controller）
- [ ] Controller 方法返回 `Result<T>`
- [ ] Service 类使用 `@RequiredArgsConstructor` + `final` 字段注入
- [ ] 没有在 Controller 中写业务逻辑

### 注释检查
- [ ] 类级注释包含【标题】和职责说明
- [ ] 关键注解有中文解释
- [ ] WHY 注释（为什么这样写）存在
- [ ] 没有"废话注释"（如 `// set name` 对应 `setName()`）

### 安全检查
- [ ] Response DTO 不包含 password 字段
- [ ] Service 层有 `getXXXForUser()` 权限校验
- [ ] 参数校验注解 + `@Valid` 正确使用
- [ ] 使用 `jakarta.*` 而非 `javax.*`

### 数据库检查
- [ ] 新表使用 `utf8mb4` 字符集
- [ ] 外键字段有索引
- [ ] 时间字段用 `LocalDateTime`
- [ ] 数值字段用包装类型

### 事务检查
- [ ] 涉及多次写操作的方法有 `@Transactional`
- [ ] 查询方法不需要 `@Transactional`

---

## 常见错误

1. **忘记在 Controller 方法的 @RequestBody 参数前加 @Valid**: 导致 DTO 校验注解不生效
2. **Service 没有做用户级数据隔离**: 其他用户可以通过猜 ID 访问到不属于自己的数据
3. **直接返回 Entity 给前端**: 密码等敏感信息可能泄露
4. **使用 BeanUtils.copyProperties 复制 Entity 到 Response DTO**: 可能意外复制 password 字段
5. **忘记配置分页插件**: MyBatis-Plus 的 `selectPage` 不会自动分页
6. **在 Spring Boot 4 中使用 `javax.*`**: 必须使用 `jakarta.*`
7. **使用 `spring-boot-starter-aop`**: Spring Boot 4 已移除，改用 `spring-aspects`
