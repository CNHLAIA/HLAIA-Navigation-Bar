# 组件开发规范

> Vue 3 组件的实际编码模式与约定。

---

## 概述

本项目所有组件使用 **`<script setup>` 语法**（Vue 3 Composition API 编译期语法糖），搭配 Element Plus 组件库。组件按职责分为"页面视图"和"可复用组件"两类。

---

## SFC 文件结构

每个 `.vue` 文件严格按以下顺序组织三个顶层块：

```vue
<!--
  ComponentName.vue -- 简短描述

  功能/设计说明（可选，复杂组件写）
-->
<template>
  <!-- HTML 模板 -->
</template>

<script setup>
/**
 * 组件描述
 *
 * 职责：
 * - 功能1
 * - 功能2
 */
import { ref, computed, watch, onMounted } from 'vue'
// ... imports
// ... props/emits
// ... state
// ... computed
// ... lifecycle
// ... methods
</script>

<style scoped>
/* CSS 样式 */
</style>
```

要点：
- **`<template>` 在前**，`<script setup>` 在后，`<style scoped>` 在最后
- 注释使用 HTML 注释（`<!-- -->`）写在 `<template>` 前，解释组件功能
- `<script setup>` 内用 JSDoc 注释（`/** */`）解释逻辑

### 多 `<style>` 块

当需要同时使用 scoped 和全局样式时，允许两个 `<style>` 块。实际案例如 `BookmarkGrid.vue` 和 `StagingView.vue`：

```vue
<style scoped>
/* 组件内部样式 */
</style>

<style>
/* 全局样式（用于 el-dialog 等 teleport 到 body 的元素） */
.bookmark-dialog .el-dialog { ... }
.card-context-menu { ... }
</style>
```

---

## `<script setup>` 模式

### 顶层变量自动暴露

`<script setup>` 中的顶层变量、函数、import 的组件都会自动暴露给模板，不需要 `return`。

```js
// 直接在 template 中使用
const sidebarOpen = ref(false)
const authStore = useAuthStore()

// 不需要 export default { setup() { return { ... } } }
```

### import 自动注册

导入的组件直接在 template 中使用，不需要 `components` 选项：

```js
import NavBar from '@/components/NavBar.vue'
import FolderTree from '@/components/FolderTree.vue'
// template 中直接 <NavBar /> <FolderTree />
```

---

## Props 定义

使用 `defineProps()` 宏定义 props，使用对象语法并包含类型和默认值：

```js
// 推荐写法（带类型和默认值）
const props = defineProps({
  bookmark: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  folderId: {
    type: Number,
    default: null
  },
  path: {
    type: Array,
    default: () => []
  }
})
```

实际参考文件：
- `frontend/src/components/BookmarkCard.vue` -- Object/Boolean props
- `frontend/src/components/FolderBreadcrumb.vue` -- Array props with default
- `frontend/src/components/NavBar.vue` -- Boolean props with default
- `frontend/src/components/BatchToolbar.vue` -- Boolean/Number props

### Props 使用约定

- 在 `<script setup>` 中通过 `props.xxx` 访问（需要赋值给变量时）
- 在 `<template>` 中直接用 prop 名访问（不需要 `props.` 前缀）
- 使用 `watch(() => props.xxx, ...)` 监听 prop 变化

---

## Emits 定义

使用 `defineEmits()` 宏定义事件名数组：

```js
// 推荐写法
const emit = defineEmits(['navigate', 'select', 'delete'])

// 在模板中直接 $emit
// <button @click="$emit('delete', item)">Delete</button>

// 在 script 中调用
function handleConfirm() {
  emit('confirm', selectedId.value)
}
```

实际参考文件：
- `frontend/src/components/FolderBreadcrumb.vue` -- `defineEmits(['navigate'])`
- `frontend/src/components/BatchToolbar.vue` -- `defineEmits(['delete', 'copy', 'toggle-select-all', 'dismiss'])`
- `frontend/src/components/StagingList.vue` -- `defineEmits(['move-to-folder', 'set-expiry', 'delete'])`

### 事件命名约定

- 使用 kebab-case：`move-to-folder`, `toggle-select-all`
- 模板中可直接 `$emit('delete', item)` 简写
- 复杂逻辑在 script 中定义函数再 emit

---

## 组件通信模式

### 父 -> 子：Props

```vue
<!-- 父组件 -->
<BookmarkGrid
  :folder-id="folderStore.currentFolderId"
  :folder-name="currentFolderName"
/>

<!-- 子组件 -->
<script setup>
const props = defineProps({
  folderId: { type: Number, default: null },
  folderName: { type: String, default: '' }
})
</script>
```

### 子 -> 父：Events

```vue
<!-- 父组件 -->
<FolderBreadcrumb
  :path="folderStore.folderPath"
  @navigate="handleBreadcrumbNavigate"
/>

<!-- 子组件 -->
<script setup>
const emit = defineEmits(['navigate'])
function handleNavigate(segment) {
  emit('navigate', segment.id)
}
</script>
```

### 跨组件：Pinia Store

```js
// 任何组件都可以直接使用 store
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()

// 直接读取 state
bookmarkStore.bookmarks
folderStore.currentFolderId

// 调用 action
await bookmarkStore.fetchBookmarks(folderId)
bookmarkStore.toggleSelect(bookmark.id)
```

实际参考：`BookmarkGrid.vue` 直接操作 `useBookmarkStore()` 和 `useFolderStore()`。

### v-model 双向绑定

用于弹窗等需要父子同步状态的场景：

