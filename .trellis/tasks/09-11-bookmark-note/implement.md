# Implement: Bookmark note remarks

> 顺序执行；每步末尾有验证。后端改动极小（2 文件），前端为主。

## 1. 后端

- [x] 1.1 新建 `src/main/resources/db/migration/V8__widen_bookmark_description.sql`：
  `ALTER TABLE \`bookmark\` MODIFY COLUMN \`description\` TEXT COMMENT '书签备注(Markdown)';`（带中文学习注释，风格参照 V4）
  > 已完成：含中文注释头（说明拓宽原因 + 参照 V4 先例），SQL 与设计一致
- [x] 1.2 `BookmarkCreateRequest.java:58` 附近给 `description` 加 `@Size(max = 10000)` + 中文注释（import `jakarta.validation.constraints.Size`）
  > 已完成：含教学注释（V8 拓宽后 DB 限制消失、Bean Validation 兜底、DTO 双用途一处生效）
- [x] 验证：`mvn -q compile` 通过；启动后端（依赖本机 Docker MySQL）确认 Flyway 应用 V8、`SHOW COLUMNS FROM bookmark LIKE 'description'` 为 TEXT
  > `mvn -q compile` 通过（exit 0）；后端启动 + Flyway 应用 V8 + SHOW COLUMNS 检查**待手动验证**（本环境不启动 MySQL/后端）

## 2. 前端基础

- [x] 2.1 `frontend/` 下 `npm install markdown-it`
  > 已完成：markdown-it@15.0.2（带 linkify-it@6）；npm audit 报告的 5 个高危漏洞均为 axios/vite/postcss 等既有依赖，与本次新增无关
- [x] 2.2 新建 `frontend/src/utils/markdown.js`：markdown-it 实例（`html:false, linkify:true, breaks:true`）+ 覆盖 `link_open` 渲染规则加 `target="_blank" rel="noopener noreferrer"` + 导出 `renderMarkdown(text)`（参照组件规范 JSDoc 注释）
  > 已完成；另发现并修复：linkify-it v6 将 fuzzyLink 默认改为 false（www.xxx 裸域名不再链接），已显式 `md.linkify.set({ fuzzyLink: true })` 恢复；node 冒烟测试验证链接属性/HTML 转义/br 换行均正确
- [x] 验证：`npm run build` 通过（新依赖打包无报错）
  > 通过（exit 0）；>500kB chunk 警告为既有问题（基线构建同样存在，router chunk 929.67kB → 929.68kB 几乎无增量）

## 3. 备注编辑面板

- [x] 3.1 新建 `frontend/src/components/NoteDialog.vue`：受控对话框（props `visible`/`bookmark`，emits `update:visible`/`save`），编辑/预览切换 + textarea + v-html 预览，样式含 `.note-dialog` 全局块与 ≤768px 媒体查询（模式参照 FolderPickerDialog.vue 与 BookmarkGrid.vue:205-245 的 bookmark-dialog）
  > 已完成：el-radio-button 编辑/预览切换（header 右侧）、textarea rows=10 + maxlength=10000（与后端 @Size 对齐）、预览区 min-height 与编辑态齐平防跳版、saving prop 驱动保存按钮 loading、watch visible 初始化（`?? ''`）
- [x] 3.2 i18n：`zh-CN.js` / `en-US.js` 在 `bookmarks.contextMenu.*`（zh-CN.js:88-96）旁加 `note` 键，`bookmarks.noteDialog.*`（title/placeholder/edit/preview）双语
  > 已完成：note 键加在 edit 之后（与菜单项顺序一致）；noteDialog 块加在 editDialog 之后
- [x] 验证：`npm run build` 通过
  > 通过（exit 0）

## 4. 入口接线（BookmarkGrid.vue）

