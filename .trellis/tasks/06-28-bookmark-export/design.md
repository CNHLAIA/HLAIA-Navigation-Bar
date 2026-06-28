# Design — 书签导出功能

## 1. 架构与边界

导出 = 「加载该用户全部文件夹 + 全部书签」→「组装成与导入服务互逆的 Netscape Bookmark HTML」→「以文件下载响应返回」。

```
前端 BookmarkGrid.vue ──GET /api/bookmarks/export──▶ BookmarkController.exportBookmarks
                                                              │
                                                              ▼
                                                    BookmarkExportService
                                                       │        │
                                          getFolderTree ┘        └ selectList(书签)
                                              │
                                              ▼
                                   生成 Netscape HTML (Jsoup)
                                              │
                                              ▼
                                   ResponseEntity<byte[]>
                                   Content-Disposition: attachment
```

**为什么不让 `getFolderTree` 直接带书签明细？**
`FolderService.getFolderTree` 返回的 `FolderTreeResponse` 只有 `bookmarkCount`，不含书签实体（标题/URL/icon）。改它会影响整个树接口，破坏面太大。导出服务自己查一次书签、按 `folderId` 分组即可，与导入服务"一次性加载全部书签"的模式一致。

## 2. 后端契约

### 2.1 端点

```
GET /api/bookmarks/export
Header: Authorization: Bearer <token>   # JWT 自动保护，无需改 SecurityConfig
返回: 200 OK
      Content-Type: text/html; charset=UTF-8
      Content-Disposition: attachment; filename="bookmarks_20260628_153012.html"
      Body: Netscape Bookmark HTML 字节流
```

无请求参数（范围已定全量、favicon 已定始终带）。

**为什么 GET 而非 POST？**
导出是只读、幂等、无副作用的"取数据"操作，符合 GET 语义，也便于浏览器原生 `<a>`/`window.location` 直接触发下载。导入用 POST 是因为它要上传文件（有副作用、非幂等）。

**为什么路径在 `/api/bookmarks/export` 而非 `/api/bookmarks/{id}/export`？**
导出对象是"用户全部书签"，不是某个具体书签资源；放在 `/api/bookmarks` 集合下、用 `/export` 子动作表达，与现有 `/api/bookmarks/import` 对称。

### 2.2 新增组件

| 组件 | 路径 | 职责 |
|---|---|---|
| `BookmarkExportService` | `service/BookmarkExportService.java` | 加载树 + 书签、序列化为 Netscape HTML |
| 端点方法 | `controller/BookmarkController.java` 内新增 `exportBookmarks` | 解析 userId、调 service、组装 `ResponseEntity<byte[]>` |

**新增错误码**：`ErrorCode.EXPORT_FAILED(2010, "Bookmark export failed")`。
- 业务码段 `2001-2999` 已有到 2009，`2010` 顺延。导入用的是 `IMPORT_FAILED(2008)`，导出用 `2010`（跳过 2009 已占用的 `BOOKMARK_DUPLICATE`），语义对称。

### 2.3 文件名

`bookmarks_yyyyMMdd_HHmmss.html`（用户本地时间）。时间戳避免重名，让多次导出文件并存——契合"灾备备份"场景下保留多个版本。

### 2.4 HTML 序列化（与 `BookmarkImportService` 互逆）

导入端解析的格式（来自 `BookmarkImportService.parseAndImport`）：
- `<DL><p>` 表示一个文件夹的内容容器
- `<DT><H3>文件夹名</H3>` 后紧跟 `<DL><p>` 为该文件夹的子内容
- `<DT><A HREF="url" ICON="data:...;base64,...">标题</A>` 表示书签
- 根层是 `<!DOCTYPE NETSCAPE-Bookmark-file-1>` + `<META>` + 顶层 `<DL><p>`

**导出输出结构**（确保能被 `BookmarkImportService` 重新解析，也能被 Chrome 识别）：

```html
<!DOCTYPE NETSCAPE-Bookmark-file-1>
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3>文件夹A</H3>
    <DL><p>
        <DT><A HREF="https://..." ICON="data:image/png;base64,...">书签标题</A>
        <DT><H3>子文件夹</H3>
        <DL><p>
            ...
        </DL><p>
    </DL><p>
    <DT><A HREF="https://..." ICON="data:image/png;base64,...">根级书签</A>
</DL><p>
```

