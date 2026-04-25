# 质量与风格规范

> CSS/样式约定、国际化模式、代码审查清单。

---

## 概述

本节涵盖前端开发中除组件逻辑和 API 以外的横切关注点：CSS 样式体系、国际化、图标使用、响应式设计以及代码审查时的必检项。

---

## CSS 设计体系

### 全局 CSS 变量

所有设计 Token 定义在 `frontend/src/App.vue` 的 `:root` 中：

```css
:root {
  /* 背景色 */
  --hlaia-bg: #FAFAF8;             /* 页面主背景：暖白色 */
  --hlaia-surface: #FFFFFF;         /* 卡片/面板背景：纯白 */
  --hlaia-surface-light: #F5F4F0;   /* 输入框/浅层背景 */

  /* 边框 */
  --hlaia-border: #E8E4DF;

  /* 主色调：蓝色系 */
  --hlaia-primary: #4A7FC7;
  --hlaia-primary-light: #6B9BD2;
  --hlaia-primary-dark: #3A6BAA;

  /* 文字色 */
  --hlaia-text: #2C3E50;
  --hlaia-text-muted: #8B9DAF;
  --hlaia-text-light: #B0BEC5;

  /* 语义色 */
  --hlaia-danger: #E74C3C;
  --hlaia-warning: #F5A623;
  --hlaia-success: #27AE60;
  --hlaia-accent: #E8927C;

  /* 阴影 */
  --hlaia-shadow: 0 2px 8px rgba(44, 62, 80, 0.08);
  --hlaia-shadow-hover: 0 4px 16px rgba(44, 62, 80, 0.12);

  /* 圆角 */
  --hlaia-radius: 8px;
  --hlaia-radius-lg: 12px;
}
```

### 新增颜色规则

- 优先使用已有 CSS 变量
- 新增颜色必须添加到 `:root` 变量中，不允许硬编码色值（全局样式覆盖除外）
- 全局样式覆盖 Element Plus 时允许使用硬编码色值（因为这些是针对特定组件的微调）

### 字体

```css
font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif;
```

- 所有文本元素统一指定 `font-family: 'DM Sans', sans-serif`
- 不使用 Element Plus 默认字体

---

## Scoped 样式

### 基本规则

- 所有组件使用 `<style scoped>`，防止样式泄漏
- 全局样式只在 `App.vue` 和需要 teleport 的组件中使用无 scoped 的 `<style>` 块

### Element Plus 样式穿透

使用 `:deep()` 穿透 scoped 自定义 Element Plus 组件样式：

```css
.login-form :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.15);
}
```

### Teleport 元素的样式

使用 `teleport to="body"` 的元素（如右键菜单）无法被 scoped 样式影响，需要无 scoped 的 `<style>` 块：

```vue
<template>
  <teleport to="body">
    <div class="card-context-menu" ...>...</div>
  </teleport>
</template>

<!-- 无 scoped：右键菜单在 body 层级，scoped 无法命中 -->
<style>
.card-context-menu {
  position: fixed;
  z-index: 9999;
  background: #FFFFFF;
  border: 1px solid #E8E4DF;
}
</style>
```

实际参考文件：`frontend/src/components/BookmarkGrid.vue`（第 1010-1112 行）。

### Element Plus 对话框样式覆盖

对话框通过 `append-to-body` 渲染到 body 层级。本项目中有两种覆盖方式：

**方式一：无 scoped 样式（BookmarkGrid、StagingView 使用）**

用于页面视图组件中的对话框，直接用 class 选择器覆盖：

```vue
<el-dialog class="bookmark-dialog" ...>

<!-- 无 scoped -->
<style>
.bookmark-dialog .el-dialog {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
}
.bookmark-dialog .el-input__wrapper {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
}
</style>
```

**方式二：scoped 内 :deep() 穿透（FolderTree 使用）**

用于嵌套在其他组件内部的对话框，在 `<style scoped>` 中用 `:deep()` 穿透：

```vue
<el-dialog class="folder-dialog" ...>

<style scoped>
.folder-dialog :deep(.el-dialog) {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
}
.folder-dialog :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
}
</style>
```

