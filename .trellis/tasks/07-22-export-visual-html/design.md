# Design — 导出可视化 HTML 页面

> 配套 `prd.md`。本文件定边界、契约、数据流、取舍；`implement.md` 定执行步骤。

---

## 1. 架构总览

```
┌──────────────┐   GET /api/bookmarks/export-data (Result<JSON>, JWT)
│  后端         │ ◀──────────────────────────────────────────────┐
│  BookmarkExport│                                                │
│  Service      │   返回 ExportDataResponse: { folders树+bookmarks }│
│  (重构)       │                                                │
└──────────────┘                                                │
                                                                │
┌──────────────┐  调 export-data 取数                            │
│  前端导出     │ ────────────────────────────────────────────────┘
│  BookmarkGrid │
│  handleExport │  ① renderExportHtml(treeData) ──► htmlString（纯函数）
│  Confirm      │  ② new Blob([html], {type:'text/html'}) + objectURL 下载
└──────────────┘
                                                                │
┌──────────────┐  同一个 renderExportHtml()                       │
│  预览页(开发)  │ ─────────────────────────────────────────────────┘
│  /dev/export- │  用 mock 数据实时渲染，HMR 秒级调样式
│  preview      │
└──────────────┘
```

**核心原则**：`renderExportHtml(treeData)` 是唯一模板函数，预览页和真实下载共用，绝不写两份 HTML 生成逻辑。

---

## 2. 后端设计

### 2.1 新增 DTO：`ExportDataResponse`

文件：`src/main/java/com/hlaia/dto/response/ExportDataResponse.java`（新建）

```java
@Data
public class ExportDataResponse {
    private String exportedAt;          // 导出时间 ISO 字符串，前端放页头
    private List<ExportFolderNode> folders; // 根文件夹列表（已含嵌套 children + 直属 bookmarks）
}
```

为避免在 `FolderTreeResponse`（侧栏树用的，只有 count）上塞书签详情污染既有契约，**新建两个导出专用 DTO**：

```java
@Data
public class ExportFolderNode {
    private Long id;
    private String name;
    private String icon;                // emoji/图标名
    private Integer sortOrder;
    private List<ExportFolderNode> children;   // 子文件夹（嵌套，无限层）
    private List<ExportBookmarkNode> bookmarks; // 该文件夹直属书签
}

@Data
public class ExportBookmarkNode {
    private String title;
    private String url;
    private String iconUrl;             // base64 data URI，直接喂给 <img src>
    private String description;         // 可空
    private Integer sortOrder;
}
```

**为什么新建 DTO 而不扩 FolderTreeResponse？**
`FolderTreeResponse` 是侧栏树契约，前端到处用，给它加 `bookmarks` 字段会：a) 让每个 `GET /folders/tree` 响应体暴涨（全量书签）；b) 破坏既有调用方语义。导出是一次性全量快照，语义不同，独立 DTO 更干净。

### 2.2 重构 `BookmarkExportService`

保留它已验证的 2 次查询加载逻辑（`folderMapper` + `bookmarkMapper`，按 sortOrder 升序 + groupBy），但把「拼 Netscape HTML」换成「组装 ExportDataResponse」：

- 删除：`renderFolder` / `renderBookmark` / `escapeHtml` / `escapeAttr`（Netscape 专用，不再需要）。
- 新增方法：`ExportDataResponse getExportData(Long userId)`。
- 复用：第 81-104 行的两查询 + 两个 groupBy Map（`bookmarksByFolder`、`foldersByParent`），只是递归时往 DTO 里填而不是拼字符串。
- 根判定不变：`parentId == null` 为根。

### 2.3 新增 / 改造 Controller 端点

**新增**：
```java
@GetMapping("/bookmarks/export-data")
@Operation(summary = "Export all bookmarks as visual HTML data (JSON)")
public Result<ExportDataResponse> getExportData(@AuthenticationPrincipal Long userId) {
    return Result.success(bookmarkExportService.getExportData(userId));
}
```
走标准 `Result<T>` JSON 包裹，与项目其它接口一致。JWT 保护（已有 security 配置覆盖）。

**旧端点 `GET /api/bookmarks/export`（Netscape）处理**：直接**删除**。
- 前端 `exportBookmarks()` API 函数改为调 export-data JSON 接口（见 3.1）。
- 旧 `ResponseEntity<byte[]>` + `Content-Disposition` 模式不再需要——下载的 HTML 由前端 Blob 生成。
- 删 `BookmarkController` 里 `exportBookmarks` 方法及其 import（`HttpHeaders`/`MediaType`/`ResponseEntity`/`DateTimeFormatter`/`LocalDateTime` 若无其它使用则清理）。

