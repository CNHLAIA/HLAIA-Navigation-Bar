# 日志规范

> 本项目的日志记录方式、日志级别使用和操作日志机制。

---

## 概述

本项目有两种日志机制：

1. **应用日志（SLF4J）**: 通过 `@Slf4j` 注解在代码中直接输出日志，用于调试和问题排查
2. **操作日志（AOP + @Async + 数据库）**: 通过 `OperationLogAspect` 切面自动记录用户操作，用于安全审计

---

## 应用日志（SLF4J）

### 使用方式

通过 Lombok 的 `@Slf4j` 注解自动生成 `log` 对象：

```java
@Slf4j
@Service
public class IconFetchService {
    public void fetchAndSaveIcon(Long bookmarkId, String url) {
        // ...
        log.info("Updated icon for bookmark {}: {}", bookmarkId, iconUrl);
    }
}
```

### 使用 @Slf4j 的文件

当前项目中使用 `@Slf4j` 的类（典型代表）：
- `src/main/java/com/hlaia/service/IconFetchService.java` -- favicon 抓取日志
- `src/main/java/com/hlaia/service/OperationLogService.java` -- 操作日志写入失败告警
- `src/main/java/com/hlaia/service/SearchSyncService.java` -- ES 同步日志
- `src/main/java/com/hlaia/service/SearchService.java` -- 全量重建索引日志

### 日志级别使用规范

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| `log.info()` | 关键业务操作的成功记录 | favicon 回填成功、ES 同步成功 |
| `log.warn()` | 异常但可恢复的情况 | 操作日志写入失败、ES 同步失败（事务已提交，靠 reindex 兜底） |
| `log.error()` | 操作失败但不影响系统运行 | 当前主要用于非业务路径 |
| `log.debug()` | 开发调试信息 | SSRF 防护拒绝的 URL、favicon 解析失败细节 |

### 日志格式约定

- 使用 `{}` 占位符而非字符串拼接：`log.info("Synced bookmark {} to ES", id)`
- 日志消息使用英文
- 包含足够的上下文信息（如资源 ID、用户 ID）

---

## 操作日志（AOP 自动记录，仅审计管理员越权操作）

### 架构

操作日志通过 AOP 切面自动记录，**只记录管理员的越权写操作**，流程如下：

```
AdminController 写操作方法执行（ban/unban/deleteFolder）
    ↓ AOP 拦截
OperationLogAspect.logOperation()
    ↓ 先执行目标方法
joinPoint.proceed()
    ↓ 成功后记录日志（GET 请求跳过）
operationLogService.record(adminId, action, target, detail)
    ↓ @Async 虚拟线程异步执行
OperationLogService.record()
    ↓ 写入数据库
operation_log 表
```

### OperationLogAspect 切面

参考 `src/main/java/com/hlaia/aspect/OperationLogAspect.java`：

- **拦截范围**: 仅 `com.hlaia.controller.AdminController` 下的方法
- **过滤 GET**: 切面体内通过 `isAnnotationPresent(GetMapping.class)` 跳过只读操作（listUsers / getUserFolders）
- **记录时机**: 先执行目标方法（`proceed()`），只有成功返回后才记录日志
- **记录内容**:
  - `userId`（管理员 ID，从 SecurityContext 获取）
  - `action`（语义化常量，如 `BAN_USER` / `UNBAN_USER` / `DELETE_FOLDER`）
  - `target`（类名.方法名，如 `AdminController.banUser`）
  - `detail`（业务上下文，如 `banned user 5` / `deleted folder 12`，取第一个方法参数）
- **容错设计**: 日志记录失败不影响业务请求（catch 块捕获异常 + `log.warn`）

```java
@Around("execution(* com.hlaia.controller.AdminController.*(..))")
public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
    Object result = joinPoint.proceed();  // 先执行业务方法
    try {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // 只审计写操作，GET（listUsers / getUserFolders）跳过
        if (method.isAnnotationPresent(GetMapping.class)) {
            return result;
        }
        // 根据方法名 switch 生成 action / detail
        // 通过 OperationLogService 异步写库（@Async 虚拟线程）
        operationLogService.record(adminId, action, target, detail);
    } catch (Exception e) {
        log.warn("操作日志记录失败: {}", e.getMessage());
    }
    return result;
}
```

### 为什么只审计 AdminController？

历史版本曾拦截 `controller` 包下所有方法（仅排除 AuthController），导致：
1. 用户改自己的书签/文件夹也被记，日志被高频噪音淹没
2. 用户操作自己的数据本就不属于审计范畴——审计关心的是"越权"
3. `detail` 字段永远是 null（只有方法名，没有业务上下文），记下来没人看