**关键约定**：
- 根级文件夹（`parent_id = NULL`）展开在顶层 `<DL>` 内。
- 根级书签（`folder_id` 指向根级文件夹的……等等，需澄清见下"数据模型澄清"）。
- `<H3>` 和 `<A>` 的 `ICON` 属性值必须 HTML 属性转义（`"` → `&quot;`）。
- 书签按 `sort_order` 排序输出；同级文件夹也按 `sort_order`。
- `children` 为 `null`（懒初始化）的叶子文件夹，输出空的 `<DL><p></DL><p>`。

### 2.5 数据模型澄清（重要）

`bookmark.folder_id` 是 `NOT NULL`，意味着**每个书签必然属于一个文件夹**，不存在"无文件夹的根级书签"。而 `folder.parent_id = NULL` 表示根级文件夹。

因此导出树形态固定为：
```
根级文件夹A (parent_id=NULL)
├── 书签们 (folder_id=A)
├── 子文件夹B
│   └── 书签们 (folder_id=B)
根级文件夹C (parent_id=NULL)
└── 书签们 (folder_id=C)
```
序列化时遍历 `getFolderTree` 返回的 roots 列表，每个根文件夹作为顶层 `<DT><H3>`，递归处理其 `children` 和该文件夹下的书签。

### 2.6 HTML 转义与生成方式

**用字符串拼接生成 HTML，配手写转义方法**（`escapeHtml` 处理 `<>&` 用于文本节点，`escapeAttr` 额外处理 `"` 用于属性值）。

**为什么不用 Jsoup DOM API？**
最初设计倾向用 Jsoup（与导入端同库），但分析后发现风险：Netscape 格式里的 `<DL><p>` 是非标准结构——`<p>` 出现在 `<dl>` 直接子级不符合 HTML 规范，Jsoup 的 HTML5 解析器会做纠错（可能移动或吞掉 `<p>`），破坏导出结构。而 Netscape 格式是固定模板，结构简单可预测，字符串拼接更可控，也更易与 Chrome 官方导出格式对齐。

导入端 `parseAndImport` 用 `equalsIgnoreCase` 比较 `<DT>`/`<DL>`/`<H3>`/`<A>`，所以本服务输出大写标签（与 Chrome 导出一致）能被正确解析。

## 3. 前端契约

### 3.1 API 客户端

`frontend/src/api/bookmark.js` 新增：

```js
export function exportBookmarks() {
  return request.get('/bookmarks/export', {
    responseType: 'blob',          // 关键：二进制响应，绕过默认 JSON 解包
    timeout: 60000                 // 文件可能数 MB，对齐导入超时
  })
}
```

**核心难点：响应拦截器兼容**
`request.js` 的响应拦截器对 `res = response.data` 做 `res.code !== 200` 判断。对于 blob 响应，`res` 是 `Blob` 对象，`res.code` 为 `undefined`，会被误判为业务错误并弹 `ElMessage.error`。

**解决方案**：在响应拦截器开头加守卫——若 `response.config.responseType === 'blob'`，直接 `return response`（返回完整 response 对象，让调用方取 `response.data` 这个 Blob）。这是最小侵入改动，不影响现有 JSON 接口。

> 替代方案：导出走独立的 `axios` 实例（不复用 `request`）。但那样要重复实现 JWT 拦截器、401 刷新逻辑，维护成本高，不采纳。

### 3.2 前端交互（`BookmarkGrid.vue`）

镜像现有导入弹窗模式：

| 项 | 导入（现有） | 导出（新增） |
|---|---|---|
| 按钮 | `import-bookmark-btn`（工具栏） | `export-bookmark-btn`（同位置，紧挨导入按钮） |
| 状态 | `importDialogVisible` / `importLoading` | `exportDialogVisible` / `exportLoading` |
| 弹窗内容 | 文件上传 + 目标文件夹 + 重复模式 | 显示「文件夹数 + 书签数」做预期管理 |
| 确认回调 | `handleImportConfirm` | `handleExportConfirm` |

**弹窗内容数据来源**：从 `folderStore.folderTree`（已加载）递归统计文件夹总数 + 书签总数（累加各节点 `bookmarkCount`）。无需新增后端"预统计"接口——树已在内存。

