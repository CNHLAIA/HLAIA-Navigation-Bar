<!--
  FolderTree.vue -- 递归文件夹树组件

  功能：
  - 递归渲染文件夹树形结构
  - 点击文件夹节点：通知父组件加载该文件夹的书签
  - 右键上下文菜单：重命名、删除、新建子文件夹
  - 顶部"新建文件夹"按钮
  - 拖拽排序和移动（使用 vue-draggable-plus）

  设计：
  - Warm Minimal Light 主题
  - 树形缩进，每级增加 padding
  - 展开/折叠图标（箭头）
  - 选中状态：主题蓝色高亮
  - 悬停：浅灰背景
-->
<template>
  <div class="folder-tree">
    <!-- 顶部：新建文件夹按钮 -->
    <div class="tree-header">
      <button class="new-folder-btn" @click="handleCreateRoot">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M7 3v8M3 7h8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span>{{ t('folders.newFolder') }}</span>
      </button>
    </div>

    <!-- 树形结构 -->
    <div v-if="folderStore.folderTree.length > 0" class="tree-body">
      <VueDraggable
        v-model="treeData"
        group="folders"
        :animation="200"
        ghost-class="drag-ghost"
        drag-class="drag-active"
        @end="handleDragEnd"
      >
        <FolderTreeNode
          v-for="element in folderStore.folderTree"
          :key="element.id"
          :node="element"
          :depth="0"
          :selected-id="folderStore.currentFolderId"
          @select="handleSelect"
          @rename="handleRename"
          @delete="handleDelete"
          @create-sub="handleCreateSub"
          @drag-end="handleDragEnd"
        />
      </VueDraggable>
    </div>

    <!-- 空状态 -->
    <div v-else class="tree-empty">
      <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
        <path d="M4 12l12-8 12 8v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V12z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M12 28V16h8v12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span>{{ t('folders.noFolders') }}</span>
    </div>

    <!-- 新建/重命名对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('folders.newFolderDialog.title') : t('folders.renameFolder')"
      width="360px"
      :append-to-body="true"
      class="folder-dialog"
      @close="dialogVisible = false"
    >
      <el-input
        v-model="dialogName"
        :placeholder="t('folders.folderName')"
        maxlength="100"
        clearable
        @keyup.enter="handleDialogConfirm"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogConfirm">
          {{ dialogMode === 'create' ? t('folders.create') : t('folders.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * FolderTree 组件
 *
 * 使用 vue-draggable-plus 实现拖拽排序。
 * 使用递归组件 FolderTreeNode 来渲染树的每一层。
 *
 * 关键设计：
 * - 树形数据由 Pinia Folder Store 管理
 * - 对话框（Dialog）复用：创建根文件夹、创建子文件夹、重命名都用同一个对话框
 * - 拖拽结束后调用 sortFolders API 更新排序
 */
import { ref, computed, h, defineComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueDraggable } from 'vue-draggable-plus'
import { useFolderStore } from '@/stores/folder'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const folderStore = useFolderStore()

// ---- 拖拽数据（本地可修改的副本） ----
// vue-draggable-plus 使用 v-model 进行双向绑定
// 这里 writable computed 让 VueDraggable 可以直接修改 store 数据（拖拽排序时）
// 拖拽结束后 @end 回调会调用 sortFolders API 持久化新顺序
const treeData = computed({
  get: () => folderStore.folderTree,
  set: (val) => { folderStore.folderTree = val }
})

// ---- 对话框状态 ----
const dialogVisible = ref(false)
const dialogMode = ref('create') // 'create' | 'rename'
const dialogName = ref('')
const dialogTargetId = ref(null) // 重命名时的文件夹 ID
const dialogParentId = ref(null) // 创建子文件夹时的父级 ID
const dialogLoading = ref(false)

/**
 * 选中文件夹
 * 通知 store 更新 currentFolderId
 */
function handleSelect(folderId) {
  folderStore.setCurrentFolder(folderId)
}

/**
 * 创建根级文件夹
 */
function handleCreateRoot() {
  dialogMode.value = 'create'
  dialogName.value = ''
  dialogParentId.value = null
  dialogTargetId.value = null
  dialogVisible.value = true
}

/**
 * 创建子文件夹
 * @param {number} parentId - 父文件夹 ID
 */
function handleCreateSub(parentId) {
  dialogMode.value = 'create'
  dialogName.value = ''
  dialogParentId.value = parentId
  dialogTargetId.value = null
  dialogVisible.value = true
}

/**
 * 重命名文件夹
 * @param {Object} node - { id, name }
 */
function handleRename(node) {
  dialogMode.value = 'rename'
  dialogName.value = node.name
  dialogTargetId.value = node.id
  dialogParentId.value = null
  dialogVisible.value = true
}

/**
 * 删除文件夹（带确认弹窗）
 * @param {Object} node - { id, name }
 */
async function handleDelete(node) {
  try {
    await ElMessageBox.confirm(
      t('folders.confirm.message'),
      t('folders.confirm.title'),
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await folderStore.deleteFolder(node.id)
    ElMessage.success(t('folders.toast.deleted'))
  } catch {
    // 用户取消删除，不做任何处理
  }
}

/**
 * 对话框确认：创建或重命名
 */
async function handleDialogConfirm() {
  const name = dialogName.value.trim()
  if (!name) {
    ElMessage.warning(t('folders.validation.nameRequired'))
    return
  }

  dialogLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await folderStore.createFolder({
        name,
        parentId: dialogParentId.value
      })
      ElMessage.success(t('folders.toast.created'))
    } else {
      await folderStore.updateFolder(dialogTargetId.value, { name })
      ElMessage.success(t('folders.toast.renamed'))
    }
    dialogVisible.value = false
  } finally {
    dialogLoading.value = false
  }
}

/**
 * 拖拽结束：收集排序数据并提交到后端
 */
function collectSortData(nodes) {
  const result = []
  nodes.forEach((node, index) => {
    result.push({ id: node.id, sortOrder: index })
    if (node.children && node.children.length > 0) {
      result.push(...collectSortData(node.children))
    }
  })
  return result
}

async function handleDragEnd() {
  const sortData = collectSortData(folderStore.folderTree)
  try {
    await folderStore.sortFolders(sortData)
  } catch {
    ElMessage.error(t('folders.toast.orderFailed'))
  }
}

// ---- 递归树节点子组件 ----
/**
 * FolderTreeNode -- 递归渲染的树节点组件
 * 使用 defineComponent + h() 渲染函数实现递归
 * 因为 Vue 3 SFC 中组件不能直接递归引用自身，这里用渲染函数方式
 *
 * 注意：此组件内部使用 t() 进行 i18n 翻译
 */
const FolderTreeNode = defineComponent({
  name: 'FolderTreeNode',
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    selectedId: { type: Number, default: null }
  },
  emits: ['select', 'rename', 'delete', 'create-sub', 'drag-end'],
  setup(props, { emit }) {
    const { t } = useI18n()
    const expanded = ref(true)
    const showMenu = ref(false)

    function toggle() {
      expanded.value = !expanded.value
    }

    function onSelect() {
      emit('select', props.node.id)
    }

    function onContextMenu(e) {
      e.preventDefault()
      e.stopPropagation()
      showMenu.value = !showMenu.value
      // 点击其他区域关闭菜单
      const closeMenu = () => {
        showMenu.value = false
        document.removeEventListener('click', closeMenu)
      }
      setTimeout(() => document.addEventListener('click', closeMenu), 0)
    }

    return () => {
      const hasChildren = props.node.children && props.node.children.length > 0
      const isSelected = props.selectedId === props.node.id
      const indent = props.depth * 16

      return h('div', { class: 'tree-node-wrapper' }, [
        // 当前节点
        h('div', {
          class: ['tree-node', { 'is-selected': isSelected, 'has-children': hasChildren }],
          style: { paddingLeft: `${8 + indent}px` },
          onClick: (e) => {
            e.stopPropagation()
            onSelect()
          },
          onContextmenu: (e) => onContextMenu(e)
        }, [
          // 展开/折叠箭头
          h('span', {
            class: ['node-toggle', { 'is-expanded': expanded.value, 'is-hidden': !hasChildren }],
            onClick: (e) => {
              e.stopPropagation()
              toggle()
            }
          }, [
            h('svg', { width: 12, height: 12, viewBox: '0 0 12 12', fill: 'none' }, [
              h('path', { d: 'M4.5 3L7.5 6L4.5 9', stroke: 'currentColor', 'stroke-width': '1.2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' })
            ])
          ]),
          // 文件夹图标
          h('span', { class: 'node-icon' }, [
            h('svg', { width: 14, height: 14, viewBox: '0 0 14 14', fill: 'none' }, [
              h('path', {
                d: 'M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417',
                stroke: 'currentColor', 'stroke-width': '1.2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round'
              })
            ])
          ]),
          // 名称
          h('span', { class: 'node-name', title: props.node.name }, props.node.name),
          // 右键菜单
          showMenu.value ? h('div', {
            class: 'node-context-menu',
            onClick: (e) => e.stopPropagation()
          }, [
            h('button', {
              class: 'menu-item',
              onClick: (e) => {
                e.stopPropagation()
                showMenu.value = false
                emit('rename', { id: props.node.id, name: props.node.name })
              }
            }, t('folders.contextMenu.rename')),
            h('button', {
              class: 'menu-item',
              onClick: (e) => {
                e.stopPropagation()
                showMenu.value = false
                emit('create-sub', props.node.id)
              }
            }, t('folders.contextMenu.newSubfolder')),
            h('button', {
              class: 'menu-item menu-item-danger',
              onClick: (e) => {
                e.stopPropagation()
                showMenu.value = false
                emit('delete', { id: props.node.id, name: props.node.name })
              }
            }, t('folders.contextMenu.delete'))
          ]) : null
        ]),

        // 子节点（递归渲染，使用 VueDraggable 支持子级拖拽排序）
        hasChildren && expanded.value
          ? h(VueDraggable, {
              modelValue: props.node.children,
              'onUpdate:modelValue': (val) => { props.node.children = val },
              group: 'folders',
              animation: 200,
              ghostClass: 'drag-ghost',
              dragClass: 'drag-active',
              class: 'tree-node-children',
              onEnd: (evt) => emit('drag-end', evt)
            },
            {
              default: () => props.node.children.map(child =>
                h(FolderTreeNode, {
                  key: child.id,
                  node: child,
                  depth: props.depth + 1,
                  selectedId: props.selectedId,
                  onSelect: (id) => emit('select', id),
                  onRename: (data) => emit('rename', data),
                  onDelete: (data) => emit('delete', data),
                  onCreateSub: (id) => emit('create-sub', id),
                  onDragEnd: (evt) => emit('drag-end', evt)
                })
              )
            }
          )
          : null
      ])
    }
  }
})
</script>

<style scoped>
/* ---- 树容器 ---- */
.folder-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ---- 顶部按钮区 ---- */
.tree-header {
  padding: 12px 12px 8px;
}

.new-folder-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  border-radius: var(--hlaia-radius);
  border: 1px dashed var(--hlaia-primary-light);
  background: rgba(74, 127, 199, 0.04);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.new-folder-btn:hover {
  background: rgba(74, 127, 199, 0.08);
  border-color: var(--hlaia-primary);
}

/* ---- 树主体（可滚动） ---- */
.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 4px 12px;
}

