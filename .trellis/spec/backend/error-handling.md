# 错误处理规范

> 本项目的错误类型定义、异常传播机制和 API 错误响应格式。

---

## 概述

本项目采用统一的错误处理架构：

1. **ErrorCode 枚举** -- 集中定义所有错误码和错误信息
2. **BusinessException 自定义异常** -- 在 Service 层抛出业务异常
3. **GlobalExceptionHandler** -- 全局捕获异常，转换为统一的 JSON 响应
4. **Result 包装类** -- 所有 API 返回统一的 `{code, message, data}` 格式

核心文件：
- `src/main/java/com/hlaia/common/ErrorCode.java`
- `src/main/java/com/hlaia/common/BusinessException.java`
- `src/main/java/com/hlaia/common/GlobalExceptionHandler.java`
- `src/main/java/com/hlaia/common/Result.java`

---

## 错误码定义（ErrorCode）

### 错误码规划规则

| 范围 | 用途 | 示例 |
|------|------|------|
| 200 | 成功 | `SUCCESS(200, "success")` |
| 400 | 请求参数错误 | `BAD_REQUEST(400, "Bad request")` |
| 401 | 未认证 | `UNAUTHORIZED(401, "Unauthorized")` |
| 403 | 无权限 | `FORBIDDEN(403, "Forbidden")` |
| 404 | 资源不存在 | `NOT_FOUND(404, "Not found")` |
| 500 | 服务器内部错误 | `INTERNAL_ERROR(500, "Internal server error")` |
| 1001-1999 | 认证相关错误 | `USER_EXISTS(1001)`, `TOKEN_EXPIRED(1003)` |
| 2001-2999 | 业务逻辑错误 | `FOLDER_NOT_FOUND(2001)`, `RATE_LIMITED(2007)` |

参考 `src/main/java/com/hlaia/common/ErrorCode.java` 的完整定义：

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 标准 HTTP 错误码
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    INTERNAL_ERROR(500, "Internal server error"),

    // 认证相关（1001-1999）
    USER_EXISTS(1001, "Username already exists"),
    INVALID_CREDENTIALS(1002, "Invalid username or password"),
    TOKEN_EXPIRED(1003, "Token expired"),
    TOKEN_INVALID(1004, "Invalid token"),
    NICKNAME_EXISTS(1005, "Nickname already exists"),

    // 业务相关（2001-2999）
    FOLDER_NOT_FOUND(2001, "Folder not found"),
    BOOKMARK_NOT_FOUND(2002, "Bookmark not found"),
    STAGING_NOT_FOUND(2003, "Staging item not found"),
    USER_NOT_FOUND(2004, "User not found"),
    USER_BANNED(2005, "User is banned"),
    ACCESS_DENIED(2006, "Access denied"),
    RATE_LIMITED(2007, "Too many requests"),

    private final int code;
    private final String message;
}
```

### 新增错误码的规则

- 在对应的范围段内追加新的枚举值
- 使用大写下划线命名（如 `USER_EXISTS`）
- message 使用英文
- 错误码数字不可复用

---

## BusinessException 自定义异常

### 使用方式

在 Service 层通过 `throw new BusinessException(ErrorCode.XXX)` 抛出业务异常：

```java
// 参考 src/main/java/com/hlaia/service/AuthService.java
if (count > 0) {
    throw new BusinessException(ErrorCode.USER_EXISTS);
}

// 也可以直接传入错误码和信息
throw new BusinessException(400, "Cannot move folder into its own descendant");
```

### 设计要点

- 继承 `RuntimeException`（非受检异常），不需要在方法签名上声明 throws
- 携带 `code` 字段，GlobalExceptionHandler 可以提取并返回给前端
- 提供两种构造方法：通过 `ErrorCode` 枚举或直接传入 code/message

参考 `src/main/java/com/hlaia/common/BusinessException.java`：

```java
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    // 通过 ErrorCode 枚举创建（推荐）
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    // 直接传入错误码和信息
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

---

## 全局异常处理器（GlobalExceptionHandler）

### 处理的异常类型

参考 `src/main/java/com/hlaia/common/GlobalExceptionHandler.java`：

| 异常类型 | HTTP 状态码 | 处理方法 | 触发场景 |
|---------|------------|---------|---------|
| `BusinessException` | 200 (OK) | `handleBusinessException()` | Service 层主动抛出的业务异常 |
| `MethodArgumentNotValidException` | 400 (BAD_REQUEST) | `handleValidation()` | `@Valid` 校验 `@RequestBody` 失败 |
| `ConstraintViolationException` | 400 (BAD_REQUEST) | `handleConstraintViolation()` | `@Validated` 校验路径/查询参数失败 |
| `BadCredentialsException` | 401 (UNAUTHORIZED) | `handleBadCredentials()` | Spring Security 用户名或密码错误 |
| `AccessDeniedException` | 403 (FORBIDDEN) | `handleAccessDenied()` | Spring Security 权限不足 |
| `Exception` | 500 (INTERNAL_SERVER_ERROR) | `handleException()` | 所有其他未捕获的异常（兜底） |