**下载处理**（`handleExportConfirm`）：
```js
const response = await exportBookmarks()         // 拦截器已改为返回完整 response
const blob = new Blob([response.data], { type: 'text/html;charset=utf-8' })
const url = URL.createObjectURL(blob)
const a = document.createElement('a')
a.href = url
a.download = response.headers['content-disposition']  // 优先用后端给的文件名
                            // 需从 header 解析 filename
document.body.appendChild(a); a.click(); a.remove()
URL.revokeObjectURL(url)
```

文件名优先取后端 `Content-Disposition` 中的 `filename`；解析失败则前端用本地时间兜底生成。

### 3.3 i18n

在 `bookmarks` 下新增 `exportDialog` 块（与 `importDialog` 平级），`zh-CN.js` 和 `en-US.js` 各一份。键：`title` / `summary`（含 {folders}/{bookmarks} 占位）/ `exportBtn` / `exporting` / `success` / `failed`。

### 3.4 触发位置约束

导出按钮放在 `.grid-top-actions`（与刷新、导入、新增同排）。**但导出是"全部书签"，与当前选中文件夹无关**——因此按钮的显示条件不应依赖 `folderId`。

> 现有 `v-if="folderId"` 包裹了整个 `.grid-top-actions`，意味着未选文件夹时工具栏隐藏。导出按钮若也放这里，未选文件夹时点不到。
>
> **决策**：保持导出按钮在 `.grid-top-actions` 内（视觉一致），但这要求用户先选中一个文件夹才能看到导出入口——这在当前双栏布局下是常态（左侧默认选中第一个文件夹）。若发现"未选文件夹也要能导出"，作为后续优化。PRD 的 AC 不强制要求未选文件夹可见。

## 4. 安全与数据隔离

- 端点落在 `SecurityConfig` 的 `anyRequest().authenticated()`，JWT 保护。
- 用户经 `@AuthenticationPrincipal Long userId` 解析，service 所有查询 `.eq(userId)` 限定，**与现有所有书签/文件夹接口的数据隔离模式完全一致**。
- 无操作日志（R5 已定）。
- 无新增 SQL 注入面（全用 MyBatis-Plus `LambdaQueryWrapper`，无拼接）。

## 5. 性能考量

- 两次查询（全部文件夹 + 全部书签），与 `getFolderTree` 一致，是项目既有模式。
- 内存：单个用户书签量级（几百到几千），`icon_url` 是 `TEXT` 但总量在 MB 级，`byte[]` 一次性装下无压力（导入服务同样是全量加载，已验证可行）。
- 不引入分页/流式（`StreamingResponseBody`）——个人使用规模不需要，`ResponseEntity<byte[]>` 与 `FaviconController` 模式一致，简单直接。

## 6. 兼容性与回滚

- **纯新增**：新增 service、新增 controller 方法、新增前端按钮/弹窗/api/i18n。**无任何对现有接口/表/字段的修改**（`request.js` 拦截器加 blob 守卫是唯一对现有代码的改动，且是纯增量分支）。
- 回滚：删除新增文件 + 撤销 `BookmarkController` 的新增方法 + 撤销 `BookmarkGrid.vue`/`request.js`/i18n 的增量即可，零数据迁移。
- 导入服务（`BookmarkImportService`）**零改动**——AC5 借它做闭环验证。

## 7. 关键取舍记录

| 决策点 | 选择 | 理由 |
|---|---|---|
| HTML 生成方式 | 字符串拼接 + 手写转义 | Netscape `<DL><p>` 非标准，Jsoup 解析会纠错破坏结构；拼接更可控，与 Chrome 官方格式对齐 |
| 下载响应 | `ResponseEntity<byte[]>` | 项目首个文件下载，复用 `FaviconController` 模式，不引入流式 |
| 文件名时间戳 | 本地时间 `yyyyMMdd_HHmmss` | 个人使用，无需时区复杂度 |
| 前端 blob 处理 | 改 `request.js` 加 blob 守卫 | 最小侵入，复用 JWT/刷新逻辑 |
| 导出按钮可见性 | 依赖 `folderId`（在工具栏内） | 视觉一致，默认选中场景下可用；未选文件夹可见留作后续 |
| 弹窗统计数据来源 | 内存 `folderStore` 递归统计 | 避免新增后端预统计接口 |