实际参考文件：
- `frontend/src/components/BookmarkGrid.vue`（`.bookmark-dialog` 样式，无 scoped）
- `frontend/src/components/FolderTree.vue`（`.folder-dialog` 样式，scoped + `:deep()`）
- `frontend/src/views/StagingView.vue`（`.staging-dialog` 样式，无 scoped）

---

## CSS 类命名约定

### BEM-like 命名

不严格遵循 BEM，但使用一致的命名模式：

```css
/* 容器 */
.bookmark-grid-wrapper { }
.staging-layout { }
.settings-content { }

/* 组件内元素 */
.card-favicon { }
.card-info { }
.card-title { }
.card-url { }

/* 状态修饰符 */
.is-selected { }
.is-active { }
.is-open { }
.is-expiring-soon { }

/* 语义化按钮 */
.btn-delete { }
.btn-copy { }
.btn-view { }
.btn-ban { }

/* 区域划分 */
.sidebar-header { }
.sidebar-content { }
.sidebar-footer { }
.grid-top-bar { }
.grid-top-actions { }
```

### 状态 class 命名

- 使用 `is-` 前缀：`is-selected`, `is-active`, `is-open`, `is-expanded`
- 与 Vue 的动态 class 绑定配合：

```vue
<div :class="{ 'is-selected': selected, 'is-selecting': isSelecting }">
```

---

## 响应式设计

### 断点约定

| 断点 | 宽度 | 用途 |
|------|------|------|
| 移动端 | `max-width: 768px` | 手机、折叠菜单、单列 |
| 平板 | `769px - 1024px` | 缩小侧边栏、减少列数 |
| 桌面 | `min-width: 1025px` | 完整布局 |

### CSS Grid 响应式

书签和暂存区网格使用 CSS Grid + 媒体查询：

```css
.bookmark-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);  /* 桌面 4 列 */
  gap: 10px;
}

@media (max-width: 1200px) {
  .bookmark-grid { grid-template-columns: repeat(3, 1fr); }  /* 3 列 */
}

@media (max-width: 768px) {
  .bookmark-grid { grid-template-columns: repeat(2, 1fr); }  /* 2 列 */
}
```

### 移动端适配模式

- 侧边栏在移动端变为全屏覆盖层（position: fixed + transform）
- 操作按钮在移动端始终可见（桌面端 hover 才显示）
- 表格在移动端隐藏次要列

---

## 国际化（i18n）

### 配置

使用 vue-i18n 的 Composition API 模式：

```js
// frontend/src/i18n/index.js
const i18n = createI18n({
  legacy: false,  // 必须为 false，使用 Composition API
  locale: localStorage.getItem('hlaia-locale') || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS }
})
```

### 组件中使用

```js
const { t } = useI18n()
// 或同时获取 locale
const { t, locale } = useI18n()
```

### 翻译文件结构

翻译文件按功能模块组织，使用嵌套对象：

```js
// frontend/src/i18n/zh-CN.js
export default {
  common: { cancel: '取消', delete: '删除', ... },
  auth: {
    login: { subtitle: '...', validation: { ... }, toast: { ... } },
    register: { ... }
  },
  bookmarks: { loading: '...', empty: { ... }, contextMenu: { ... }, ... },
  folders: { ... },
  staging: { ... },
  admin: { users: { ... }, userDetail: { ... } },
  settings: { ... },
  notFound: { ... }
}
```

### 模板中使用

```vue
<template>
  <h1>{{ t('bookmarks.addBookmark') }}</h1>
  <p>{{ t('bookmarks.empty.noBookmarks') }}</p>
</template>
```

### 带参数的翻译

```js
// 翻译文件
'confirmMessage': '确定要删除选中的 {count} 个书签吗？'

// 组件中使用
t('bookmarks.batch.confirmMessage', { count })
```

### 表单校验中的 i18n

校验规则的 message 使用**函数形式**（而非字符串），确保语言切换时消息也更新：

```js
const rules = {
  username: [
    { required: true, message: () => t('auth.login.validation.usernameRequired'), trigger: 'blur' }
  ]
}
```

