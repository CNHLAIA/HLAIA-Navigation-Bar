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

## 操作日志（AOP 自动记录）

### 架构

操作日志通过 AOP 切面自动记录，流程如下：

```
Controller 方法执行
    ↓ AOP 拦截
OperationLogAspect.logOperation()
    ↓ 先执行目标方法
joinPoint.proceed()
    ↓ 成功后记录日志
operationLogService.record(userId, action, target)
    ↓ @Async 虚拟线程异步执行
OperationLogService.record()
    ↓ 写入数据库
operation_log 表
```

### OperationLogAspect 切面

参考 `src/main/java/com/hlaia/aspect/OperationLogAspect.java`：

- **拦截范围**: `com.hlaia.controller` 包下的所有 Controller 方法，**排除 AuthController**
- **记录时机**: 先执行目标方法（`proceed()`），只有成功返回后才记录日志
- **记录内容**: userId（从 SecurityContext 获取）、action（方法名）、target（类名.方法名）
- **容错设计**: 日志记录失败不影响业务请求（catch 块捕获异常但不向上传播）

```java
@Around("execution(* com.hlaia.controller.*.*(..)) && " +
        "!execution(* com.hlaia.controller.AuthController.*(..))")
public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
    Object result = joinPoint.proceed();  // 先执行业务方法
    try {
        // 提取用户 ID、方法名、类名
        // 通过 OperationLogService 异步写库（@Async 虚拟线程）
        operationLogService.record(userId, action, target);
    } catch (Exception e) {
        // 日志记录失败不影响业务（catch 吞掉异常）
    }
    return result;
}
```

### 排除 AuthController 的原因

1. 登录/注册是高频操作，每次都记录日志会产生大量无用日志
2. 用户未登录时没有 userId，日志信息不完整
3. 登录操作属于认证行为，不属于"业务操作"

### OperationLogService 落库

参考 `src/main/java/com/hlaia/service/OperationLogService.java`：

- 方法 `record(userId, action, target)` 标注 `@Async`，在虚拟线程后台执行
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
- `detail` 和 `ip` 字段当前由切面填充为 null（预留字段）

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
3. **在 catch 块中不记录日志就吞掉异常**: `OperationLogAspect` 的 catch 块目前是空的，建议添加 `log.warn()`
4. **记录敏感信息**: 绝不在日志中输出密码、Token 等敏感数据
5. **`@Async` 方法自调用**: Spring 的 `@Async` 通过代理生效，必须在不同的 Bean 之间调用。
   `OperationLogAspect → OperationLogService` 是跨 Bean 调用，天然走代理；切勿在同一个类内部
   直接调用本类的 `@Async` 方法，那样会退化成同步执行。
