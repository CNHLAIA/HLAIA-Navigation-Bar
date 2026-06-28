# 书签导出功能

## Goal

在导航栏主工作区新增"书签导出"入口，用户点击后可一键导出自己名下的全部书签，生成一份可被主流浏览器（Chrome/Edge/Firefox）直接导入、也能被本系统重新导入的本地文件。

用户价值：
1. **跨平台迁移** —— 导出的文件可直接导入其他浏览器/系统。
2. **灾备备份** —— 系统崩溃或不可用时，本地导出文件可临时作为书签访问入口或恢复源。

## Background

- 项目已存在导入功能 `BookmarkImportService`，解析 **Netscape Bookmark HTML** 格式（Chrome 兼容），使用 Jsoup。
  - 格式约定：`<DL><p>` 列表、`<DT><H3>文件夹名</H3>` 表示文件夹、`<DT><A HREF ICON="data:...">标题</A>` 表示书签，`ICON` 属性存放 base64 favicon data URI。
  - 因此导出格式是导入的逆操作，保证"导出 → 再导入"闭环。
- 文件夹树由 `FolderService.getFolderTree(userId)` 组装：两次查询（全部文件夹 + 全部书签），按 `parent_id=NULL` 识别根，递归挂 `children`。导出复用该加载逻辑。
- `bookmark.icon_url` 为 `TEXT`（V4 迁移放宽），存 base64 data URI，单条可能数 KB。
- 后端目前无任何文件下载/流式响应基础设施；最接近的模板是 `FaviconController` 的 `ResponseEntity<byte[]>` + header builder。
- 安全：新增的 `/api/bookmarks/export` 落在 `SecurityConfig` 的 `anyRequest().authenticated()` 下，JWT 自动保护，用户经 `@AuthenticationPrincipal Long userId` 解析。无需改安全配置。
- 操作日志 AOP 仅覆盖 `AdminController`；导出属普通用户读操作，不在审计范围（且本系统为个人使用，无审计需求）。
- 前端导入入口在 `BookmarkGrid.vue` 的 `.grid-top-actions`（与"刷新""导入书签""新增"按钮同排），导入相关 i18n 在 `bookmarks.importDialog.*`。

## Requirements

- **R1（锁定）**：导出格式为 **Netscape Bookmark HTML**，与现有 `BookmarkImportService` 互逆（确保导出文件能被本系统重新导入，也能被 Chrome/Edge/Firefox 直接导入）。
- **R2**：导出范围 = **当前用户名下全部书签**，按文件夹树组织。不支持按单个文件夹或选中项导出。
- **R3**：导出文件**始终包含 favicon**（`<A>` 标签带 `ICON="data:image/...;base64,..."` 属性），不提供"是否包含图标"勾选项。
- **R4**：**弹窗确认**交互形态。
  - 点击"导出书签"按钮 → 弹出对话框，展示「文件夹数 + 书签数」做预期管理。
  - 用户点"确认导出" → 触发下载，按钮转 loading 态，完成后轻量 toast 反馈。
- **R5**：不写操作日志（个人使用，无审计需求；现有 AOP 也仅覆盖 AdminController）。

## Acceptance Criteria

- [ ] AC1：登录用户在主工作区工具栏点击"导出书签"，弹出确认对话框，正确显示其名下文件夹数与书签数。
- [ ] AC2：点击"确认导出"后，浏览器下载一个名为 `bookmarks_<时间戳>.html`（或类似）的文件。
- [ ] AC3：导出的 HTML 为合法 Netscape Bookmark 格式——文件夹层级正确（根文件夹在顶层，子文件夹递归嵌套），书签归属正确的父文件夹，且保持 `sort_order` 顺序。
- [ ] AC4：导出 HTML 中每个书签的 `<A>` 标签携带 `HREF`、标题文本、`ICON`（base64 data URI，对应数据库 `icon_url`）。
- [ ] AC5：**闭环验证**——将导出的文件通过现有 `POST /api/bookmarks/import` 重新导入到另一用户/空账号，文件夹树与书签可被正确还原（标题、URL、图标、层级）。
- [ ] AC6：导出文件可被 Chrome 浏览器直接导入（"书签 → 导入书签"），书签与文件夹结构可见。
- [ ] AC7：未登录访问导出端点返回 401；用户只能导出自己名下的书签（数据隔离）。
- [ ] AC8：空书签账号导出不报错，生成包含空根列表的合法 HTML。

## Out of Scope

- 按单个文件夹或选中书签导出（可作后续迭代）。
- "是否包含 favicon"的勾选项。
- 导出为其他格式（JSON / CSV 等）。
- 操作日志记录。
- 导入端点的任何改动（导入服务已存在，本次仅做导出侧，AC5 借它做闭环验证）。

## Open Questions

无（核心产品决策已全部收敛）。
