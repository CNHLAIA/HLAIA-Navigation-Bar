# Implement — 书签导出功能

> 执行顺序即依赖顺序。每步标注【验证】，通过后再进入下一步。所有路径相对仓库根。

## Step 0 — 准备

- [ ] 确认 `.trellis/tasks/06-28-bookmark-export/` 下 `prd.md`、`design.md` 已评审通过。
- [ ] `task.py start 06-28-bookmark-export` 后再开始编码（status → in_progress）。
- 验证：`python ./.trellis/scripts/task.py current --source` 指向本任务。

---

## Step 1 — 后端：新增错误码

- [ ] `src/main/java/com/hlaia/common/ErrorCode.java`：在业务码段（2009 之后）新增
      `EXPORT_FAILED(2010, "Bookmark export failed")`。
- 验证：编译通过 `./mvnw compile -q`（或 IDE 编译）。

## Step 2 — 后端：新增 `BookmarkExportService`

- [ ] 新建 `src/main/java/com/hlaia/service/BookmarkExportService.java`：
  - `@Service` + `@RequiredArgsConstructor` + `@Slf4j`（与 `BookmarkImportService` 注解风格一致）。
  - 依赖 `FolderMapper`、`BookmarkMapper`。
  - **公共方法**：
    ```java
    public byte[] exportBookmarks(Long userId)
    ```
  - **内部逻辑**（参考 `FolderService.getFolderTree` 的两查询 + 建树模式）：
    1. 查全部文件夹 `folderMapper.selectList(eq(userId).orderByAsc(sortOrder))`。
    2. 查全部书签 `bookmarkMapper.selectList(eq(userId).orderByAsc(sortOrder))`。
    3. 书签按 `folderId` 分组成 `Map<Long, List<Bookmark>>`。
    4. 子文件夹按 `parentId` 分组成 `Map<Long, List<Folder>>`。
    5. 字符串拼接生成 Netscape HTML（见 design §2.4/§2.6）：
       - 拼文件头（`<!DOCTYPE NETSCAPE-Bookmark-file-1>` + `<META>` + `<TITLE>` + `<H1>` + 顶层 `<DL><p>`）。
       - 递归方法 `renderFolder(StringBuilder, folder, depth, ...)`：
         - 拼 `<DT><H3>{转义后的name}</H3>`，再拼子 `<DL><p>`；
         - 先输出该 folder 的书签（`<DT><A HREF="..." ICON="...">{转义标题}</A>`），再递归输出子文件夹；
         - 闭合 `</DL><p>`。
       - 根级文件夹（`parentId == null`）逐一 `renderFolder(...)`。
    6. `escapeHtml()`（文本节点，转义 `<>&`）和 `escapeAttr()`（属性值，额外转义 `"`）两个手写转义方法。
    7. `return html.toString().getBytes(StandardCharsets.UTF_8)`。
  - 异常：catch 任意 `Exception` → `log.error` + 抛 `BusinessException(EXPORT_FAILED)`。
  - **注意 `children` 判空**：Folder 实体无 children 字段；递归时用 `foldersByParent.get(folder.getId())` 取子列表，可能为空 list 或 null——用 `getOrDefault` 或判空。
- [ ] 代码注释遵循项目风格：解释「为什么用 Jsoup 而非字符串拼接」「为什么书签按 folderId 分组」等 WHY。
- 验证：编译通过。

> **关键测试点（AC5 闭环）**：导出的 HTML 必须能被 `BookmarkImportService.parseAndImport` 重新解析。建议先在内存里手写一个最小用例（1 根文件夹 + 1 子文件夹 + 2 书签含 ICON）验证结构。

## Step 3 — 后端：`BookmarkController` 新增导出端点

- [ ] `src/main/java/com/hlaia/controller/BookmarkController.java` 新增：
  ```java
  @GetMapping("/bookmarks/export")
  @Operation(summary = "Export all bookmarks as Netscape HTML")
  public ResponseEntity<byte[]> exportBookmarks(@AuthenticationPrincipal Long userId) {
      byte[] html = bookmarkExportService.exportBookmarks(userId);
      String filename = "bookmarks_" + LocalDateTime.now()
          .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".html";
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
          .header(HttpHeaders.CONTENT_DISPOSITION,
                  "attachment; filename=\"" + filename + "\"")
          .body(html);
  }
  ```
- [ ] 注入 `private final BookmarkExportService bookmarkExportService;`（与现有 `bookmarkImportService` 对称）。
- [ ] 补充必要的 import：`ResponseEntity`、`HttpHeaders`、`MediaType`、`LocalDateTime`、`DateTimeFormatter`。
- 验证：启动后端，带 JWT 访问 `GET /api/bookmarks/export`，下载到 HTML；未带 token 返回 401（AC7）。

## Step 4 — 后端：单元/集成测试（可选但推荐）

- [ ] 参照 `src/test/java/com/hlaia/service/BookmarkImportServiceTest.java`，新建 `BookmarkExportServiceTest`：
  - 构造 mock 数据（root folder + 子 folder + bookmarks with iconUrl）；
  - 调 `exportBookmarks`，断言返回 HTML 包含正确的 `<H3>`、`<A HREF>`、`ICON`；
  - **闭环断言**：把导出的 HTML 字节喂给 `BookmarkImportService`（mock mapper 接收 insert），验证能解析出对应数量的 folder/bookmark（AC5 自动化）。
- 验证：`./mvnw test -Dtest=BookmarkExportServiceTest`。