/* ---- 树节点（通过 h() 渲染，需要用 :deep） ---- */
.tree-body :deep(.tree-node-wrapper) {
  /* 无额外样式，用于包裹 */
}

.tree-body :deep(.tree-node) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  transition: all 0.15s ease;
  user-select: none;
}

.tree-body :deep(.tree-node:hover) {
  background: var(--hlaia-surface-light);
}

.tree-body :deep(.tree-node.is-selected) {
  background: rgba(74, 127, 199, 0.1);
}

.tree-body :deep(.tree-node.is-selected .node-name) {
  color: var(--hlaia-primary);
  font-weight: 600;
}

.tree-body :deep(.tree-node.is-selected .node-icon) {
  color: var(--hlaia-primary);
}

/* 展开/折叠箭头 */
.tree-body :deep(.node-toggle) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--hlaia-text-muted);
  transition: transform 0.2s ease;
}

.tree-body :deep(.node-toggle.is-expanded) {
  transform: rotate(90deg);
}

.tree-body :deep(.node-toggle.is-hidden) {
  visibility: hidden;
}

/* 文件夹图标 */
.tree-body :deep(.node-icon) {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--hlaia-text-muted);
  transition: color 0.15s ease;
}

/* 节点名称 */
.tree-body :deep(.node-name) {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  line-height: 1.4;
}

