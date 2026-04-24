# Favicon 自动获取改进 + 书签移动功能

## 概述

两个独立功能改进：
1. 改进后端 Favicon 获取成功率（HTML 解析 + 多路径回退）
2. 书签支持移动到其他文件夹（单个 + 批量）

## 功能一：Favicon 获取改进

### 当前问题

`IconFetchConsumer` 仅通过 HEAD 请求 `domain/favicon.ico`，成功率低。很多网站的 favicon 声明在 HTML `<link>` 标签中，路径不一定为 `/favicon.ico`。

### 改进方案

**依赖**：pom.xml 新增 `org.jsoup:jsoup:1.18.3`

**IconFetchConsumer 改造流程**：

1. 用 Jsoup 获取网页 HTML（GET 请求，设置 User-Agent）
2. 按优先级查找 favicon URL：
   - `<link rel="icon">` 的 href
   - `<link rel="shortcut icon">` 的 href
   - `<link rel="apple-touch-icon">` 的 href（兜底）
3. 若 HTML 中未找到，回退到 `domain + "/favicon.ico"`（HEAD 请求验证）
4. 将 relative href 转为 absolute URL
5. 验证 URL 可访问（HEAD 返回 200）后更新 `bookmark.iconUrl`
6. 全部失败则保持 null，前端 Google Favicon 服务兜底

**修改文件**：
- `pom.xml`：新增 jsoup 依赖
- `IconFetchConsumer.java`：重写 favicon 获取逻辑

**前端无变更**：前端 `BookmarkCard.vue` 已有 Google Favicon 服务兜底逻辑，保持不变。

## 功能二：书签移动

### 后端

**新增文件**：

- `src/main/java/com/hlaia/dto/request/BookmarkMoveRequest.java`
  - `bookmarkIds: List<Long>` — @NotEmpty
  - `targetFolderId: Long` — @NotNull

**新增端点**：

```
PUT /api/bookmarks/move
```

**BookmarkService.moveBookmarks(userId, request)** 逻辑：
1. 查询所有 bookmarkId，验证全部属于当前用户，不存在则抛 BOOKMARK_NOT_FOUND
2. 查询 targetFolderId，验证存在且属于当前用户，不存在则抛 FOLDER_NOT_FOUND
3. 计算目标文件夹当前最大 sortOrder
4. 批量更新：设置 folderId = targetFolderId，sortOrder 从 max+1 递增
5. 返回成功

**修改文件**：
- `BookmarkController.java`：新增 moveBookmarks 端点
- `BookmarkService.java`：新增 moveBookmarks 方法

### 前端

**新增文件**：

- `frontend/src/components/FolderPickerDialog.vue`
  - Props: `visible`, `excludeFolderId`（排除当前文件夹）
  - 展示文件夹树（树形结构，支持展开/折叠）
  - 单击选中文件夹，点击确认触发 `@confirm` 事件
  - 使用 Element Plus 的 `el-tree` 组件

**修改文件**：

- `BookmarkGrid.vue`：
  - 右键菜单增加"移动到..."选项
  - 多选状态下右键增加"移动选中到..."选项
  - 引入 FolderPickerDialog，确认后调用 store 的 moveBookmarks

- `stores/bookmark.js`：
  - 新增 `moveBookmarks(bookmarkIds, targetFolderId)` action
  - 调用 `PUT /api/bookmarks/move`
  - 成功后刷新当前书签列表
  - 同时刷新文件夹树（bookmarkCount 变化）

### 交互流程

**单个移动**：
1. 用户右键书签卡片 → 弹出菜单包含"移动到..."
2. 点击"移动到..." → 弹出 FolderPickerDialog
3. 选择目标文件夹 → 点击确认
4. 书签移动完成，当前视图刷新

**批量移动**：
1. 用户 Ctrl+Click 多选书签
2. 右键任意已选书签 → 弹出菜单包含"移动选中到..."
3. 点击后弹出 FolderPickerDialog
4. 选择目标文件夹 → 确认
5. 所有选中书签移动完成