- [x] 4.1 右键菜单（170-202 行）新增「备注」ctx-item（i18n 键），点击 `handleEditNote(contextMenu.bookmark)`：关菜单、记 `noteBookmark`、开 `noteDialogVisible`
  > 已完成：菜单项带同款 14x14 stroke SVG 图标（文档+行线条），置于「编辑」之后
- [x] 4.2 模板挂 `<NoteDialog v-model:visible="noteDialogVisible" :bookmark="noteBookmark" @save="handleNoteSave" />`
  > 已完成：挂载于 FolderPickerDialog 之后，另传 `:saving="noteSaving"`
- [x] 4.3 `handleNoteSave(text)`：`await bookmarkStore.updateBookmark(noteBookmark.id, { description: text })` 成功后关对话框（失败留在面板，loading 态由对话框 save 按钮）
  > 已完成：try/catch/finally，成功 toast + 关弹窗；catch 留面板（拦截器已提示，注释说明防止未处理 Promise 拒绝）
- [x] 验证：`npm run dev` 手测——右键菜单出「备注」；写 MD 保存；重开内容一致；空串保存后菜单再开为空
  > **待手动验证**（implement 子代理不启动 dev server）；代码路径与既有书签编辑对话框一致

## 5. 悬浮预览（BookmarkCard.vue）

- [x] 5.1 mouseenter/mouseleave：description 非空白时启动/清除 1000ms 定时器；触发时 `getBoundingClientRect()` 定位 teleport 弹窗（下方优先，视口钳制，可翻转上方）
  > 已完成：非空白（trim）才计时；两阶段定位（先卡片下方 provisional 渲染，nextTick 量实际宽高后钳制/翻转，微任务内完成无可见跳动）
- [x] 5.2 `.note-popover`：v-html 渲染、max-width 360px / max-height 40vh / overflow auto、`pointer-events: none`、z-index 9990（低于菜单 9999）、卸载时清定时器；样式放非 scoped 块，配色用全局 CSS 变量
  > 已完成：含 Markdown 元素基础排版（标题/列表/链接/行内码/代码块/引用/hr/img/strong），视觉语言对齐右键菜单（radius-lg/border/shadow-hover + 同款入场动画）；onBeforeUnmount 清定时器
- [x] 验证：`npm run dev` 手测——悬浮 ≥1s 弹渲染框、移开消失、空备注不弹、视口边缘不溢出、拖拽卡片无残留弹窗
  > **待手动验证**（同上）；定时器清理路径（mouseleave + onBeforeUnmount）已覆盖拖拽移除场景

## 6. 回归与收尾

- [ ] 6.1 回归：全局搜索命中备注文本（AC6）；中英文切换文案正常（AC5）；≤768px 视口长按出菜单且对话框可编辑（AC3）
  > **待手动验证**（ES 搜索/语言切换/移动端长按需运行环境）；i18n 键已双语齐备，移动端对话框宽度覆盖已按 bookmark-dialog 同款实现
- [x] 6.2 `npm run build` + `mvn -q compile` 全绿
  > 两者均通过（exit 0）；npm build 568ms，>500kB 警告为既有
- [ ] 6.3 运行 trellis-check 全量检查（最后一轮覆盖全部 AC）
  > 留待协调者调度 trellis-check 阶段（implement 子代理不自行启动检查代理）

## 风险文件与回滚点

- 触碰最多的现有文件：`BookmarkGrid.vue`（右键菜单/对话框区）、`BookmarkCard.vue`（新增交互）——回滚点 = git 单提交切分：建议「后端+前端基础」与「悬浮弹窗」可分两个 commit
- 迁移 V8 一旦在共享环境应用不可自动回退（列拓宽无数据损失，风险低）

## 验证命令速查

```bash
mvn -q compile                                  # 后端编译
cd frontend && npm run build                    # 前端构建
cd frontend && npm run dev                      # 手测入口
docker exec -it <mysql> mysql -u<user> -p -e "SHOW COLUMNS FROM hlaia_nav.bookmark LIKE 'description'"
```
