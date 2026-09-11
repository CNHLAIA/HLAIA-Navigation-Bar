# Design: Bookmark note remarks

## 总体思路

备注 = 复用后端现成的 `bookmark.description` 字段。后端唯一改动是一个 Flyway 迁移（拓宽列宽）+ 一个校验注解；全部新功能逻辑在前端（菜单项、备注对话框、悬浮弹窗、markdown-it 渲染）。

## 架构与边界

### 后端（2 处小改动）

1. **`V8__widen_bookmark_description.sql`**（命名沿用 `V{序号}__{动词短语}.sql`，参照 V4 拓宽 icon_url 的先例）：
   ```sql
   ALTER TABLE `bookmark` MODIFY COLUMN `description` TEXT COMMENT '书签备注(Markdown)';
   ```
   - VARCHAR(500) → TEXT（64KB），非破坏性变更，已有数据原样保留
2. **`BookmarkCreateRequest.description`** 加 `@Size(max = 10000)`：旧列宽 500 的 DB 层限制消失后，由 Bean Validation 兜底防止超大备注（DTO 同时服务 POST/PUT，一处生效）

不需要改的（已打通）：实体、`BookmarkResponse`、`BookmarkService` create/update 拷贝逻辑（非 null 即覆盖，空串可清空）、ES 索引与搜索、导出。

### 前端（4 个触点 + 1 个新依赖 + 1 个新工具 + 1 个新组件）

```
frontend/
├── package.json                      # + markdown-it
├── src/utils/markdown.js             # 新建：共享 markdown-it 实例 + renderMarkdown()
├── src/components/NoteDialog.vue     # 新建：备注编辑对话框（textarea + 编辑/预览切换）
├── src/components/BookmarkGrid.vue   # 右键菜单加「备注」项 + 挂载 NoteDialog + 保存逻辑
├── src/components/BookmarkCard.vue   # 悬浮 1 秒只读弹窗
└── src/i18n/{zh-CN,en-US}.js         # 新键
```

**决策：不引入 el-popover**——项目现有右键菜单就是自定义 teleport 方案，悬浮弹窗沿用同一模式，主题样式完全可控（全项目目前零 tooltip/popover 用例）。

## 数据流与契约

API 契约**零变更**（`description` 字段本就存在于请求/响应 DTO）：

```
NoteDialog (textarea)
  └─ emit save(text)
       └─ BookmarkGrid.handleNoteSave → bookmarkStore.updateBookmark(id, { description: text })
            └─ PUT /api/bookmarks/{id}  (BookmarkCreateRequest.description)
                 └─ BookmarkService.updateBookmark: 非 null 字段覆盖 → 空串清空备注
                      └─ ES SearchSyncEvent(UPDATE)（既有机制）
                           └─ store 失效缓存并整表刷新 → 卡片悬浮读到新备注
```

悬浮弹窗数据源：BookmarkCard 已接收完整 bookmark prop（列表接口本就返回 description），无新增请求。

## 组件设计

### `utils/markdown.js`

```js
import MarkdownIt from 'markdown-it'

// html: false —— 渲染前转义原始 HTML 片段，防备注内容注入脚本
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
// 覆盖链接渲染：悬浮预览/编辑预览中的链接按项目外链约定新标签页打开
md.renderer.rules.link_open = (tokens, idx, options, env, self) => { ... target="_blank" rel="noopener noreferrer" ... }
export function renderMarkdown(text) { return text ? md.render(text) : '' }
```

### `NoteDialog.vue`（参照 FolderPickerDialog 的受控对话框模式）

- Props：`visible: Boolean`、`bookmark: Object`（打开时取 `bookmark.description ?? ''` 为初值）
- Emits：`update:visible`、`save(text)`
- 结构：`el-dialog` + 顶部 `el-radio-group`（编辑/预览）或等价 tab 切换 + 编辑态 `el-input type="textarea" :rows="10"` + 预览态 `v-html="renderMarkdown(text)"` + footer 取消/保存（保存按钮 loading）
- 纯 UI 组件，不调 API（组合逻辑归 BookmarkGrid，符合单一职责约定）
- 对话框 class `note-dialog`，`append-to-body`，宽度沿用 bookmark-dialog 模式并加 ≤768px 媒体查询

### `BookmarkGrid.vue` 改动

- 菜单模板（170-202 行区域）新增 `<button class="ctx-item">` 备注（i18n 键 `bookmarks.contextMenu.note`），点击调 `handleEditNote(contextMenu.bookmark)` 关菜单开对话框
- 新增 `noteDialogVisible` ref + `noteBookmark` ref；`handleNoteSave(text)` → `bookmarkStore.updateBookmark(id, { description: text })` → 关对话框（错误提示由 request.js 拦截器统一处理，与现有对话框一致）

### `BookmarkCard.vue` 悬浮弹窗

- `mouseenter`：仅当 `props.bookmark.description` 非空白时启动 1000ms 定时器
- 定时器触发：读卡片 `getBoundingClientRect()`，teleport 到 body 的 `.note-popover`（fixed，z-index 低于菜单的 9999，取 9990），优先显示在卡片下方，越界翻转到上方/左右钳制（视口边缘保护，右键菜单都没有做但弹窗内容更高更该做）
- 内容：`v-html="renderMarkdown(bookmark.description)"`，max-width ~360px、max-height ~40vh、overflow auto
- `mouseleave`：清定时器 + 关弹窗；弹窗本体 `pointer-events: none`（悬浮预览是只读的，鼠标移进弹窗不劫持焦点，直接跟随卡片 mouseleave 消失，避免"想点弹窗里链接"和"弹窗挡住相邻卡片"的两难）
- 组件卸载（拖拽排序中卡片可能被移除）时清理定时器
- 样式：参照 BookmarkGrid 的双 `<style>` 块模式，teleport 元素样式写在非 scoped 块；配色用 App.vue `:root` CSS 变量贴合暖色极简主题

### i18n 键（两个语言文件同步加）

`bookmarks.contextMenu.note`（备注 / Note）、`bookmarks.noteDialog.title`、`bookmarks.noteDialog.placeholder`、`bookmarks.noteDialog.edit`、`bookmarks.noteDialog.preview`

## 权衡与风险

| 决策 | 理由 | 放弃方案的代价 |
|------|------|----------------|
| 复用 description 而非新加 note 列 | DTO/ES/导出/搜索全部现成，javadoc 本就注释"书签描述/备注" | 新列要动 8+ 文件且 ES 需重建 mapping |
| markdown-it（~100KB）而非 md-editor-v3 | 用户会 MD 语法不要工具栏；渲染库同时服务预览与悬浮弹窗 | md-editor-v3 体积 5 倍且需主题定制 |
| `html: false` + linkify | XSS 防线在渲染器内建，不引入 DOMPurify 额外依赖 | 若未来要允许内嵌 HTML 需另加净化器 |
| 空串=清空、null=不变 | 沿用现有部分更新语义，不改后端 | 无 |
| 悬浮弹窗 pointer-events: none | 消除弹窗与卡片 hover 状态互相纠缠 | 弹窗内链接不可点（点击应打开书签的场景优先） |

## 兼容与回滚

- V8 为列拓宽，幂等数据保留，回滚即 `ALTER TABLE bookmark MODIFY COLUMN description VARCHAR(500)`（超长数据需先截断，实际不会发生）
- 前端回滚 = 移除菜单项/两处新组件/依赖，无数据耦合
- ES 无 mapping 变更、无 reindex 需求