> 备选：保留 export 端点做 302 转发。**不采纳**——会让前端下载流程分裂成两套，且留死代码。一刀切删干净。

### 2.4 不动
- `BookmarkImportService`、`importBookmarks` API、导入对话框——完全不动。
- `ErrorCode.EXPORT_FAILED` 保留（新方法仍可能抛）。

---

## 3. 前端设计

### 3.1 API 层

`frontend/src/api/bookmark.js`：
- 删除旧 `exportBookmarks()`（blob 版）。
- 新增 `getExportData()`：
```js
export function getExportData() {
  return request.get('/bookmarks/export-data')
}
```
返回经拦截器解包后 `res.data` 即 `ExportDataResponse`。

### 3.2 模板函数（核心）

新建 `frontend/src/utils/exportHtml.js`（纯函数，无 Vue 依赖，便于在预览页和导出流程共用）：

```js
/**
 * 把导出数据渲染成自包含可视化 HTML 字符串
 * @param {Object} data - ExportDataResponse { exportedAt, folders: ExportFolderNode[] }
 * @returns {string} 完整 HTML 文档（内联 CSS + JS，单文件可用）
 */
export function renderExportHtml(data) { ... }
```

**输出 HTML 结构**：
```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>我的书签</title>
  <style>/* 内联：配色用 Warm Minimal Light，主色 #4A7FC7 */</style>
</head>
<body>
  <header class="page-header">
    <h1>我的书签</h1>
    <p class="meta">N 个文件夹 · M 个书签 · 导出于 yyyy-MM-dd</p>
    <input id="search" type="search" placeholder="搜索书签标题或网址…">
  </header>
  <main id="content">
    <!-- 递归渲染根文件夹区块；每个区块含文件夹标题 + 书签卡片网格 + 子文件夹区块 -->
  </main>
  <div id="empty-state" hidden>没有匹配的书签</div>
  <script>/* 内联：搜索过滤逻辑 */</script>
</body>
</html>
```

**关键实现点**：
- 递归渲染文件夹：`renderFolderNode(node, depth)` → 返回一段 HTML，含 `<section class="folder" data-depth="d">` + 标题 + 直属书签网格 + 递归 children。
- 书签卡片：`<a class="card" href="URL" target="_blank" rel="noopener">` 内含 `<img class="favicon" src="iconUrl">` + `<span class="title">` + `<span class="url">`。无 icon 时给占位（首字母或默认图标）。
- 搜索 JS：给每个 `.card` 加 `data-title` / `data-url`（小写化）；输入事件遍历 `.card` 显隐；某区块全隐藏时隐藏其 section；全无匹配显示 `#empty-state`。
- 转义：渲染 title/url/description 时 HTML 转义（`& < > "`），防注入/破坏结构。写个小 `escapeHtml` 工具即可。

**可测试性**：`renderExportHtml` 是纯函数，可单测（给定 mock 数据 → 断言 HTML 含某文件夹名/卡片数）。

### 3.3 导出流程改造（`BookmarkGrid.vue`）

`handleExportConfirm`（约 933-970 行）改为：
```js
const data = await getExportData()          // res.data = ExportDataResponse
const html = renderExportHtml(data)
const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
// 复用现有 objectURL + <a> click + revoke 下载模式
const filename = `bookmarks_${formatTimestamp()}.html`
```
- 文件名生成从前端做（原先是后端 Content-Disposition，现在前端自己拼同款 `yyyyMMdd_HHmmss`）。
- `exportStats`（文件夹/书签数）逻辑不变，仍走 `folderStore.folderTree`；或直接用 export-data 返回的数据算——**保持用 exportStats**，对话框打开时就有数，不必先请求。
- loading / toast 不变。

### 3.4 实时预览页（开发辅助，非生产路由）

新增 `frontend/src/views/dev/ExportPreview.vue` + 一个临时 dev 路由（如 `/dev-export`，仅 dev 环境，不进正式菜单）：
- 内嵌一个 `<iframe>` 或直接 `v-html` 把 `renderExportHtml(mockData)` 渲染出来。
- mock 数据放 `frontend/src/utils/exportMock.js`（几层嵌套文件夹 + 带图标书签 + 空文件夹，覆盖典型形态）。
- **改 exportHtml.js 的 CSS/结构 → Vite HMR → 浏览器即时刷新**，达成「实时看页面设计、实时调整」。
- 预览页和生产导出用同一个 `renderExportHtml`，所见即所得。

> 预览页是临时脚手架，设计敲定后可删（或保留作开发参考，用 dev-only 路由守卫隔离）。

