# Bookmark export emits standard Netscape format importable by app and browsers

## Goal

导出对话框提供双格式选择:**默认标准 Netscape 书签格式**(导出的文件可再次导入本站,也能被 Chrome/Firefox 导入),现有美观展示页保留为第二选项;导入遇到不支持的格式时给用户明确提示。

## Background / Root Cause

- 现象:用户用本站导出书签(`bookmarks_20260903_210734.html`),再导入本站报错 `No <DL> tag found in imported bookmark file`。
- 根因:**导出与导入格式不兼容**。
  - 导出链路:前端 `BookmarkGrid.vue` 调 `GET /api/bookmarks/export-data` 拿 JSON 树,由 `frontend/src/utils/exportHtml.js` 的 `renderExportHtml()` 渲染成**美观展示页**(带 CSS/目录/搜索框),该 HTML 无任何 `<DL>`/`<DT>` 结构。
  - 导入链路:`BookmarkImportService`(约 L128)用 Jsoup `selectFirst("DL")` 查找 Netscape 格式根节点,找不到即抛 `ErrorCode.IMPORT_FAILED`(2008, "Bookmark import failed")。
  - 展示页文件中 371 条书签 URL 数据齐全,但结构无法被导入端识别;该格式浏览器也无法导入。
- 用户已确认方案:**双格式导出**(本次任务),不做"导入端兼容展示页格式"。

## Requirements

### R1 标准 Netscape 格式导出(默认)

- 导出对话框新增格式选择,两项:
  - **标准格式(Netscape)**——默认选中;
  - **展示页面(HTML)**——现有 `renderExportHtml` 产物,文案注明用于分享浏览、不可再导入。
- 标准格式产物要求(Netscape Bookmark File 格式,Chrome/Firefox 可导入):
  - 文件头:`<!DOCTYPE NETSCAPE-Bookmark-file-1>` + `<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">` + `<TITLE>`/`<H1>`;
  - 根 `<DL><p>`,文件夹 `<DT><H3>名称</H3>` + 嵌套 `<DL><p>...</DL><p>`,书签 `<DT><A HREF="url" ICON="data-uri">标题</A>`;
  - `ICON` 属性写入 `iconUrl`(库里存的就是 base64 data URI,可完整往返;为空则省略该属性);
  - 标题/名称正确转义(`& < > "`);
  - 文件名仍为 `bookmarks_<yyyyMMdd_HHmmss>.html`。
- 渲染实现放在 `frontend/src/utils/exportHtml.js`(新增纯函数,如 `renderNetscapeHtml(data)`),复用同一份 export-data JSON,不新增后端接口。

### R2 导入失败提示明确

- 后端:导入文件无 `<DL>` 时,异常消息从通用 "Bookmark import failed" 细化为明确指出格式不支持(如 "Unsupported bookmark file format: expected Netscape-format HTML (<DL>)")。
- 实现:新增(或复用)更精确的 ErrorCode,或携带该文案抛 BusinessException;不改变 HTTP 状态码与全局异常处理结构。

### R3 回归约束

- 展示页面导出行为不变(样式、目录、下载文件名规则)。
- 导入端对 Chrome/Firefox 导出文件的解析行为不变。
- 后端 export-data 接口契约不变。

## Non-Goals

- 不做"导入端解析展示页格式"(用户已否决)。
- 不做 ADD_DATE 等时间戳属性(导出数据无创建时间字段)。
- 不改归档任务 09-03-search-click-open-url 的内容。

## Acceptance Criteria

- [ ] 导出对话框可选格式,默认标准格式;选择"展示页面"时产物与现状一致。
- [ ] 用本站导出的标准格式文件再导入本站:文件夹层级、书签标题、URL、图标(data URI)完整还原。
- [ ] 导出的标准格式文件符合 Netscape 书签文件结构(DOCTYPE 声明 + DL/DT/H3/A),抽查可用 Chrome 书签管理器导入。
- [ ] 上传一个非 Netscape 结构的 HTML(如旧的展示页文件)导入:返回明确格式错误提示,不再是笼统的导入失败;事务回滚无残留数据。
- [ ] `cd frontend && npm run build` 通过;后端 `mvn -q compile`(或 `mvnw`)通过。

## Notes

- 轻量任务,PRD-only。
- 参考文件:前端 `BookmarkGrid.vue`(导出对话框 ~L326、下载逻辑 ~L936)、`utils/exportHtml.js`;后端 `BookmarkImportService.java`(~L128 无 DL 报错)、`ErrorCode.java`。
- 导入端读取细节(`HREF`/`ICON`/`H3`、DT 内嵌 DL)见 `BookmarkImportService` L219-280,导出端必须与之对齐。
