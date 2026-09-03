# Import/export buttons always available with root-level import target

## Goal

导入/导出按钮在主视图始终可见(不依赖选中文件夹,空账号也能导入);导入对话框增加"根层级"目标选项,使导入后的目录结构与导出文件完全一致。纯前端任务,后端已支持。

## Background / Root Cause

用户报告两个连带的可用性问题:

1. **按钮被文件夹锁死**:`BookmarkGrid.vue` L68 `<div v-if="folderId" class="grid-top-actions">` 把导入/导出/刷新/新增按钮整排条件渲染。新账号没有任何文件夹时 `folderId` 为 null,按钮全部消失——用户被迫先手动建一个文件夹才能看到导入按钮。
2. **导入结构被强制加一层**:导入对话框的文件夹选择器(`importFolderList`,L286)只列出现有文件夹,**没有"根层级"选项**;`openImportDialog` 默认选中当前文件夹(L848)。于是导入内容永远落在某个文件夹里——用户为了导入而手动创建的那个根目录成了多余的包裹层,导入结果与导出文件的目录树不一致。

后端事实(已核实,无需改动):

- `BookmarkController` L313:`@RequestParam(value = "targetFolderId", required = false) Long targetFolderId`。
- `BookmarkImportService.importBookmarks`:`targetFolderId == null` 时第一层内容直接挂到根层级;非空时校验归属后作为 parentId。
- 导出(`/bookmarks/export-data`)本来就是全量导出,与当前文件夹无关(`exportStats` 遍历整棵树)。

## Requirements

### R1 导入/导出按钮全局可见

- 主视图(`BookmarkGrid.vue`)顶栏的**导入、导出**两个按钮始终渲染,不受 `folderId` 约束。
- **刷新、新增书签**按钮保持 `v-if="folderId"`(它们操作当前文件夹的书签,无文件夹时无意义)。
- 实现方式:拆分 `grid-top-actions` 的 `v-if`,而非整块保留(允许按钮分组渲染:左侧或排序上保证视觉不突兀,遵循现有样式)。
- 说明取舍:按钮放主视图顶栏而非 NavBar——NavBar 还出现在设置页/暂存页,导入导出在那里没有语义;主视图本身就是书签管理入口,"全局"以主视图常驻满足。

### R2 导入支持根层级目标

- 导入对话框文件夹选择器顶部新增"根层级"选项:
  - 展示名用 i18n(如 `bookmarks.importDialog.rootLevel`,文案"根层级 / Root level"),图标风格与现有文件夹项一致;
  - 排在列表最前、无缩进;
  - 选中值用 `null` 表示。
- 默认选中逻辑:当前选中了文件夹 → 默认该文件夹(保持现有便利);未选中文件夹(含空账号)→ 默认根层级。
- 空账号(无任何文件夹)时选择器仅显示"根层级"一项,可直接导入。
- **提交时 FormData 处理**:`targetFolderId` 为 `null` 时**不得 append**(JS `formData.append(k, null)` 会序列化成字符串 "null",Spring 转 Long 报错);仅非 null 时 append。后端缺省参数即根层级。

### R3 回归约束

- 在文件夹内导入到该文件夹的现有行为不变(默认值、OVERWRITE/SKIP、统计提示)。
- 导出对话框行为不变。
- 刷新/新增按钮在选中文件夹时的行为与位置不变。
- 后端零改动。

## Non-Goals

- 不把按钮迁移到 NavBar(理由见 R1)。
- 不改导入解析/导出渲染逻辑(上一任务 09-03-export-netscape-format 的范围)。
- 不做空状态引导页重设计。

## Acceptance Criteria

- [ ] 空账号(无文件夹、未选中)登录主视图:顶栏可见"导入""导出"按钮,"刷新""新增书签"不显示。
- [ ] 空账号点击导入:目标选择器只有"根层级"且默认选中;选择 Netscape 格式文件导入成功,生成的目录树与导出文件结构一致(无多余包裹文件夹)。
- [ ] 选中某文件夹后点击导入:默认目标为该文件夹,列表第一项"根层级"可改选;改选根层级导入后结构同样与文件一致。
- [ ] 选中文件夹时四个按钮(刷新/导入/导出/新增)均在,行为与现状一致(回归)。
- [ ] i18n 中英文案齐全,无硬编码 UI 文本;`cd frontend && npm run build` 通过。

## Notes

- 轻量任务,PRD-only。
- 关键文件:`frontend/src/components/BookmarkGrid.vue`(L68 按钮区条件、L279-300 选择器模板、L457-480 状态与 importFolderList、L848 openImportDialog 默认值、L895-900 FormData)、`frontend/src/i18n/zh-CN.js` / `en-US.js`(importDialog 下新增 rootLevel 等 key)。
- 相关 spec:`.trellis/spec/guides/bookmark-import-export-contract.md`(格式契约,本次不改格式但同域)。