### BusinessException 返回 HTTP 200 的设计

注意：`BusinessException` 的 `@ResponseStatus` 是 `HttpStatus.OK`（HTTP 200），因为通过 `Result` 中的 `code` 字段区分成功/失败，HTTP 层面都是 200。这种设计让前端只需解析 JSON body 来判断请求是否成功。

### 参数校验错误的返回格式

```json
{
    "code": 400,
    "message": "username: 用户名不能为空",
    "data": null
}
```

格式为 `字段名: 错误信息`，只返回第一个错误。

---

## 统一响应格式（Result）

### 成功响应

```java
// 无数据
return Result.success();
// 返回: {"code": 200, "message": "success", "data": null}

// 带数据
return Result.success(bookmarkService.createBookmark(userId, request));
// 返回: {"code": 200, "message": "success", "data": {"id": 1, "title": "...", ...}}
```

### 错误响应

```java
// 通过 ErrorCode 枚举（推荐）
return Result.error(ErrorCode.USER_NOT_FOUND);
// 返回: {"code": 2004, "message": "User not found", "data": null}

// 直接传入错误码和信息
return Result.error(400, "参数不能为空");
// 返回: {"code": 400, "message": "参数不能为空", "data": null}
```

参考 `src/main/java/com/hlaia/common/Result.java`。

---

## 错误传播流程

```
Controller 方法
    ↓ 调用
Service 方法
    ↓ 发现业务错误
throw new BusinessException(ErrorCode.XXX)
    ↓ 向上传播
GlobalExceptionHandler.handleBusinessException()
    ↓ 转换为 JSON
return Result.error(code, message)
    ↓ 返回给前端
{"code": 2001, "message": "Folder not found", "data": null}
```

### 典型使用场景

**场景 1: 资源不存在（最常见）**

```java
// src/main/java/com/hlaia/service/BookmarkService.java
private Bookmark getBookmarkForUser(Long userId, Long bookmarkId) {
    Bookmark bookmark = bookmarkMapper.selectById(bookmarkId);
    if (bookmark == null || !bookmark.getUserId().equals(userId)) {
        throw new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND);
    }
    return bookmark;
}
```

**场景 2: 业务规则违反**

```java
// src/main/java/com/hlaia/service/AuthService.java
if (count > 0) {
    throw new BusinessException(ErrorCode.USER_EXISTS);
}
```

**场景 3: 安全策略（防止信息泄露）**

```java
// 用户不存在和密码错误返回相同错误码，防止用户名枚举攻击
if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
}
```

---

## Controller 层不处理错误

Controller 方法不包含 try-catch，所有异常由 GlobalExceptionHandler 统一处理：

```java
// 正确写法 -- 不捕获异常
@PostMapping("/bookmarks")
public Result<BookmarkResponse> create(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody BookmarkCreateRequest request) {
    return Result.success(bookmarkService.createBookmark(userId, request));
}

// 错误写法 -- 不要在 Controller 中 try-catch
@PostMapping("/bookmarks")
public Result<BookmarkResponse> create(...) {
    try {
        return Result.success(bookmarkService.createBookmark(userId, request));
    } catch (BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }
}
```

---

## 参数校验（@Valid）

### Request DTO 上的校验注解

参考 `src/main/java/com/hlaia/dto/request/RegisterRequest.java`：

```java
@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
    private String password;

    @Size(max = 15, message = "昵称不能多于15个字符")
    private String nickname;  // 可选字段，不加 @NotBlank
}
```

### 校验注解选择规则

| 注解 | 适用场景 | 示例 |
|------|---------|------|
| `@NotNull` | 数值/对象类型不能为 null | `@NotNull Long folderId` |
| `@NotBlank` | String 不能为 null/空/纯空白 | `@NotBlank String title` |
| `@Size` | 字符串长度范围 | `@Size(min = 3, max = 50)` |
| `@NotEmpty` | 集合不能为空 | List 类型字段 |

### Controller 中触发校验

必须在请求体参数前加 `@Valid`：

```java
public Result<BookmarkResponse> create(@Valid @RequestBody BookmarkCreateRequest request) {
```

不加 `@Valid` 的话，DTO 上的校验注解不会执行。

---

## 常见错误

1. **在 Controller 中写 try-catch**: 应该让 GlobalExceptionHandler 统一处理
2. **忘记加 @Valid**: 导致 DTO 上的校验注解不生效
3. **Long 类型用 @NotBlank**: `@NotBlank` 只适用于 String，Long 用 `@NotNull`
4. **直接返回 Exception.getMessage() 给前端**: 可能泄露内部实现细节（如数据库错误信息），应返回友好的错误描述
5. **BusinessException 返回非 200 HTTP 状态码**: 本项目的约定是 HTTP 200 + Result 中的 code 字段区分错误