---

## Step 5 — 前端：改 `request.js` 加 blob 守卫

- [ ] `frontend/src/api/request.js` 响应拦截器成功分支开头加：
  ```js
  // blob 响应（文件下载）绕过业务码解包，直接返回完整 response
  if (response.config.responseType === 'blob') {
    return response
  }
  const res = response.data
  // ...原有逻辑
  ```
- 验证：现有 JSON 接口不受影响（守卫只在 blob 时触发）；手动验证一个 blob 请求拿到的是 `response` 对象。

## Step 6 — 前端：新增 `exportBookmarks` API

- [ ] `frontend/src/api/bookmark.js` 末尾新增（紧挨 `importBookmarks`）：
  ```js
  /**
   * 导出当前用户全部书签为 Netscape Bookmark HTML 文件
   * @returns {Promise} - 完整 response 对象（拦截器对 blob 直接透传），response.data 为 Blob
   */
  export function exportBookmarks() {
    return request.get('/bookmarks/export', {
      responseType: 'blob',
      timeout: 60000
    })
  }
  ```

## Step 7 — 前端：i18n 文案

- [ ] `frontend/src/i18n/zh-CN.js` 的 `bookmarks` 下（`importDialog` 之后）新增 `exportDialog`：
  ```js
  exportDialog: {
    title: '导出书签',
    summary: '将导出全部 {folders} 个文件夹、{bookmarks} 个书签（含图标）',
    exportBtn: '确认导出',
    exporting: '正在导出...',
    success: '导出成功',
    failed: '导出失败，请重试'
  }
  ```
- [ ] `frontend/src/i18n/en-US.js` 对应英文块（`title: 'Export Bookmarks'` 等）。
- 验证：`grep exportDialog frontend/src/i18n/*.js` 两文件都有。

## Step 8 — 前端：`BookmarkGrid.vue` 加按钮 + 弹窗 + 逻辑

- [ ] **模板**：在 `.grid-top-actions` 内、`import-bookmark-btn` 之后加 `export-bookmark-btn`（镜像样式，svg 用"上传箭头"图标）。
- [ ] **模板**：新增导出 `el-dialog`（镜像 import dialog 结构，但内容简化为：一段 `summary` 文案，无表单）。footer：取消 + 确认导出（loading）。
- [ ] **script setup**：
  - import `exportBookmarks`；
  - 新增响应式：`exportDialogVisible`、`exportLoading`；
  - computed `exportSummary`：递归 `folderStore.folderTree` 统计 `folderCount`、`bookmarkCount`（累加各 `bookmarkCount`）。
  - `openExportDialog()`：置 visible=true（数据从 computed 取，无需预加载）。
  - `handleExportConfirm()`：见 design §3.2，调 `exportBookmarks()` → 建 Blob → 触发 `<a download>` → toast 成功/失败 → 关闭弹窗。
  - 文件名解析：从 `response.headers['content-disposition']` 提取 `filename="..."`；失败兜底前端生成。
- [ ] **样式**：新增 `.export-bookmark-btn` 样式，复用 `.import-bookmark-btn` 的视觉（border + surface bg），保持工具栏按钮一致。
- 验证：`cd frontend && npm run build` 通过；浏览器手动：点导出 → 弹窗显示数量 → 确认 → 下载到 HTML 文件。

---

## Step 9 — 端到端验收（对照 AC）

- [ ] AC1：选中文件夹 → 工具栏看到"导出书签" → 点击 → 弹窗显示正确的文件夹/书签数。
- [ ] AC2：确认后下载 `bookmarks_<时间戳>.html`。
- [ ] AC3/AC4：用文本编辑器/浏览器打开导出文件，检查层级、HREF、ICON 正确。
- [ ] **AC5 闭环**：用另一账号（或清空测试账号）走"导入书签"上传刚导出的文件 → 文件夹树与书签被正确还原（标题、URL、图标、层级）。
- [ ] AC6：把导出文件导入 Chrome（chrome://bookmarks → 导入书签）→ 结构可见。
- [ ] AC7：未登录/他人 token 访问导出端点 → 401 / 只能拿自己数据。
- [ ] AC8：空书签账号导出 → 得到含空 `<DL>` 的合法 HTML，不报错。

## Step 10 — 全量质量检查

- [ ] 后端：`./mvnw test`（全量测试绿）+ lint。
- [ ] 前端：`cd frontend && npm run build` + lint。
- [ ] 跨层一致性：错误码、API 路径、i18n 键前后端对齐。
- [ ] 对照 `prd.md` 逐条 AC 打勾。

## 风险点 / 回滚

- **HTML 结构不被导入端解析**：采用字符串拼接方案（非 Jsoup DOM），规避了 `<DL><p>` 被 HTML5 解析器纠错的风险。仍需用最小用例验证导出结构能被 `parseAndImport` 吃下（AC5 是硬指标）。导入端用 `equalsIgnoreCase` 比较 `<DT>/<DL>/<H3>/<A>`，本服务输出大写标签与之兼容。
- **`request.js` blob 守卫误伤现有接口**：守卫条件 `responseType === 'blob'` 精确，现有 JSON 接口不会触发；若发现回归，守卫可改为只在 URL `/bookmarks/export` 时生效。
- **文件名 header 编码**：纯 ASCII 文件名（`bookmarks_yyyyMMdd_HHmmss.html`）无编码问题，无需 RFC 5987 `filename*=`。