```vue
<!-- 父组件 -->
<FolderPickerDialog
  v-model:visible="moveDialogVisible"
  :title="t('bookmarks.moveDialog.title')"
  @confirm="handleMoveConfirm"
/>

<!-- 子组件 FolderPickerDialog.vue -->
<script setup>
const props = defineProps({
  visible: Boolean,
  title: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'confirm'])
// 通过 emit('update:visible', false) 通知父组件更新
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('update:visible', false)"
  />
</template>
```

---

## 递归组件

当组件需要递归渲染（如文件树），使用 `defineComponent` + `h()` 渲染函数，而非 SFC 递归：

```js
// 在 FolderTree.vue 内部定义递归子组件
const FolderTreeNode = defineComponent({
  name: 'FolderTreeNode',
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    selectedId: { type: Number, default: null }
  },
  emits: ['select', 'rename', 'delete', 'create-sub', 'drag-end'],
  setup(props, { emit }) {
    // ...
    return () => {
      // h() 渲染函数中递归引用 FolderTreeNode 自身
      return h('div', { class: 'tree-node-wrapper' }, [
        h('div', { class: 'tree-node' }, [...]),
        hasChildren && expanded.value
          ? h(VueDraggable, { ... }, {
              default: () => props.node.children.map(child =>
                h(FolderTreeNode, { key: child.id, node: child, depth: props.depth + 1, ... })
              )
            })
          : null
      ])
    }
  }
})
```

实际参考文件：`frontend/src/components/FolderTree.vue`（`FolderTreeNode` 定义在第 252-388 行）。

---

## Element Plus 图标用法

本项目**不安装 `@element-plus/icons-vue`**，而是在需要图标的组件中用 `shallowRef` + `h()` 自定义 SVG 图标：

```js
import { shallowRef, h } from 'vue'

const UserIcon = shallowRef({
  render: () => h('svg', {
    viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2'
  }, [
    h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }),
    h('circle', { cx: '12', cy: '7', r: '4' })
  ])
})
```

然后在 `el-input` 中使用：

```vue
<el-input :prefix-icon="UserIcon" />
```

- 使用 `shallowRef`（而非 `ref`）避免 Vue 对 SVG 对象做深层响应式代理的性能开销
- 导航栏和按钮中的图标直接内联 `<svg>` 标签，不用 `prefix-icon`

实际参考文件：`frontend/src/views/LoginView.vue`（第 102-103 行）。

---

## 表单校验模式

使用 Element Plus 的 `el-form` + 校验规则，配合 `formRef.value.validate()` 异步校验：

```js
const form = reactive({ username: '', password: '' })
const formRef = ref(null)

// 校验规则：message 使用函数形式支持 i18n 切换
const rules = {
  username: [
    { required: true, message: () => t('auth.login.validation.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: () => t('auth.login.validation.usernameLength'), trigger: 'blur' }
  ]
}

// 提交时校验
async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // ... 提交逻辑
}
```

自定义校验器（如确认密码一致性检查）：

```js
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error(t('auth.register.validation.passwordMismatch')))
  } else {
    callback()
  }
}

const rules = {
  confirmPassword: [
    { required: true, message: () => t('...'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}
```

实际参考文件：
- `frontend/src/views/LoginView.vue` -- 基本表单校验
- `frontend/src/views/RegisterView.vue` -- 自定义校验器 + i18n
- `frontend/src/views/SettingsView.vue` -- 双表单独立校验

---

## 组件设计原则

### 单一职责

- `BookmarkCard.vue` -- 只负责渲染单张书签卡片，不包含 API 调用
- `BatchToolbar.vue` -- 纯展示 + 事件派发，不包含业务逻辑
- `BookmarkGrid.vue` -- 组合组件，负责协调 BookmarkCard/BatchToolbar/FolderPickerDialog

### 对话框复用

同一个对话框用于多种操作（创建/编辑），通过 `dialogMode` 变量切换行为：

```js
const dialogVisible = ref(false)
const dialogMode = ref('create')  // 'create' | 'edit'
const dialogForm = ref({ title: '', url: '', iconUrl: '', id: null })

function openCreateDialog() {
  dialogMode.value = 'create'
  dialogForm.value = { title: '', url: '', iconUrl: '', id: null }
  dialogVisible.value = true
}

function openEditDialog(bookmark) {
  dialogMode.value = 'edit'
  dialogForm.value = { ...bookmark }
  dialogVisible.value = true
}

async function handleDialogConfirm() {
  if (dialogMode.value === 'create') {
    await bookmarkStore.createBookmark(...)
  } else {
    await bookmarkStore.updateBookmark(id, ...)
  }
  dialogVisible.value = false
}
```

实际参考文件：
- `frontend/src/components/BookmarkGrid.vue`（创建/编辑书签对话框）
- `frontend/src/components/FolderTree.vue`（创建/重命名文件夹对话框）

### 拖拽排序模式

使用 `vue-draggable-plus` 的 `VueDraggable` 组件，配合 writable computed：

```js
const gridData = computed({
  get: () => bookmarkStore.bookmarks,
  set: (val) => { bookmarkStore.bookmarks = val }
})
```

```vue
<VueDraggable
  v-model="gridData"
  :animation="200"
  ghost-class="grid-drag-ghost"
  @end="handleDragEnd"
>
  <BookmarkCard v-for="element in gridData" :key="element.id" ... />
</VueDraggable>
```

拖拽结束后提交排序：

```js
async function handleDragEnd() {
  const sortData = bookmarkStore.bookmarks.map((bm, index) => ({
    id: bm.id, sortOrder: index
  }))
  await bookmarkStore.sortBookmarks(sortData)
}
```

实际参考文件：
- `frontend/src/components/BookmarkGrid.vue`（书签拖拽排序）
- `frontend/src/components/FolderTree.vue`（文件夹拖拽排序 + 嵌套层级）
