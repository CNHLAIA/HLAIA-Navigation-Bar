# 导出可视化 HTML 页面

## Goal

把书签导出从「机器可读但人眼难看」的 Netscape HTML，替换为**自包含、可离线打开、带搜索的可视化 HTML 页面**——卡片网格（导航站风格），按文件夹分组展示，每个书签带图标 / 标题 / 网址。让用户导出后打开就能直观浏览自己的书签库。

## Background（已确认事实）

### 当前导出实现（将被替换）
- 后端：`BookmarkExportService.exportBookmarks(userId)`（`src/main/java/com/hlaia/service/BookmarkExportService.java:79-135`），用 2 次查询拿到「该用户全部文件夹 + 全部书签」，按 sortOrder 排序，字符串拼接成 Netscape Bookmark HTML。
- 控制器：`BookmarkController.exportBookmarks`（`BookmarkController.java:345-357`），`GET /api/bookmarks/export`，返回 `ResponseEntity<byte[]>`（**不走 Result JSON 包裹**），带 `Content-Disposition: attachment`。
- 前端下载：`BookmarkGrid.vue` 的 `handleExportConfirm`（约 933-970 行），blob 接收 → 解析文件名 → 触发下载；导出对话框在 324-347 行，统计 `exportStats`（470-485 行）从 `folderStore.folderTree` 递归累加 `bookmarkCount`。
- API 封装：`frontend/src/api/bookmark.js:109-114`，`exportBookmarks()` 用 `responseType:'blob'`。
- i18n：`frontend/src/i18n/zh-CN.js:156-163`、`en-US.js:156` 的 `bookmarks.exportDialog.*`。

### 数据可用性（决定架构的关键事实）
- **前端内存里没有「全部书签详情」**：`folderStore.folderTree`（`stores/folder.js:28`，来自 `FolderTreeResponse`）只有 `bookmarkCount` 数字，不含书签 title/url/iconUrl。`bookmarkStore`（`stores/bookmark.js:77-112`）只懒加载用户打开过的文件夹。
- **后端已有高效全量加载路径**：`BookmarkExportService` 的 2 次查询（`folderMapper` + `bookmarkMapper`，各按 sortOrder 升序）正是可视化所需数据的来源。
- 因此采用「后端加 JSON 接口、前端生成 HTML」方案。

### 用户决策（本轮 brainstorm 已确认）
1. **格式策略**：替换为可视化格式。导出端不再产出 Netscape HTML；导入侧 `BookmarkImportService` 保持不变（仍可读 Netscape，供从浏览器导入）。
2. **视觉风格**：卡片网格（导航站风格）。
3. **搜索**：导出的 HTML 内嵌纯前端 JS，支持按标题/网址实时搜索过滤；文件仍单文件离线可用。

## Requirements

### R1 · 导出格式替换
- 导出按钮触发的下载文件，由 Netscape HTML 改为**自包含可视化 HTML**。
- 导出文件**单文件可用**：CSS 内联在 `<style>`，JS 内联在 `<script>`，图标用数据库已有的 base64 data URI，无任何外部依赖、无需联网。
- 文件名沿用 `bookmarks_<yyyyMMdd_HHmmss>.html` 命名规则。

### R2 · 后端新增「全量书签树」JSON 接口
- 新增 `GET /api/bookmarks/export-data`（或等价路径），返回 `Result<T>` 包裹的 JSON，一次性给出：完整文件夹树（含嵌套 children）+ 每个文件夹直属书签（title/url/iconUrl/description/sortOrder）。
- 复用现有 2 次查询的加载逻辑，不重复造轮子。
- JWT 鉴权，数据隔离（只能拿自己的）。
- 该接口供前端导出时取数；不在此接口做 HTML 渲染。

### R3 · 前端 HTML 模板生成
- 在前端实现一套纯函数模板：`(treeData) => htmlString`。
- 预览页与最终下载文件**共用同一套模板**，避免两份代码。
- 导出对话框确认后：调用 export-data 接口 → 模板渲染 → 触发下载（Blob + objectURL，复用现有下载模式）。

### R4 · 卡片网格视觉设计（导航站风格）
- 页面整体：顶部标题区（如「我的书签」+ 统计数），顶部固定搜索框。
- 内容按根文件夹分区，每个分区有文件夹名标题；嵌套子文件夹以可识别的层级呈现（缩进或子分区）。
- 每个书签是一张卡片：图标（favicon）+ 标题 + 网址（可截断）；点击卡片在新标签打开链接。
- 空文件夹、空书签库都要有合理的空态展示，不能崩。

### R5 · 内置搜索（纯前端）
- 搜索框实时过滤：按书签标题或网址包含匹配；不区分大小写。
- 无结果时显示空态提示。
- 搜索逻辑内嵌在导出 HTML 的 `<script>` 中，离线可用。

### R6 · 导出对话框保持现有交互
- 对话框仍显示文件夹数 / 书签数统计（`exportStats` 复用）。
- 确认按钮 loading 态、成功/失败 toast 不变。
- 文案「含图标」之类若不再准确则同步修正 i18n。

### R7 · 向后兼容与清理
- **删除** `BookmarkExportService` 的 Netscape 渲染逻辑（renderFolder/renderBookmark/escapeHtml/escapeAttr）及 `exportBookmarks` 旧方法——它不再被调用。
- `GET /api/bookmarks/export` 端点：改为内部委托新接口数据 + 由前端生成，或直接移除并由 export-data 取代。设计阶段定夺，原则是不留死端点。
- 导入侧（`BookmarkImportService`、`importBookmarks` API）**完全不动**。

## Acceptance Criteria

- [ ] **AC1**：登录后点导出 → 确认 → 下载到 `bookmarks_<时间戳>.html`，文件名与时间戳格式正确。
- [ ] **AC2**：双击打开下载的 HTML，**离线、无网络**下完整渲染：标题区、搜索框、按文件夹分区的卡片网格。
- [ ] **AC3**：每个书签卡片显示图标（favicon）、标题、网址；点击在新标签打开正确链接。
- [ ] **AC4**：嵌套子文件夹在页面上有清晰的层级体现（根文件夹分区，子文件夹可辨识）。
- [ ] **AC5**：搜索框输入关键词，按标题/网址实时过滤，无结果有空态。
- [ ] **AC6**：空书签账户导出的 HTML 也能正常打开并显示空态，不报错。
- [ ] **AC7**：导出对话框显示正确的文件夹数 / 书签数；确认/取消/loading/toast 行为与现状一致。
- [ ] **AC8**：未登录访问 export-data 接口返回 401。
- [ ] **AC9**：`GET /api/bookmarks/export`（旧）与 `BookmarkExportService` 旧 Netscape 方法已按 R7 处理，无死代码 / 死端点残留。
- [ ] **AC10**：导入功能（Netscape → 数据库）不受影响，回归通过。

## Out of Scope

- 导入侧任何改动（仍读 Netscape）。
- 按文件夹/勾选导出（全量导出，同前）。
- 导出为 JSON / CSV 等其它格式。
- 导出文件的自定义主题 / 暗色模式切换（首版固定一套清爽配色）。
- 操作日志记录（同前，导出不记日志）。

## Open Questions

- 无（架构、风格、搜索、格式策略均已确认）。
