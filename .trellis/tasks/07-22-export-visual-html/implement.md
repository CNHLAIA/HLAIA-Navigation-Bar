# Implement — 导出可视化 HTML 页面

> 执行计划，配套 `prd.md` + `design.md`。按序执行，每个 Gate 必须过。

---

## 执行顺序

### 阶段 A：先把实时预览搭起来（先看效果，再接线）

> 目的：让你能立刻在 http://localhost:5173/ 打开预览页，边看边调设计。后端先不动。

- [ ] **A1** 新建 `frontend/src/utils/exportMock.js`：构造典型 mock 数据（2-3 个根文件夹、含 1-2 层嵌套子文件夹、每个文件夹 3-5 个带 iconUrl 的书签、至少一个空文件夹、一个长标题/长 URL 边界用例）。
- [ ] **A2** 新建 `frontend/src/utils/exportHtml.js`：实现 `renderExportHtml(data)` 纯函数（HTML 结构 + 内联 CSS + 内联搜索 JS + escapeHtml 工具）。第一版配色用 design.md §4 的 token。
- [ ] **A3** 新建 `frontend/src/views/dev/ExportPreview.vue`：用 mock 数据调 `renderExportHtml`，`v-html` 或 iframe 渲染。
- [ ] **A4** 加临时 dev 路由（`/dev-export`），仅 dev 环境可见，不进正式菜单。
- [ ] **Gate A**：浏览器开 `http://localhost:5173/#/dev-export`（或路由模式对应路径），能看到卡片网格 + 搜索框可用。
- [ ] **A5**（迭代）：根据反馈调 CSS / 卡片布局 / 层级表现 / 搜索交互。HMR 秒级生效。

### 阶段 B：后端接线

- [ ] **B1** 新建 `ExportDataResponse.java` + `ExportFolderNode.java` + `ExportBookmarkNode.java`（design.md §2.1）。
- [ ] **B2** 重构 `BookmarkExportService`：保留 2 次查询 + groupBy，新增 `getExportData(Long userId)` 返回 `ExportDataResponse`；删除 `renderFolder`/`renderBookmark`/`escapeHtml`/`escapeAttr` 及旧 `exportBookmarks` 方法。
- [ ] **B3** `BookmarkController`：新增 `GET /bookmarks/export-data`（`Result<ExportDataResponse>`）；删除旧 `exportBookmarks` 方法，清理不再用的 import（`HttpHeaders`/`MediaType`/`ResponseEntity`/`DateTimeFormatter`/`LocalDateTime` 视实际使用）。
- [ ] **Gate B**：后端编译通过 + 启动成功。

### 阶段 C：前端接线（预览 → 真实下载）

- [ ] **C1** `api/bookmark.js`：删旧 `exportBookmarks()`，新增 `getExportData()`。
- [ ] **C2** `BookmarkGrid.vue` 的 `handleExportConfirm`：改调 `getExportData()` → `renderExportHtml(data)` → Blob 下载，文件名前端拼 `bookmarks_<yyyyMMdd_HHmmss>.html`。
- [ ] **C3** 清理 `BookmarkGrid.vue` 里旧 blob 下载相关 import（`exportBookmarks` 引用）。
- [ ] **Gate C**：登录 → 选文件夹 → 点导出 → 下载的 HTML 打开效果与预览页一致。

### 阶段 D：验收测试

- [ ] **D1** AC1-AC7：手动验下载文件名、离线渲染、卡片点击、层级、搜索、空账户、对话框统计。
- [ ] **D2** AC8：未登录 curl/Postman 访问 `/api/bookmarks/export-data` 返回 401。
- [ ] **D3** AC9：确认无死代码（旧 export 端点、旧 service 方法、旧 API 函数全删）。
- [ ] **D4** AC10：导入功能回归（导入一个 Netscape 文件仍成功）。

---

## 校验命令

```bash
# 后端编译（Java 25）
cd /d/HelloWorld/Java/HLAIA-Navigation-Bar
./mvnw compile -q            # 或项目实际用的构建命令

# 前端
cd frontend
npm run build                # 生产构建，验证无报错（含 lint/type 感知）
# 开发期实时：npm run dev（已在 http://localhost:5173 运行）

# 401 验证（后端启动后）
curl -i http://localhost:8080/api/bookmarks/export-data   # 期望 401
```

---

## 风险点 / 回滚锚

| 风险 | 缓解 |
|---|---|
| 删旧端点后某处仍引用 | Gate B 编译 + Gate C 运行会暴露；全局搜 `exportBookmarks` / `/bookmarks/export` 确认无残留 |
| 导出 HTML 在大数据量下卡顿 | 卡片网格用 CSS grid，纯展示无虚拟化需求；搜索是 O(n) DOM 显隐，千级书签无压力。若超大可后续优化，非首版范围 |
| XSS：书签 title/url 含脚本 | `renderExportHtml` 内统一 `escapeHtml`；URL 用 `<a href>` 时校验 scheme（只允许 http/https，防 `javascript:`）|
| dev 预览页误入生产 | 路由用 `import.meta.env.DEV` 守卫，生产构建排除 |

## 备注

- 先做阶段 A 让你实时调设计，这是本任务体验的关键；B/C 接线在设计敲定后做。
- `renderExportHtml` 的 URL scheme 校验值得在 A2 就加上（白名单 http/https，其余降级为纯文本不渲染成链接）。
