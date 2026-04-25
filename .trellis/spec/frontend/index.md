# Frontend Development Guidelines

> 本项目前端开发的实际编码规范索引。

---

## 概述

本目录包含前端开发的所有规范文档。每个文件记录了 Vue 3 + Pinia + Element Plus 前端中实际使用的编码约定，供 AI sub-agent 或新开发者参考。

---

## 规范索引

| 规范 | 描述 | 状态 |
|------|------|------|
| [目录结构](./directory-structure.md) | 文件组织、命名约定、路径别名 | Done |
| [组件规范](./component-guidelines.md) | Vue 3 组件模式、Props/Emits、递归组件、拖拽 | Done |
| [状态管理](./state-management.md) | Pinia Setup Store 模式、Store 与 API 交互 | Done |
| [API 模式](./api-patterns.md) | Axios 封装、拦截器、Token 刷新、错误处理 | Done |
| [质量规范](./quality-guidelines.md) | CSS 变量、i18n、响应式、禁止模式、审查清单 | Done |

---

## 关键约定速查

- **框架**: Vue 3.5 + Vite 8 + Pinia 3 + Element Plus 2.13
- **组件写法**: `<script setup>`（Composition API），不使用 Options API
- **Store 风格**: Setup Store（`defineStore('name', () => { ... })`），不使用 Options Store
- **UI 组件库**: Element Plus，图标使用 `shallowRef` + `h()` 自定义 SVG（不安装 @element-plus/icons-vue）
- **拖拽**: vue-draggable-plus，配合 writable computed + `@end` 回调提交排序
- **国际化**: vue-i18n Composition API 模式（`legacy: false`），默认中文
- **认证**: JWT 存储在 sessionStorage（标签页隔离），request.js 拦截器自动附加和刷新
- **API 响应**: 后端统一 `{ code, message, data }`，拦截器解包后调用方拿到 `res.data`
- **错误处理**: 拦截器全局提示 `ElMessage.error()`，组件层 try/catch 可覆盖
- **CSS**: 全局 CSS 变量定义在 App.vue `:root`，组件用 `<style scoped>` + `:deep()` 穿透
- **设计主题**: Warm Minimal Light（暖色极简亮色），主色 `#4A7FC7`
- **Token**: sessionStorage 存储（hlaia_access_token / hlaia_refresh_token）
- **路径别名**: `@` -> `src/`，所有跨目录 import 使用 `@/`
- **表单**: el-form + reactive + ref 校验，`formRef.value.validate()` 异步校验

---

## 核心参考文件

开发时最常需要参考的文件：

| 文件 | 用途 |
|------|------|
| `frontend/src/api/request.js` | Axios 实例和拦截器模板 |
| `frontend/src/stores/bookmark.js` | 最完整的 Store 示例（CRUD + 多选 + 批量 + 跨 Store 联动） |
| `frontend/src/components/BookmarkGrid.vue` | 最复杂的组件示例（Grid + 拖拽 + 对话框 + 右键菜单 + 导入） |
| `frontend/src/views/LoginView.vue` | 表单 + 校验 + i18n 的标准模板 |
| `frontend/src/App.vue` | 全局 CSS 变量和 Element Plus 样式覆盖 |
| `frontend/src/i18n/zh-CN.js` | 翻译文件结构参考 |