### 3.5 i18n 调整
- `bookmarks.exportDialog.summary` 原文「将导出全部 {folders} 个文件夹、{bookmarks} 个书签（含图标）」——「含图标」仍准确（卡片含 favicon），保留。
- 无需新增 key（下载流程内部不面向用户文案）。

---

## 4. 视觉设计 Token（Warm Minimal Light，对齐项目主题）

> 本节为定稿态。设计经一次迭代收敛，记录最终取向与关键决策。

**设计取向**：安静、克制的「索引式」版面，单色系（仅项目主色 `#4A7FC7` + 中性灰），拒绝彩色拼盘。不用 emoji——文件夹层级靠缩进 + 引导线 + 左侧目录(TOC) 表达。

| Token | 值 | 说明 |
|---|---|---|
| 主色 | `#4A7FC7` | 唯一彩色，仅用于根标记线/搜索聚焦/hover |
| 主色软底 | `rgba(74,127,199,.10)` | TOC hover、搜索框 focus 光晕 |
| 页面背景 | `#F6F7F9` | 中性灰 |
| 卡片/区块 | `#FFFFFF` | |
| 分隔线 | `#E6E9EE` / `#EEF1F5`（软） | |
| 标题色 | `#1F2A37` | |
| 正文 | `#4B5563` | |
| 次要（网址） | `#94A0B0` | |
| 圆角 | 卡片 `8px` | |
| 卡片网格 | `repeat(auto-fill, minmax(140px, 1fr))` | 关键：见下方「卡片列数」 |
| favicon | `26px`，无图标用首字母占位（主色底白字） | |

**布局（两栏，铺满全宽）**：
- 顶部 sticky 头：标题 + 统计数 + 右侧搜索框（SVG 放大镜图标）。
- 左栏 TOC（`190px`，sticky）：一眼看清文件夹树，点选锚点跳转；窄屏（≤860px）折叠到顶部横排。
- 右栏正文：根文件夹是带下划线的区块标题（短竖线标记）；子文件夹用**左侧引导线 + 缩进 + 圆点标记**嵌套，层级一眼可辨。
- 内容区 `width:100%`（不设 max-width），铺满全屏。

**卡片列数（关键实现细节）**：
- 网格规则 `repeat(auto-fill, minmax(140px, 1fr))`，1920px 下正文网格实测约 11 列、1280px 约 10 列。
- **坑**：flex 卡片默认 `min-width:auto`，会被 nowrap 文本撑开，导致轨道列数上不去。必须给 `.card` 设 `min-width:0`，卡片才能真正收缩到 140px、`auto-fill` 才会创建足够多的轨道。这是本设计能否「一排多列」的命门。

**安全**：动态文本全走 `escapeHtml`；URL 只允许 http/https 链接化（`safeLink` 白名单），`javascript:` 等危险 scheme 降级为不可点击的 `div`。

---

## 5. 数据流总结

```
用户点「确认导出」
  → getExportData() → GET /api/bookmarks/export-data (JWT)
  → BookmarkExportService.getExportData(userId)
      → 2 次查询(复用现有逻辑) + groupBy + 递归组装 ExportDataResponse
  → Result.success(data) JSON 返回
  → 前端 renderExportHtml(data) → htmlString
  → Blob + objectURL + <a>.click() → 下载 bookmarks_<ts>.html
  → 双击打开：单文件离线渲染 + 内置搜索
```

---

## 6. 兼容性与取舍

| 决策 | 取舍 |
|---|---|
| 删 Netscape 导出端点 | 失去「本系统导出→浏览器导入」能力。但浏览器导入场景本就由 import 侧（读 Netscape）覆盖，用户从浏览器导出再导入本系统仍可用。可接受。 |
| HTML 在前端生成 | 后端变薄（只出 JSON）；改样式零后端重启，预览友好。代价：导出文件大小略增（内联 JS/CSS），但单文件几千行量级，可忽略。 |
| 新建 ExportDataResponse 而非扩 FolderTreeResponse | 多两个 DTO 类，但保护既有侧栏树契约不被全量书签污染。值得。 |
| 导出仍是全量 | 同前版 PRD R2，不做按文件夹/勾选导出。 |

---

## 7. 回滚

- 全部改动集中在：1 个新 DTO 文件 + `BookmarkExportService` 重构 + `BookmarkController` 增删端点 + 前端 `exportHtml.js`/`exportMock.js`/`export-bookmark.js` 改动 + `BookmarkGrid.vue` 改下载流程 + dev 预览页。
- 回滚点：每个文件改动独立，`git revert` 单次提交即可还原。无 DB 迁移、无配置变更，回滚零风险。
