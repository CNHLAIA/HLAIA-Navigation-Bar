# PRD: 书签右键复制网址 + 页面刷新按钮

## 需求

### 功能 1：右键复制网址
- 在 BookmarkGrid 的右键上下文菜单中新增"复制网址"按钮
- 位于"编辑"和"移动到..."之间
- 点击后使用 `navigator.clipboard.writeText()` 将书签 URL 复制到剪贴板
- 复制成功后显示 ElMessage 提示

### 功能 2：页面刷新按钮
- 在 BookmarkGrid 的 grid-top-actions 区域新增一个刷新按钮
- 位于"导入书签"按钮左侧
- 点击后重新调用 `bookmarkStore.fetchBookmarks(folderId)` 刷新当前文件夹的书签列表

## 涉及文件
- `frontend/src/components/BookmarkGrid.vue` — 右键菜单添加复制项 + 顶部添加刷新按钮
- `frontend/src/i18n/zh-CN.js` — 中文翻译
- `frontend/src/i18n/en-US.js` — 英文翻译

## 不涉及后端改动
