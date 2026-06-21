# 状态管理规范

> Pinia Store 的实际编码模式与约定。

---

## 概述

本项目使用 **Pinia** 作为状态管理库，全部采用 **Setup Store** 风格（函数式定义，类似 Composition API）。Store 作为 API 层和组件层之间的桥梁，组件不直接调用 API 函数（管理后台页面除外）。

---

## Store 文件清单

| 文件 | Store 名称 | 职责 |
|------|-----------|------|
| `frontend/src/stores/auth.js` | `auth` | 登录/注册/登出、JWT 状态恢复 |
| `frontend/src/stores/bookmark.js` | `bookmark` | 书签 CRUD、多选、批量操作、排序 |
| `frontend/src/stores/folder.js` | `folder` | 文件夹树、面包屑路径、CRUD、排序、移动 |
| `frontend/src/stores/staging.js` | `staging` | 暂存区 CRUD、过期管理、移动到文件夹 |

---

## Setup Store 模板

所有 Store 遵循统一的结构模板：

```js
/**
 * Pinia Xxx Store -- 描述
 *
 * 设计模式：Setup Store（Composition API 风格）
 * 使用 ref/reactive 定义状态，用普通函数定义 actions，用 computed 定义 getters。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getXxx as getXxxApi, createXxx as createXxxApi } from '@/api/xxx'

export const useXxxStore = defineStore('xxx', () => {
  // ---- State ----

  /** 状态描述 */
  const items = ref([])
  const loading = ref(false)

  // ---- Getters ----

  /** 派生状态描述 */
  const hasItems = computed(() => items.value.length > 0)

  // ---- Actions ----

  /**
   * 获取数据
   */
  async function fetchItems() {
    loading.value = true
    try {
      const res = await getXxxApi()
      items.value = res.data || []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建条目
   * @param {Object} data - 参数描述
   */
  async function createItem(data) {
    const res = await createXxxApi(data)
    await fetchItems() // 刷新列表
    return res.data
  }

  return {
    // state
    items,
    loading,
    // getters
    hasItems,
    // actions
    fetchItems,
    createItem
  }
})
```

---

## State 定义规范

### 使用 `ref()` 定义所有状态

```js
// 基本类型
const loading = ref(false)
const currentFolderId = ref(null)

// 数组
const bookmarks = ref([])

// Set（用于多选）
const selectedIds = ref(new Set())
```

### 状态恢复（Auth Store 特有）

Auth Store 在初始化时从 sessionStorage 恢复登录状态：

```js
// stores/auth.js
export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(!!getToken())
  const savedUser = getUserFromToken()
  const username = ref(savedUser?.username || savedUser?.sub || '')
  const nickname = ref(savedUser?.nickname || '')
  const role = ref(savedUser?.role || '')
  // ...
})
```

### 更新 Set 类型状态

直接创建新 Set 替换（而非 mutate），保持响应式追踪：

```js
function toggleSelect(id) {
  const newSet = new Set(selectedIds.value)
  if (newSet.has(id)) newSet.delete(id)
  else newSet.add(id)
  selectedIds.value = newSet  // 替换整个 Set
}
```

实际参考文件：`frontend/src/stores/bookmark.js`（第 173-181 行）。

---

## Getters 规范

使用 `computed()` 定义派生状态：

```js
// 简单派生
const hasSelection = computed(() => selectedIds.value.size > 0)
const isAdmin = computed(() => role.value === 'ADMIN')

// 过滤计算
const selectedBookmarks = computed(() => {
  return bookmarks.value.filter(b => selectedIds.value.has(b.id))
})

// 递归查找（文件夹树）
const currentFolder = computed(() => {
  if (!currentFolderId.value) return null
  return findNodeById(folderTree.value, currentFolderId.value)
})
```

---

## Actions 规范

### 异步 Action 模式

所有涉及 API 调用的 action 使用 async/await：

```js
async function fetchItems() {
  loading.value = true
  try {
    const res = await getXxxApi()
    items.value = res.data || []
  } finally {
    loading.value = false  // finally 确保无论成功失败都关闭 loading
  }
}
```

要点：
- `loading.value = true` 在 try 之前设置
- `loading.value = false` 在 `finally` 中设置（不是 catch 后）
- `res.data || []` 用 `|| []` 防止 null/undefined

### 写操作后刷新

创建/更新/删除后，重新拉取完整列表（而非本地操作）：

```js
async function createFolder(data) {
  const res = await createFolderApi(data)
  await fetchTree()  // 重新拉取整棵树
  return res.data
}
```

这是本项目的设计选择：**简单可靠**，优先全量刷新而非复杂的本地状态同步。

### 多 Store 联动

当操作影响多个 Store 时，在一个 action 中调用另一个 Store：

```js
// stores/bookmark.js
import { useFolderStore } from './folder'

async function moveBookmarks(bookmarkIds, targetFolderId) {
  await moveBookmarksApi(bookmarkIds, targetFolderId)
  try {
    if (currentFolderId.value) {
      await fetchBookmarks(currentFolderId.value)  // 刷新当前书签
    }
    const folderStore = useFolderStore()  // 在函数内部获取，避免循环依赖
    await folderStore.fetchTree()          // 刷新文件夹树
  } catch (e) {
    console.error('Failed to refresh after move:', e)
  }
}
```

要点：
- `useFolderStore()` 在函数**内部**调用，避免模块加载时的循环依赖问题
- 联动操作包裹在 try/catch 中，确保一个失败不影响另一个

### 同步 Action

纯本地状态操作不需要 async：

```js
function setCurrentFolder(id) {
  currentFolderId.value = id
}

function clearSelection() {
  selectedIds.value = new Set()
}

function selectAll() {
  selectedIds.value = new Set(bookmarks.value.map(b => b.id))
}
```

---

## API 命名别名导入

Store 导入 API 函数时使用别名，避免与 Store 内部同名函数冲突：

```js
import {
  getBookmarks as getBookmarksApi,
  createBookmark as createBookmarkApi,
  deleteBookmark as deleteBookmarkApi,
  sortBookmarks as sortBookmarksApi,
  batchDeleteBookmarks as batchDeleteApi
} from '@/api/bookmark'
```

Store 内部 action 名：`fetchBookmarks`, `createBookmark`, `deleteBookmark`
API 函数别名：`getBookmarksApi`, `createBookmarkApi`, `deleteBookmarkApi`

---

## 组件中使用 Store

```js
const bookmarkStore = useBookmarkStore()

// 读取 state
bookmarkStore.loading
bookmarkStore.bookmarks

// 读取 getter
bookmarkStore.hasSelection
bookmarkStore.isAllSelected

// 调用 action
await bookmarkStore.fetchBookmarks(folderId)
bookmarkStore.toggleSelect(bookmark.id)
bookmarkStore.clearSelection()
```

Store 实例可以在 `<script setup>` 顶层创建，不需要在 `onMounted` 中延迟创建。

---

## 管理员页面的例外

管理员页面（`UserListView.vue`, `UserDetailView.vue`）**直接调用 API 函数**，不通过 Pinia Store。这是因为管理员页面数据独立，不需要全局共享状态。

```js
// UserListView.vue -- 直接调用 API
import { getUsers, banUser, unbanUser } from '@/api/admin'

async function loadPage(page) {
  const res = await getUsers(page, pageSize.value)
  users.value = res.data.records || []
}
```

如果管理员功能变得复杂（需要跨组件共享状态），再考虑创建 `admin` Store。
