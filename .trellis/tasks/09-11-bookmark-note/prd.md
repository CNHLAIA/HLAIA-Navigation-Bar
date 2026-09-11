# Bookmark note remarks

## Goal

为每个标签块（书签卡片）新增 Markdown 备注功能：

- 右键菜单新增「备注」选项，点击弹出备注面板，可编辑支持 Markdown 语法的备注
- 鼠标悬浮标签块 1 秒后弹出只读备注框（渲染后的 Markdown）
- 手机端：长按标签块呼出的同一菜单中「备注」可查看和编辑备注（响应式同一菜单）

## Background

- 项目此前零 Markdown 依赖；前端一贯轻量依赖风格（不装图标库、手写 SVG）
- 后端 `bookmark.description` 字段已全链路打通且前端从未使用：
  - 实体 `Bookmark.java:58`、请求 DTO `BookmarkCreateRequest.java:58`、响应 DTO `BookmarkResponse.java:43`
  - ES 已索引（ik 分词，`BookmarkDocument.java:106-107`）且已在搜索 multiMatch 中（`SearchService.java:72`）
  - 导出 `ExportBookmarkNode` 已包含；Netscape HTML 导入格式本身不含备注，无需改
  - DB 列为 `VARCHAR(500)`（V1:36），对 Markdown 长文本偏紧；拓宽列宽有先例 `V4__alter_icon_url_to_text.sql`
- `BookmarkService.updateBookmark:184-188` 部分更新语义：null = 不变，非 null（含空串）= 覆盖 → 清空备注发 `description: ''` 即可
- 前端右键菜单为自定义 teleport 菜单（`BookmarkGrid.vue:170-202`），手机长按触发同一 contextmenu 事件，无独立移动端组件
- 卡片（`BookmarkCard.vue`）无任何 tooltip/popover 基础设施，且 `.bookmark-card` 有 `overflow: hidden`，弹窗必须 teleport 到 body
- 书签列表接口已返回 description，BookmarkCard 的 bookmark prop 即含该字段，悬浮弹窗数据无需新增请求

## Requirements

- R1 存储：复用 `bookmark.description` 列承载备注，新增 V8 Flyway 迁移拓宽为 TEXT
- R2 右键菜单：新增「备注」菜单项 → 备注编辑面板（纯 textarea，无工具栏；编辑/预览切换），保存走现有 `PUT /api/bookmarks/{id}` 提交 `description`
- R3 悬浮预览：鼠标悬浮标签块 ≥1 秒且备注非空 → 弹出只读 Markdown 渲染框；移开鼠标即消失；备注为空不弹
- R4 手机端：同一长按菜单「备注」→ 同一编辑面板（对话框响应式适配），无悬浮预览
- R5 i18n：zh-CN / en-US 双语键
- R6 清空备注保存后 description 为空串（非 null），悬浮不再弹框

## Technical Notes

- 用户本人熟悉 Markdown 语法：编辑面板用纯 textarea（markdown-it 仅做解析渲染），不需要工具栏/快捷按钮
- Markdown 渲染统一 `html: false`（防 XSS），开启 linkify；预览区链接按项目外链约定加 `target="_blank" rel="noopener noreferrer"`

## Acceptance Criteria

- [ ] AC1: 右键标签块 → 菜单出现「备注」项 → 面板中写 Markdown 备注保存 → 重新打开内容一致
- [ ] AC2: 有备注的标签块，鼠标悬浮 ≥1 秒弹出渲染后的 Markdown 只读框，移开即消失；无备注不弹
- [ ] AC3: 手机端（≤768px 视口 / 触屏长按）菜单「备注」→ 可查看和编辑备注
- [ ] AC4: 清空备注保存后，悬浮不再弹框（description 为空串）
- [ ] AC5: 中英文界面菜单/面板文案均正常显示
- [ ] AC6: 全局搜索仍能命中备注内容（description 已在 ES 索引中，回归验证）
- [ ] AC7: 备注支持常见 Markdown 元素（标题/列表/粗斜体/链接/代码块/引用）正确渲染

## Out of Scope

- ES 搜索行为变更（description 已索引，零改动）
- Netscape HTML 导入解析备注（格式不支持）
- 现有「编辑」书签对话框中暴露备注字段（入口唯一：右键/长按菜单「备注」）
- 文件夹备注、工具栏式 Markdown 编辑器
