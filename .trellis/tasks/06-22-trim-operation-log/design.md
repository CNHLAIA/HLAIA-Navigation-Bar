# Design — 操作日志收窄到管理员审计

> 配套 `prd.md`。本任务范围明确，design 只讲关键技术决策与改动清单，不写 implement.md。

## 1. 方案选型回顾

| 方案 | 工作量 | 灵活性 | 决策 |
|------|--------|--------|------|
| 自定义注解 `@OperationLog` + SpEL 取参数 | 大 | 高，可任意标注方法 + 任意 detail 模板 | ❌ 过度设计 |
| **限定 AdminController + 切面内反射取参数** | 小 | 满足当前 3 个方法的需求 | ✅ 采用 |
| 删 AOP，每个方法手写 `operationLogService.record(...)` | 中 | 0 灵活性，侵入业务代码 | ❌ 失去 AOP 优势 |

选定"限定 AdminController"方案的理由：当前需要审计的方法只有 3 个，且全部集中在 AdminController 内，切面内通过方法名 switch 生成 detail 完全够用。引入注解 + SpEL 是为未来扩展买期权，但 YAGNI——真要扩展时再上注解也不迟。

## 2. 切面改造（`OperationLogAspect`）

### 2.1 切点表达式

```java
// 改前：拦截 controller 包下所有方法（排除 AuthController）
@Around("execution(* com.hlaia.controller.*.*(..)) && " +
        "!execution(* com.hlaia.controller.AuthController.*(..))")

// 改后：只拦截 AdminController
@Around("execution(* com.hlaia.controller.AdminController.*(..))")
```

不再需要 `&& !execution(... AuthController ...)` 排除子句——AdminController 本身就不含 AuthController。

### 2.2 切面体内：过滤 GET 请求

切点匹配 AdminController 的所有 5 个方法，但 GET（listUsers、getUserFolders）不需要审计。有两种过滤方式：

- **方案 a**：切点表达式加 `&& @annotation(PostMapping) || @annotation(PutMapping) || @annotation(DeleteMapping)`
  - 问题：Spring AOP 的 `@annotation` 切点不能直接 OR 多个注解，写起来绕。
- **方案 b**（采用）：切面体内通过反射检查方法是否标注了 `@GetMapping`，若是则直接 `return result`，跳过日志记录。

```java
Object result = joinPoint.proceed();

Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

// 只审计写操作（POST/PUT/DELETE），GET（listUsers / getUserFolders）跳过
if (method.isAnnotationPresent(GetMapping.class)) {
    return result;
}

// ... 生成 action / detail 并记录
```

### 2.3 action / detail 生成（方法名 switch）

切面内对当前方法名做 switch，生成语义化的 action 常量和带业务上下文的 detail：

```java
String methodName = method.getName();
String action;
String detail;

switch (methodName) {
    case "banUser" -> {
        action = "BAN_USER";
        detail = "banned user " + extractFirstArg(joinPoint);
    }
    case "unbanUser" -> {
        action = "UNBAN_USER";
        detail = "unbanned user " + extractFirstArg(joinPoint);
    }
    case "deleteFolder" -> {
        action = "DELETE_FOLDER";
        detail = "deleted folder " + extractFirstArg(joinPoint);
    }
    default -> {
        // 未来新增的 AdminController 写操作方法，走默认逻辑（方法名 + 无 detail）
        // 避免新方法被切面拦截后 action 为 null
        action = methodName.toUpperCase();
        detail = null;
    }
}
```

`extractFirstArg(joinPoint)`：3 个目标方法的第一个参数都是 `@PathVariable`（userId 或 folderId），通过 `joinPoint.getArgs()[0]` 取值。

### 2.4 target 字段保留

```java
String target = signature.getDeclaringType().getSimpleName() + "." + methodName;
// 如 "AdminController.banUser"
```

保留定位信息，便于从日志反查代码位置。

## 3. OperationLogService 改造

`record` 方法签名扩展，接收 detail：

```java
// 改前
@Async void record(Long userId, String action, String target)

// 改后
@Async void record(Long userId, String action, String target, String detail)
```

实体映射相应补充 `logEntry.setDetail(detail);`。

## 4. Flyway 迁移：清空老数据

新增 `src/main/resources/db/migration/V6__truncate_operation_log.sql`：

```sql
-- 清空历史积累的噪音日志（收窄方案前的全量 Controller 记录）
-- TRUNCATE 比 DELETE FROM 快，且重置 AUTO_INCREMENT
TRUNCATE TABLE operation_log;
```

**为什么用 TRUNCATE 而不是 DELETE FROM**：
- TRUNCATE 是 DDL，不记录逐行事务日志，清空大表远快于 DELETE
- TRUNCATE 自动重置 AUTO_INCREMENT 计数器，新日志从 id=1 开始
- `operation_log` 不参与外键关联，TRUNCATE 的外键限制不影响

**为什么不在迁移里 DROP 再 CREATE**：表结构不变，无需重建。

## 5. 改动清单

### 改造（非删除）
- `src/main/java/com/hlaia/aspect/OperationLogAspect.java`
  - 切点表达式收窄到 AdminController
  - 切面体内加 GET 过滤
  - 加方法名 switch 生成 action/detail
  - 调用 `record` 时多传 detail 参数
  - 类/方法注释更新（说明收窄理由）
- `src/main/java/com/hlaia/service/OperationLogService.java`
  - `record` 方法签名加 `String detail` 参数
  - 实体映射补充 `setDetail(detail)`

### 新增
- `src/main/resources/db/migration/V6__truncate_operation_log.sql`

### 文档（Phase 3.3）
- `.trellis/spec/backend/logging-guidelines.md`：操作日志章节改为"只审计管理员越权操作"，detail/action 示例更新

### 不变
- `OperationLog` 实体、`OperationLogMapper`、`operation_log` 表结构
- 所有 Controller
- `RateLimitAspect`、其他 Service

## 6. 兼容性与风险

| 风险 | 评估 | 缓解 |
|------|------|------|
| 切点表达式写错导致拦截范围异常 | 低 | AC1/AC6/AC7 覆盖验证 |
| switch 漏掉新增的 AdminController 写方法 | 中 | default 分支兜底（方法名大写 + null detail），不会 NPE |
| `joinPoint.getArgs()[0]` 在无参方法上越界 | 中 | 已用 `isAnnotationPresent(GetMapping.class)` 过滤；3 个目标方法都有 @PathVariable 参数；default 分支的 null detail 不依赖 args | 
| TRUNCATE 在 Flyway 重复执行环境出问题 | 低 | Flyway 用 checksum 防重复；本迁移只跑一次 |
| 老数据被清空不可恢复 | 已接受 | 用户明确决策"清空老数据"；当前数据本就无价值 |

## 7. 验证策略

- **编译**：`mvn compile`（AC9）
- **静态检查**：确认切点表达式只匹配 AdminController
- **功能手测**（部署或本地起 MySQL）：
  1. 管理员登录 → 调 ban/unban/deleteFolder → 查 `operation_log` 表有对应记录，action/detail 正确（AC3-AC5）
  2. 管理员调 listUsers/getUserFolders → 无新日志（AC6）
  3. 普通用户调书签/文件夹接口 → 无新日志（AC7）
  4. 启动时观察 Flyway 迁移日志确认 V6 执行成功（AC8）
