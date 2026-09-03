# Fix search result click to open bookmark URL instead of folder page

## Goal

搜索栏/搜索结果中点击书签时直接在新标签页打开目标网址,而不是跳转到书签所在文件夹页面。

## Background / Root Cause

- 现象:搜索栏搜索到目标网站后点击,页面跳到该书签所在的文件夹目录页,没有打开目标网页。
- 根因(已定位):`frontend/src/components/SearchBar.vue` 中两个点击处理函数对 `type === 'bookmark'` 的结果只执行了 `folderStore.setCurrentFolder(item.folderId)` + `router.push('/')`,从未使用 `item.url` 打开网址:
  - `handleSelect()` — 下拉建议列表点击(约 L144-153)
  - `handleResultClick()` — 搜索结果弹窗点击(约 L155-163)
- 项目内既有的书签打开约定(参考 `BookmarkGrid.vue` L606):
  `window.open(bookmark.url, '_blank', 'noopener,noreferrer')`
- 后端无需改动:`/api/search` 与 `/api/search/suggest` 返回的 `SearchResponse.SearchItem` 对书签已包含 `url`、`folderId` 字段(`SearchService.java` suggest/search 均有赋值)。

## Requirements

1. `SearchBar.vue` 下拉建议列表:点击书签项 → 在新标签页打开 `item.url`(使用 `window.open(item.url, '_blank', 'noopener,noreferrer')`,与 BookmarkGrid 行为一致),并关闭下拉。
2. `SearchBar.vue` 搜索结果弹窗:点击书签项 → 同样在新标签页打开 `item.url`,并关闭结果弹窗。
3. 点击书签项不再切换当前文件夹、不再跳转首页(不改变导航页当前浏览位置)。
4. 文件夹类型结果保持现状:切换 `setCurrentFolder(item.id)` 并跳转首页展示该文件夹。
5. 点击书签项后清空/关闭搜索 UI 状态(下拉或弹窗),但不强制清空关键词(保持现状即可,不额外加需求)。

## Non-Goals

- 不改动后端搜索接口与 DTO。
- 不修改文件夹结果的跳转行为。
- 不引入"打开书签并定位到所在文件夹"等新交互。

## Acceptance Criteria

- [ ] 在搜索框输入关键词,下拉建议中出现书签,点击该项:浏览器新标签页打开该书签 URL,下拉关闭,当前页面停留在原文件夹视图。
- [ ] 按 Enter 打开搜索结果弹窗,点击书签结果:新标签页打开目标 URL,弹窗关闭。
- [ ] 点击文件夹结果(下拉或弹窗):仍正确跳转到该文件夹页面(回归不变)。
- [ ] `cd frontend && npm run build` 通过(无新增编译错误)。

## Notes

- 轻量任务,PRD-only(无需 design.md / implement.md)。
- 修复方式:将两个处理函数中 bookmark 分支替换为 `window.open`,folder 分支保留;注意 `@mousedown.prevent` 下拉项与 dialog 项 `@click` 的事件语义差异不需要变更。
