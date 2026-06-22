# 操作日志收窄到管理员审计

## Goal

把当前"记录所有 Controller 操作"的噪音型日志，收窄为"只记录管理员的越权写操作"，让 `operation_log` 表回归真正的审计价值——记录"谁动了他人的数据"，而不是淹没在用户改自己书签的噪音里。

## Background

当前 `OperationLogAspect` 拦截 `com.hlaia.controller.*` 下**所有** Controller 方法（仅排除 AuthController），导致：

1. **日志噪音**：用户每改一次书签、拖一下排序都记一条。开发机上"一输出就是一大片"。
2. **无审计价值**：用户改自己的数据本就不需要审计——审计关心的是**越权操作**。
3. **detail 字段永远是 null**：只记 `BookmarkController.createBookmark` 这种方法名，没有业务上下文。
4. **无查询入口**：`operation_log` 表没有任何 API 或后台页面消费它，记下来没人看。
5. **每请求一次 DB 写**：即使是异步的，也占数据库连接和 I/O。

真正的审计需求是：**管理员封禁/解封用户、删除他人文件夹这类越权操作**，需要有据可查（万一误操作或账号被盗）。

## Requirements

### 功能需求

- **R1 切面范围收窄**：只拦截 `AdminController` 下的方法，不再拦截 BookmarkController / FolderController / UserController / StagingController / ExtensionController / FaviconController / SearchController。
- **R2 只记写操作**：在 AdminController 内部，进一步过滤——只记 `@PostMapping` / `@PutMapping` / `@DeleteMapping` 标注的方法；`@GetMapping`（listUsers、getUserFolders）不记。
  - 被记录的 3 个方法：`banUser`、`unbanUser`、`deleteFolder`
- **R3 detail 字段填充业务上下文**：不再永远是 null。根据方法生成有意义的描述：
  - `banUser(5)` → detail = `"banned user 5"`
  - `unbanUser(5)` → detail = `"unbanned user 5"`
  - `deleteFolder(12)` → detail = `"deleted folder 12"`
  - 格式：英文 + 目标 ID，简洁可检索。
- **R4 action 字段语义化**：不再用方法名（`banUser`），改用统一的动作常量：
  - `BAN_USER` / `UNBAN_USER` / `DELETE_FOLDER`
  - 大写下划线，便于 SQL `WHERE action = 'BAN_USER'` 检索。
- **R5 target 字段保留**：仍记录 `AdminController.banUser` 这种类名.方法名，保留定位信息。
- **R6 清空老数据**：通过 Flyway 迁移 `TRUNCATE TABLE operation_log`，清掉之前积累的噪音数据，新方案从空表开始。

### 工程需求

- **R7 日志量大幅下降**：原来每次用户操作都记，现在只在管理员执行 3 个写操作时记。
- **R8 不破坏现有契约**：Controller 接口、数据库表结构（除清空数据外）不变。
- **R9 容错不变**：日志写入失败仍被 catch 吞掉，不影响管理员业务操作。

## Acceptance Criteria

- [ ] AC1 `OperationLogAspect` 的切点表达式只匹配 `AdminController`（不再匹配其他 Controller）。
- [ ] AC2 切面内通过 HTTP method 过滤，GET 请求（listUsers、getUserFolders）不产生日志。
- [ ] AC3 管理员调用 `PUT /api/admin/users/5/ban` 后，`operation_log` 表新增一条：
  - `action = 'BAN_USER'`、`target = 'AdminController.banUser'`、`detail = 'banned user 5'`、`user_id = <管理员ID>`
- [ ] AC4 管理员调用 `PUT /api/admin/users/5/unban` 后：`action = 'UNBAN_USER'`、`detail = 'unbanned user 5'`
- [ ] AC5 管理员调用 `DELETE /api/admin/folders/12` 后：`action = 'DELETE_FOLDER'`、`detail = 'deleted folder 12'`
- [ ] AC6 管理员调用 `GET /api/admin/users` 或 `GET /api/admin/users/5/folders/tree` 后，**无新日志**。
- [ ] AC7 普通用户调用任何 `/api/bookmarks` / `/api/folders` / `/api/staging` 接口后，**无新日志**。
- [ ] AC8 Flyway 迁移脚本 `V6__truncate_operation_log.sql` 清空 `operation_log` 表，应用启动后迁移成功。
- [ ] AC9 `mvn compile` 通过。
- [ ] AC10 日志写入失败（如 DB 不可用）不影响管理员业务操作，仍返回 200。

## Out of Scope

- 不新增"查询操作日志"的 API（`GET /api/admin/operation-logs`）——那是另一个任务。本次只做"收窄"。
- 不改 `operation_log` 表结构（不加字段、不改类型）。
- 不改 `OperationLog` 实体或 `OperationLogMapper`。
- 不引入自定义注解 `@OperationLog`（决策已定为"限定 AdminController"方案，见 design.md）。