---

## 图标使用规范

### 导航栏和按钮中的图标

直接内联 SVG 标签，不依赖图标库：

```vue
<svg width="14" height="14" viewBox="0 0 14 14" fill="none">
  <path d="M7 3v8M3 7h8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
</svg>
```

### Element Plus 组件中的图标

使用 `shallowRef` + `h()` 创建自定义 SVG 图标组件（不安装 @element-plus/icons-vue）：

```js
const UserIcon = shallowRef({
  render: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
    h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }),
    h('circle', { cx: '12', cy: '7', r: '4' })
  ])
})
```

---

## 动画约定

### CSS 过渡

```css
transition: all 0.2s ease;        /* 默认交互过渡 */
transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);  /* 卡片悬停 */
transition: all 0.3s ease;        /* 长过渡（阴影、边框色） */
```

### Vue transition 组件

用于工具栏滑入/滑出：

```vue
<transition name="toolbar-slide">
  <div v-if="visible" class="batch-toolbar">...</div>
</transition>
```

```css
.toolbar-slide-enter-active,
.toolbar-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.toolbar-slide-enter-from { opacity: 0; transform: translateY(-12px); }
.toolbar-slide-leave-to { opacity: 0; transform: translateY(-8px); }
```

### 关键帧动画

```css
@keyframes spin { to { transform: rotate(360deg); } }           /* 加载旋转 */
@keyframes popIn { from { transform: scale(0); opacity: 0; } }  /* 选中勾选弹出 */
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } } /* 页面渐入 */
@keyframes shimmerMove { ... }                                    /* 骨架屏微光 */
```

---

## 禁止模式

### 不要做的事

| 禁止 | 原因 | 替代方案 |
|------|------|----------|
| 使用 `@element-plus/icons-vue` | 避免额外依赖 | `shallowRef` + `h()` 自定义 SVG 或内联 SVG |
| 使用 Options API | 项目统一 Composition API | `<script setup>` |
| 组件内直接调用 API（普通页面） | 通过 Store 中转 | Store action -> API function |
| 硬编码中文文本 | 不支持 i18n | `t('key')` |
| 使用 `localStorage` 存储 Token | 标签页不隔离 | `sessionStorage` |
| 使用相对路径跨目录引用 | 可读性差 | `@/` 路径别名 |
| 在 scoped 样式中使用 `/deep/` 或 `>>>` | 已废弃 | `:deep()` |
| 不写注释就提交代码 | 项目要求学习注释 | JSDoc + 行内注释 |

---

## 代码审查清单

提交前端代码前检查以下项目：

### 结构
- [ ] 文件放在正确的目录（api/stores/components/views）
- [ ] 文件命名符合约定（PascalCase.vue / camelCase.js）
- [ ] import 使用 `@/` 路径别名

### 组件
- [ ] 使用 `<script setup>` 而非 Options API
- [ ] Props 使用 `defineProps()` 并包含类型和默认值
- [ ] Events 使用 `defineEmits()` 声明
- [ ] 对话框使用 `dialogMode` 复用模式

### 状态管理
- [ ] 普通 CRUD 通过 Pinia Store -> API function
- [ ] API 函数导入使用别名（`as xxxApi`）
- [ ] 写操作后调用 fetch 刷新数据

### API
- [ ] 新 API 函数添加 JSDoc 注释（参数和返回值）
- [ ] 耗时操作设置更长的 timeout
- [ ] 错误处理遵循拦截器 + try/catch 模式

### 样式
- [ ] 使用 `<style scoped>`
- [ ] 颜色使用 CSS 变量（`var(--hlaia-xxx)`）
- [ ] Element Plus 穿透使用 `:deep()`
- [ ] teleport 元素用无 scoped 的 `<style>` 块

### 国际化
- [ ] 用户可见文本使用 `t('key')`
- [ ] 新翻译同时添加到 `zh-CN.js` 和 `en-US.js`
- [ ] 表单校验 message 使用函数形式 `() => t('...')`

### 响应式
- [ ] 移动端布局正确（768px 断点）
- [ ] 网格布局使用 CSS Grid + 媒体查询