/* ---- 右键上下文菜单 ---- */
.tree-body :deep(.node-context-menu) {
  position: absolute;
  top: 100%;
  left: 12px;
  z-index: 100;
  min-width: 140px;
  padding: 4px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow-hover);
  animation: menuFadeIn 0.15s ease;
}

@keyframes menuFadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tree-body :deep(.menu-item) {
  display: block;
  width: 100%;
  padding: 7px 12px;
  border: none;
  border-radius: 4px;
  background: transparent;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text);
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tree-body :deep(.menu-item:hover) {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-primary);
}

.tree-body :deep(.menu-item-danger) {
  color: #e74c3c;
}

.tree-body :deep(.menu-item-danger:hover) {
  background: rgba(231, 76, 60, 0.08);
  color: #e74c3c;
}

/* ---- 子节点容器 ---- */
.tree-body :deep(.tree-node-children) {
  /* 子节点无需额外样式，缩进通过 padding-left 实现 */
}

/* ---- 拖拽状态 ---- */
.tree-body :deep(.drag-ghost) {
  opacity: 0.4;
  background: rgba(74, 127, 199, 0.06);
  border-radius: 6px;
}

.tree-body :deep(.drag-active) {
  background: rgba(74, 127, 199, 0.1);
  border-radius: 6px;
}

/* ---- 空状态 ---- */
.tree-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--hlaia-text-muted);
  gap: 12px;
}

.tree-empty span {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
}

/* ---- 对话框样式覆盖 ---- */
.folder-dialog :deep(.el-dialog) {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
}

.folder-dialog :deep(.el-dialog__header) {
  padding: 20px 24px 0;
}

.folder-dialog :deep(.el-dialog__title) {
  font-family: 'DM Sans', sans-serif;
  font-weight: 600;
  color: var(--hlaia-text);
}

.folder-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.folder-dialog :deep(.el-dialog__footer) {
  padding: 0 24px 20px;
}

.folder-dialog :deep(.el-input__wrapper) {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
}

.folder-dialog :deep(.el-input__wrapper.is-focus) {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.12);
}

.folder-dialog :deep(.el-input__inner) {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
}
</style>
