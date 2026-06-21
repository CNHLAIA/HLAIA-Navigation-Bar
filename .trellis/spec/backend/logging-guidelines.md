# 日志规范

> 本项目的日志记录方式、日志级别使用和操作日志机制。

---

## 概述

本项目有两种日志机制：

1. **应用日志（SLF4J）**: 通过 `@Slf4j` 注解在代码中直接输出日志，用于调试和问题排查
2. **操作日志（AOP + Kafka + 数据库）**: 通过 `OperationLogAspect` 切面自动记录用户操作，用于安全审计

---

## 应用日志（SLF4J）

### 使用方式

通过 Lombok 的 `@Slf4j` 注解自动生成 `log` 对象：

```java
@Slf4j
@Component
public class KafkaProducer {
    public void sendIconFetchTask(Long bookmarkId, String url) {
        // ...
        log.info("Sent icon fetch task for bookmark {}", bookmarkId);
    }
}
```

### 使用 @Slf4j 的文件

当前项目中使用 `@Slf4j` 的类：
- `src/main/java/com/hlaia/kafka/KafkaProducer.java` -- Kafka 消息发送日志
- `src/main/java/com/hlaia/kafka/OperationLogConsumer.java` -- 消费者处理日志

### 日志级别使用规范

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| `log.info()` | 关键业务操作的成功记录 | Kafka 消息发送成功、消费成功 |
| `log.error()` | 操作失败但不影响系统运行 | 消费者处理消息失败 |
| `log.warn()` | 异常但可恢复的情况 | 当前代码中未使用，但 `OperationLogAspect` 的 catch 块建议添加 |
| `log.debug()` | 开发调试信息 | 当前代码中未使用 |

### 日志格式约定

- 使用 `{}` 占位符而非字符串拼接：`log.info("Sent task for bookmark {}", bookmarkId)`
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
kafkaProducer.sendOperationLog(userId, action, target)
    ↓ 异步发送到 Kafka
Kafka Topic "operation-log"
    ↓ 消费者异步消费
OperationLogConsumer.consume()
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
        // 通过 Kafka 异步发送日志
        kafkaProducer.sendOperationLog(userId, action, target);
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

### 日志写入数据库

参考 `src/main/java/com/hlaia/kafka/OperationLogConsumer.java`：

- 消费 Kafka Topic `"operation-log"`，groupId 为 `"hlaia-nav"`
- 解析 JSON 消息，创建 `OperationLog` 实体，插入数据库
- `createdAt` 使用服务器当前时间（而非消息发送时间）
- 消费者异常只记录日志不向上抛出

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

在 `OperationLogConsumer` 中体现为：
- 消费者异常只记录 `log.error()`，不向上抛出
- 单条消息处理失败不影响后续消息的消费

---

## Kafka 消息日志

参考 `src/main/java/com/hlaia/kafka/KafkaProducer.java`，消息发送后使用 `log.info` 记录：

```java
public void sendIconFetchTask(Long bookmarkId, String url) {
    String message = "{\"bookmarkId\":" + bookmarkId + ",\"url\":\"" + url + "\"}";
    kafkaTemplate.send("bookmark-icon-fetch", bookmarkId.toString(), message);
    log.info("Sent icon fetch task for bookmark {}", bookmarkId);
}
```

Kafka 消息格式使用手动拼接的 JSON 字符串（非 JSON 库），因为消息结构简单，不需要额外依赖。

---

## 常见错误

1. **在 Controller/Service 中手动写操作日志代码**: 应该让 AOP 切面自动处理
2. **日志中使用字符串拼接而非占位符**: `log.info("id=" + id)` 应改为 `log.info("id={}", id)`
3. **在 catch 块中不记录日志就吞掉异常**: `OperationLogAspect` 的 catch 块目前是空的，建议添加 `log.warn()`
4. **记录敏感信息**: 绝不在日志中输出密码、Token 等敏感数据
