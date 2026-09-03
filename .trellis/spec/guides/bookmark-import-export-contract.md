# 书签导入/导出格式契约

> 前端导出渲染器与后端导入解析器之间的可执行契约。2026-09-03 的 bug(本站导出的文件本站无法导入)正是因为该跨层格式约定未文档化,两端各自演化导致不兼容。

---

## 契约:唯一合法的导出可导入格式是 Netscape Bookmark File

- **后端导入端** `BookmarkImportService` 用 Jsoup `selectFirst("DL")` 找根节点,再按 `<DT><H3>`(文件夹)/`<DT><A HREF>`(书签)递归遍历;找不到 `<DL>` 抛 `IMPORT_INVALID_FORMAT(2011)`。
- **前端导出端** `frontend/src/utils/exportHtml.js` 提供两个纯函数:
  - `renderNetscapeHtml(data)` —— 标准 Netscape 格式,可再导入本站、可导入 Chrome/Firefox,导出对话框**默认**;
  - `renderExportHtml(data)` —— 美观展示页,仅供分享浏览,**不可再导入**,UI 文案必须注明。
- 修改任一端时,必须核对另一端仍能闭环(导出→导入往返)。

## 关键结构细节(改格式前必读)

1. **`<DL>` 必须写在 `<DT><H3>` 的下一行**(换行分隔)。Jsoup 会把这样的 DL 自动嵌套进前面的 DT,这正是导入端的主解析路径(`child.selectFirst("DL")`);写成同一行或其他布局需重新验证。
2. `ICON` 属性放 base64 data URI(库里 `icon_url` 存的格式),为空则整个属性省略(导入端 OVERWRITE 模式会保留原图标)。
3. 属性值与文本节点都要转义 `& < > "`。
4. 文件头固定四行:`<!DOCTYPE NETSCAPE-Bookmark-file-1>` / META charset / `<TITLE>` / `<H1>`。
5. `bookmark.folder_id` NOT NULL,不存在根级散落书签,根 `<DL>` 下只有顶层文件夹的 DT。

## 验证方式

- 最小验证:导出 → 重新导入本站,文件夹/标题/URL/图标完整还原。
- 修改渲染器或解析器任一端后,用对方端跑一次真实解析(可参考 jsoup 直接 parse 导出产物),不要只做目测。
