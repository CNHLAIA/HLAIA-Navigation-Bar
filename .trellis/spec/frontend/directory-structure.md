# 目录结构规范

> 本项目前端代码的组织方式和文件命名约定。

---

## 概述

前端是一个 Vue 3 + Vite 单页应用，位于 `frontend/` 目录。采用 Composition API（`<script setup>`）+ Pinia 状态管理 + Element Plus UI 组件库。所有源码位于 `frontend/src/` 下，按功能职责分目录。

---

## 目录布局

```
frontend/
├── index.html                  # HTML 入口（Vite 注入点）
├── package.json                # 依赖管理
├── package-lock.json           # 锁定依赖版本
├── vite.config.js              # Vite 构建配置（@ 路径别名、开发代理）
└── src/
    ├── main.js                 # 应用入口（createApp + 插件注册）
    ├── App.vue                 # 根组件（全局样式 + <router-view>）
    ├── assets/                 # 静态资源（图片、SVG）
    │   ├── hero.png
    │   ├── vite.svg
    │   └── vue.svg
    ├── api/                    # API 请求模块（按后端模块拆分）
    │   ├── request.js          # Axios 实例 + 拦截器（JWT + 刷新 Token）
    │   ├── auth.js             # 认证 API（login/register/logout/refresh）
    │   ├── bookmark.js         # 书签 API（CRUD/排序/批量/导入）
    │   ├── folder.js           # 文件夹 API（CRUD/排序/移动）
    │   ├── staging.js          # 暂存区 API（CRUD/过期/移动到文件夹）
    │   ├── user.js             # 用户信息 API（profile/密码修改）
    │   └── admin.js            # 管理员 API（用户列表/封禁/文件夹查看）
    ├── components/             # 可复用组件（PascalCase 命名）
    │   ├── NavBar.vue          # 顶部导航栏（Logo/链接/语言切换/用户菜单）
    │   ├── FolderTree.vue      # 文件夹树（递归渲染 + 拖拽 + 右键菜单）
    │   ├── FolderBreadcrumb.vue # 面包屑导航（路径段可点击）
    │   ├── FolderPickerDialog.vue # 文件夹选择弹窗（移动书签用）
    │   ├── BookmarkCard.vue    # 书签卡片（Favicon/标题/URL/选中态）
    │   ├── BookmarkGrid.vue    # 书签网格（Grid 布局 + 拖拽 + 批量操作）
    │   ├── BatchToolbar.vue    # 批量操作工具栏（删除/复制/全选）
    │   └── StagingList.vue     # 暂存区列表（网格 + 倒计时 + 操作按钮）
    ├── views/                  # 页面级视图（路由对应）
    │   ├── LoginView.vue       # 登录页
    │   ├── RegisterView.vue    # 注册页
    │   ├── MainView.vue        # 主页面（双栏：文件夹树 + 书签网格）
    │   ├── StagingView.vue     # 暂存区页
    │   ├── SettingsView.vue    # 个人设置页
    │   ├── NotFoundView.vue    # 404 页面
    │   └── admin/              # 管理员页面（子目录）
    │       ├── UserListView.vue    # 用户列表
    │       └── UserDetailView.vue  # 用户详情
    ├── stores/                 # Pinia Store（Setup Store 风格）
    │   ├── auth.js             # 认证状态（login/register/logout）
    │   ├── bookmark.js         # 书签状态（CRUD + 多选 + 批量操作）
    │   ├── folder.js           # 文件夹状态（树形数据 + 面包屑路径）
    │   └── staging.js          # 暂存区状态（CRUD + 过期管理）
    ├── router/                 # Vue Router 路由配置
    │   └── index.js            # 路由定义 + 全局守卫（auth/admin 检查）
    ├── i18n/                   # 国际化（vue-i18n）
    │   ├── index.js            # createI18n 配置
    │   ├── zh-CN.js            # 中文翻译
    │   └── en-US.js            # 英文翻译
    └── utils/                  # 工具函数
        └── auth.js             # Token 存储（sessionStorage）+ JWT 解码
```

---

## 浏览器扩展

扩展代码位于项目根目录的 `extension/`，与前端独立：

```
extension/
├── manifest.json               # Manifest V3 配置
├── background.js               # Service Worker（右键菜单 + API 调用）
├── generate-icons.js           # 图标生成脚本
├── icons/                      # 扩展图标（16/48/128px）
└── options/
    ├── options.html            # 扩展设置页（登录/服务器地址配置）
    └── options.js              # 设置页逻辑
```

扩展不依赖前端框架，使用原生 JS + `chrome.*` API。

---

## 命名约定

### 文件命名

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| 视图组件 | PascalCase + `View.vue` | `LoginView.vue`, `UserListView.vue` |
| 通用组件 | PascalCase + `.vue` | `NavBar.vue`, `BookmarkCard.vue` |
| Pinia Store | camelCase + `.js` | `auth.js`, `bookmark.js` |
| API 模块 | camelCase + `.js` | `auth.js`, `folder.js` |
| 工具函数 | camelCase + `.js` | `auth.js` |
| i18n 翻译 | 语言代码 + `.js` | `zh-CN.js`, `en-US.js` |

### 组件命名

- 组件文件名使用 PascalCase：`BookmarkCard.vue`（不使用 `bookmark-card.vue`）
- 组件内 name 选项只在 `defineComponent` 场景使用（如 FolderTree 中的递归子组件）
- 页面视图统一以 `View` 结尾：`MainView.vue`, `SettingsView.vue`

### JS 变量和函数命名

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| Pinia composable | `use` + 名称 + `Store` | `useAuthStore`, `useBookmarkStore` |
| API 函数 | 动词 + 名词 | `getBookmarks`, `createFolder`, `batchDeleteBookmarks` |
| Store action | 动词 + 名词（驼峰） | `fetchTree`, `createBookmark`, `toggleSelect` |
| Store state | 名词（驼峰） | `folderTree`, `selectedIds`, `loading` |
| 事件处理函数 | `handle` + 动作 | `handleLogin`, `handleCardClick`, `handleDragEnd` |
| 计算属性 | 名词/形容词 | `currentFolder`, `hasSelection`, `isAllSelected` |
| 常量 | 大写下划线 | `TOKEN_KEY`, `REFRESH_KEY` |

---

## 新增功能的文件放置规则

添加新功能时（例如新增"标签"功能），按以下规则放置文件：

| 文件类型 | 放置位置 | 示例 |
|----------|----------|------|
| API 模块 | `src/api/` | `tag.js` |
| Pinia Store | `src/stores/` | `tag.js` |
| 组件 | `src/components/` | `TagBadge.vue`, `TagSelector.vue` |
| 页面视图 | `src/views/` | `TagManageView.vue` |
| 路由 | `src/router/index.js` | 在 routes 数组中添加 |
| 翻译 | `src/i18n/zh-CN.js` + `en-US.js` | 在对象中添加 tag 命名空间 |

---

## 路径别名

Vite 配置中 `@` 指向 `src/` 目录：

```js
// vite.config.js
resolve: {
  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url))
  }
}
```

所有 import 使用 `@/` 前缀引用 src 内文件：

```js
import { useAuthStore } from '@/stores/auth'
import { getBookmarks } from '@/api/bookmark'
import NavBar from '@/components/NavBar.vue'
```

不使用相对路径（`../stores/auth`）来跨目录引用。