真正有审计价值的只有管理员的 3 个越权写操作：
- `banUser` 封禁用户（剥夺他人登录权限）
- `unbanUser` 解封用户（恢复他人登录权限）
- `deleteFolder` 删除任意用户的文件夹（破坏他人数据）

收窄后日志量从"每请求一条"降到"管理员越权时才记"，回归审计本质。

### 为什么不用自定义注解 `@OperationLog`？

注解 + SpEL 方案更灵活（任意标注方法 + 任意 detail 模板），但当前需要审计的方法只有 3 个
且全部集中在 AdminController，切面内 switch 方法名完全够用。YAGNI——真要扩展时再上注解也不迟。

### action 常量约定

action 字段使用大写下划线常量，便于 SQL 检索：

| action | 触发方法 | detail 示例 |
|--------|---------|------------|
| `BAN_USER` | `AdminController.banUser` | `banned user 5` |
| `UNBAN_USER` | `AdminController.unbanUser` | `unbanned user 5` |
| `DELETE_FOLDER` | `AdminController.deleteFolder` | `deleted folder 12` |

未来新增的 AdminController 写操作方法会被切面的 default 分支兜底（action = 方法名大写，detail = null），
不会 NPE 也不会漏记。

### OperationLogService 落库

参考 `src/main/java/com/hlaia/service/OperationLogService.java`：

- 方法 `record(userId, action, target, detail)` 标注 `@Async`，在虚拟线程后台执行
- 直接 `operationLogMapper.insert()` 写入 `operation_log` 表（不再经过消息队列）
- `createdAt` 使用服务器当前时间（异步方法可能因线程调度延迟执行）
- 方法内部 try/catch 兜底，失败仅 `log.warn`，不向上抛

### 为什么不再用 Kafka？

历史版本曾用 `KafkaProducer → operation-log Topic → OperationLogConsumer` 的链路异步写库。
但消费者最终也是写同一个 MySQL，绕一圈消息队列反而增加了网络跳、JSON 序列化和 MQ 持久化开销，
还多了一个 Kafka 单点故障。单体内 `@Async` 直接写库最简单可靠。

---

## 操作日志数据库表结构

参考 `src/main/resources/db/migration/V1__init_schema.sql`：

```sql
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       DEFAULT NULL,
    `action`     VARCHAR(50)  NOT NULL,
    `target`     VARCHAR(200) DEFAULT NULL,
    `detail`     TEXT         DEFAULT NULL,
    `ip`         VARCHAR(50)  DEFAULT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
);
```

### 日志表的特点

- 只有 `created_at`，没有 `updated_at`（日志只增不改）
- `user_id` 允许 NULL（某些操作可能没有关联用户）
- `detail` 由切面填充业务上下文（如 `banned user 5`），不再是 null
- `ip` 字段当前仍为 null（预留字段，未来可由切面从 `HttpServletRequest` 提取）
- `V6__truncate_operation_log.sql` 在收窄方案上线时清空了历史噪音数据

---

## 禁止记录的内容

1. **密码**: 绝不能在日志中输出密码明文或密文
2. **JWT Token 完整内容**: 不要记录完整的 Token 字符串
3. **用户隐私数据**: 邮箱等 PII 信息不应出现在应用日志中
4. **SQL 语句中的参数值**: 避免在日志中输出用户提交的原始数据

---

## 日志记录失败的处理原则

核心原则：**日志记录失败不应影响正常的业务请求**

在 `OperationLogAspect` 中体现为：
- 日志记录代码被 try-catch 包裹
- catch 块捕获 Exception（不是 Throwable），Error 级别的异常不吞掉
- 业务方法已成功执行（`proceed()` 在 try 之前调用），返回值不受影响

在 `OperationLogService.record()` 中体现为：
- 方法内部 try/catch 兜底，失败仅 `log.warn`，不向上抛
- 单条日志写入失败不影响后续请求

---

## 常见错误

1. **在 Controller/Service 中手动写操作日志代码**: 应该让 AOP 切面自动处理
2. **日志中使用字符串拼接而非占位符**: `log.info("id=" + id)` 应改为 `log.info("id={}", id)`
3. **记录敏感信息**: 绝不在日志中输出密码、Token 等敏感数据
4. **`@Async` 方法自调用**: Spring 的 `@Async` 通过代理生效，必须在不同的 Bean 之间调用。
   `OperationLogAspect → OperationLogService` 是跨 Bean 调用，天然走代理；切勿在同一个类内部
   直接调用本类的 `@Async` 方法，那样会退化成同步执行。
5. **把切面范围随意扩大**: 切面收窄到 AdminController 是有意为之——审计关心的是越权，
   不是用户改自己的数据。如果新接口需要审计，优先考虑它是否属于"动他人数据"的范畴。
