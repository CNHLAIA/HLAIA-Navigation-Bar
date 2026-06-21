<!--
  BookmarkGrid.vue -- 书签网格布局组件

  功能：
  - CSS Grid 响应式布局（大屏4列、中屏3列、小屏2列）
  - 接收 folderId prop，从 API 加载该文件夹下的书签
  - 渲染 BookmarkCard 卡片列表
  - 支持多选：Ctrl+Click 切换选中
  - 批量操作工具栏（BatchToolbar）
  - 拖拽排序（vue-draggable-plus）
  - 新增书签按钮 + 对话框
  - 右键上下文菜单（编辑、删除）

  设计：Warm Minimal Light 主题
  - 网格间距 12px，卡片大小一致
  - 空状态居中提示
  - 白色对话框
-->
<template>
  <div class="bookmark-grid-wrapper">
    <!-- 批量操作工具栏 -->
    <BatchToolbar
      :visible="bookmarkStore.hasSelection"
      :count="bookmarkStore.selectedIds.size"
      :is-all-selected="bookmarkStore.isAllSelected"
      @delete="handleBatchDelete"
      @copy="handleBatchCopy"
      @toggle-select-all="handleToggleSelectAll"
      @dismiss="bookmarkStore.clearSelection()"
    />

    <!-- 内容区域 -->
    <div class="grid-content-area">
      <!-- 内容加载进度条：2px 细线指示器，在数据加载期间显示 -->
      <Transition name="progress-fade">
        <div v-if="bookmarkStore.loading" class="content-progress-bar">
          <div class="progress-indicator"></div>
        </div>
      </Transition>

      <!-- 延迟骨架屏：仅在加载超过 150ms 时显示，避免快速响应时的闪烁 -->
      <div v-if="showSkeleton" class="bookmark-skeleton-grid">
        <div v-for="i in 8" :key="i" class="skeleton-card">
          <div class="skeleton-icon shimmer"></div>
          <div class="skeleton-lines">
            <div class="skeleton-line shimmer" style="width: 70%"></div>
            <div class="skeleton-line shimmer" style="width: 50%"></div>
          </div>
        </div>
      </div>

      <!--
        正常内容区域
        当 bookmarkStore.loading 为 true 但骨架屏尚未显示（0-150ms 窗口）时，
        添加 is-loading class 使内容变暗，给用户视觉反馈"正在加载"
      -->
      <div class="bookmark-grid-content" :class="{ 'is-loading': bookmarkStore.loading }">
      <!-- 网格顶部：标题 + 新增按钮 -->
      <div class="grid-top-bar">
        <div class="grid-title-area">
          <h2 class="grid-title">
            {{ folderName || t('bookmarks.selectFolder') }}
          </h2>
          <span v-if="bookmarkStore.bookmarks.length > 0" class="bookmark-count">
            {{ bookmarkStore.bookmarks.length }}
          </span>
        </div>
        <div v-if="folderId" class="grid-top-actions">
          <button
            class="refresh-btn"
            :title="t('bookmarks.refresh')"
            @click="handleRefresh"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M1.5 7a5.5 5.5 0 0 1 9.37-3.9M12.5 7a5.5 5.5 0 0 1-9.37 3.9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M11 1.5v2.5H8.5M3 10v2.5h2.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button
            class="import-bookmark-btn"
            @click="openImportDialog"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M2 10v2h10v-2M7 2v7M4.5 6.5L7 9l2.5-2.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            {{ t('bookmarks.importDialog.title') }}
          </button>
          <button
            class="add-bookmark-btn"
            @click="openCreateDialog"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 3v8M3 7h8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            {{ t('bookmarks.addBookmark') }}
          </button>
        </div>
      </div>

      <!-- 书签网格 -->
      <VueDraggable
        v-if="bookmarkStore.bookmarks.length > 0"
        v-model="gridData"
        :animation="200"
        :delay="300"
        :delay-on-touch-only="true"
        ghost-class="grid-drag-ghost"
        class="bookmark-grid"
        @end="handleDragEnd"
      >
        <BookmarkCard
          v-for="(element, index) in gridData"
          :key="element.id"
          :bookmark="element"
          :selected="bookmarkStore.selectedIds.has(element.id)"
          :is-selecting="bookmarkStore.hasSelection"
          :style="{ '--stagger-index': Math.min(index, 8) }"
          :class="{ 'card-stagger': animateCards }"
          @click="handleCardClick"
          @contextmenu="handleCardContext"
        />
      </VueDraggable>

      <!-- 空状态：有选中文件夹但无书签 -->
      <div v-else-if="folderId" class="grid-empty">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <rect x="6" y="10" width="36" height="28" rx="4" stroke="currentColor" stroke-width="1.5"/>
          <path d="M6 18h36" stroke="currentColor" stroke-width="1.5"/>
          <circle cx="14" cy="14" r="2" fill="currentColor" opacity="0.3"/>
          <circle cx="20" cy="14" r="2" fill="currentColor" opacity="0.3"/>
          <circle cx="26" cy="14" r="2" fill="currentColor" opacity="0.3"/>
        </svg>
        <p class="empty-title">{{ t('bookmarks.empty.noBookmarks') }}</p>
        <p class="empty-desc">{{ t('bookmarks.empty.addFirst') }}</p>
        <button class="empty-add-btn" @click="openCreateDialog">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 3v8M3 7h8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ t('bookmarks.addBookmark') }}
        </button>
      </div>

      <!-- 未选择文件夹 -->
      <div v-else class="grid-empty">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <path d="M8 20l16-10 16 10v18a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V20z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M20 41V28h8v13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <p class="empty-title">{{ t('bookmarks.selectFolder') }}</p>
        <p class="empty-desc">{{ t('bookmarks.empty.chooseFolder') }}</p>
      </div>
    </div><!-- /.bookmark-grid-content -->
    </div><!-- /.grid-content-area -->
    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="card-context-menu"
        :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      >
        <button class="ctx-item" @click="handleEdit">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9.625 2.125l2.25 2.25M1.75 10.25l-.5 2.5 2.5-.5L12.125 3.875a1.591 1.591 0 0 0-2.25-2.25L1.75 10.25z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ t('bookmarks.contextMenu.edit') }}
        </button>
        <button class="ctx-item" @click="handleCopyUrl">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M5.25 8.75L9 5M9 5H6.75M9 5v2.25" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M4.667 1.75h4.666a2.333 2.333 0 0 1 2.334 2.333v4.667a2.333 2.333 0 0 1-2.334 2.333H4.667a2.333 2.333 0 0 1-2.334-2.333V4.083a2.333 2.333 0 0 1 2.334-2.333z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ t('bookmarks.contextMenu.copyUrl') }}
        </button>
        <button class="ctx-item" @click="handleMoveToFolder">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1v12M4 4l3-3 3 3M1 9v3a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V9" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ bookmarkStore.hasSelection ? t('bookmarks.contextMenu.moveSelectedTo') : t('bookmarks.contextMenu.moveTo') }}
        </button>
        <button class="ctx-item ctx-item-danger" @click="handleContextDelete">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M1.75 3.5h10.5M5.25 3.5V2.333a1.167 1.167 0 0 1 1.167-1.166h1.167a1.167 1.167 0 0 1 1.166 1.166V3.5m1.75 0v8.167a1.167 1.167 0 0 1-1.166 1.166H4.667a1.167 1.167 0 0 1-1.167-1.166V3.5h7.583z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ t('bookmarks.contextMenu.delete') }}
        </button>
      </div>
    </teleport>

    <!-- 新建/编辑书签对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? t('bookmarks.addBookmark') : t('bookmarks.editDialog.title')"
      width="420px"
      :append-to-body="true"
      class="bookmark-dialog"
      @close="dialogVisible = false"
    >
      <el-form :model="dialogForm" label-position="top" class="bookmark-form">
        <el-form-item :label="t('bookmarks.form.title')">
          <el-input
            v-model="dialogForm.title"
            :placeholder="t('bookmarks.form.titlePlaceholder')"
            maxlength="255"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('bookmarks.form.url')">
          <el-input
            v-model="dialogForm.url"
            :placeholder="t('bookmarks.form.urlPlaceholder')"
            maxlength="2048"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('bookmarks.form.iconUrl')">
          <el-input
            v-model="dialogForm.iconUrl"
            :placeholder="t('bookmarks.form.iconUrlPlaceholder')"
            maxlength="500"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogConfirm">
          {{ dialogMode === 'create' ? t('common.add') : t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
    <!-- 移动到文件夹弹窗 -->
    <FolderPickerDialog
      v-model:visible="moveDialogVisible"
      :title="t('bookmarks.moveDialog.title')"
      :exclude-folder-id="folderId"
      @confirm="handleMoveConfirm"
    />

    <!-- 导入书签对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      :title="t('bookmarks.importDialog.title')"
      width="480px"
      :append-to-body="true"
      class="bookmark-dialog import-dialog"
      @close="resetImportDialog"
    >
      <div class="import-form">
        <!-- 文件上传 -->
        <div class="import-section">
          <label class="import-label">{{ t('bookmarks.importDialog.selectFile') }}</label>
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".html,.htm"
            :on-change="handleImportFileChange"
            :on-remove="handleImportFileRemove"
            :file-list="importFileList"
            drag
          >
            <div class="import-upload-inner">
              <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
                <path d="M6 22v4a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-4M16 4v16M10 10l6-6 6 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <p class="import-upload-text">{{ t('bookmarks.importDialog.selectFile') }}</p>
              <p class="import-upload-tip">{{ t('bookmarks.importDialog.fileTip') }}</p>
            </div>
          </el-upload>
        </div>

        <!-- 目标文件夹 -->
        <div class="import-section">
          <label class="import-label">{{ t('bookmarks.importDialog.targetFolder') }}</label>
          <div class="import-folder-picker">
            <div
              v-for="node in importFolderList"
              :key="node.id"
              class="import-folder-item"
              :class="{ 'is-selected': importTargetFolderId === node.id }"
              :style="{ paddingLeft: `${12 + node.depth * 20}px` }"
              @click="importTargetFolderId = node.id"
            >
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span>{{ node.name }}</span>
            </div>
          </div>
          <p class="import-hint">{{ t('bookmarks.importDialog.targetFolderHint') }}</p>
        </div>

        <!-- 重复处理模式 -->
        <div class="import-section">
          <label class="import-label">{{ t('bookmarks.importDialog.duplicateMode') }}</label>
          <el-radio-group v-model="importDuplicateMode" class="import-radio-group">
            <el-radio value="OVERWRITE">{{ t('bookmarks.importDialog.overwrite') }}</el-radio>
            <el-radio value="SKIP">{{ t('bookmarks.importDialog.skip') }}</el-radio>
          </el-radio-group>
        </div>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="importLoading"
          @click="handleImportConfirm"
        >
          {{ importLoading ? t('bookmarks.importDialog.importing') : t('bookmarks.importDialog.importBtn') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * BookmarkGrid 组件
 *
 * 核心逻辑：
 * 1. 监听 folderId prop 变化，自动加载书签
 * 2. 管理多选状态（通过 Bookmark Store）
 * 3. 提供新建/编辑/删除书签功能
 * 4. 拖拽排序后自动提交到后端
 */
import { ref, computed, watch, onUnmounted, nextTick, Transition } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueDraggable } from 'vue-draggable-plus'
import { useBookmarkStore } from '@/stores/bookmark'
import { ElMessage, ElMessageBox } from 'element-plus'
import BookmarkCard from './BookmarkCard.vue'
import BatchToolbar from './BatchToolbar.vue'
import FolderPickerDialog from './FolderPickerDialog.vue'
import { useFolderStore } from '@/stores/folder'
import { importBookmarks } from '@/api/bookmark'

const { t } = useI18n()

const props = defineProps({
  /** 当前选中的文件夹 ID，null 表示未选择 */
  folderId: {
    type: Number,
    default: null
  },
  /** 当前文件夹名称（用于标题显示） */
  folderName: {
    type: String,
    default: ''
  }
})

const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()

// ---- 延迟骨架屏状态 ----

/**
 * 是否显示骨架屏（shimmer 占位卡片）
 * 只有当 API 加载时间超过 150ms 才为 true，避免快速响应时骨架屏闪烁
 */
const showSkeleton = ref(false)

/** 骨架屏延迟定时器 ID，用于清理 */
let skeletonTimer = null

// ---- Stagger 入场动画状态 ----

/**
 * 是否为书签卡片添加 stagger 滑入动画
 * 每次文件夹切换时为 true（包括回访缓存文件夹），动画结束后自动关闭
 * 不在拖拽排序、CRUD 操作后刷新时触发
 */
const animateCards = ref(false)

/** stagger 动画清理定时器 ID */
let staggerTimer = null

// ---- 拖拽数据 ----
const gridData = computed({
  get: () => bookmarkStore.bookmarks,
  set: (val) => { bookmarkStore.bookmarks = val }
})

// ---- 右键菜单状态 ----
const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  bookmark: null
})

// ---- 对话框状态 ----
const dialogVisible = ref(false)
const dialogMode = ref('create') // 'create' | 'edit'
const dialogLoading = ref(false)
const dialogForm = ref({
  title: '',
  url: '',
  iconUrl: '',
  id: null
})

// ---- 移动弹窗状态 ----
const moveDialogVisible = ref(false)
const moveBookmarkIds = ref([])

// ---- 导入书签弹窗状态 ----
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importUploadRef = ref(null)
const importFileList = ref([])
const importSelectedFile = ref(null)
const importTargetFolderId = ref(null)
const importDuplicateMode = ref('OVERWRITE')

/**
 * 导入对话框中可选择的文件夹列表（扁平化后的树形结构）
 * 使用 computed 缓存，避免每次渲染重新计算
 */
const importFolderList = computed(() => {
  return flattenImportTree(folderStore.folderTree || [])
})

/**
 * 将嵌套的文件夹树扁平化为一维数组，用于文件夹选择列表
 * 每个节点附带 depth 属性，用于缩进显示层级关系
 * @param {Array} nodes - 树节点数组
 * @param {number} depth - 当前深度
 * @returns {Array} - 扁平化的节点列表
 */
function flattenImportTree(nodes, depth = 0) {
  const result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name, depth })
    if (node.children && node.children.length > 0) {
      result.push(...flattenImportTree(node.children, depth + 1))
    }
  }
  return result
}

// ---- 生命周期 ----

/**
 * 监听 folderId 变化，加载对应文件夹的书签
 * immediate: true 确保组件初始化时也加载
 *
 * 延迟骨架屏机制：
 * - 切换文件夹时启动 150ms 定时器
 * - 若 API 在 150ms 内返回（快速响应），跳过骨架屏，用户体验更流畅
 * - 若 API 超过 150ms（慢速响应），显示 shimmer 骨架屏占位
 * - 无论哪种情况，API 返回后都会清除定时器和骨架屏状态
 */
watch(
  () => props.folderId,
  (newId) => {
    // 每次切换文件夹时重置骨架屏定时器和动画状态
    clearTimeout(skeletonTimer)
    clearTimeout(staggerTimer)
    showSkeleton.value = false
    animateCards.value = false

    if (newId) {
      // 判断是否为缓存路径：回访已访问的文件夹时数据秒出
      // 缓存路径不需要骨架屏和 stagger 动画，避免闪烁
      const isCached = bookmarkStore.bookmarkCache.has(newId)

      // 仅在首次访问（无缓存）时启动骨架屏定时器
      if (!isCached) {
        skeletonTimer = setTimeout(() => {
          showSkeleton.value = true
        }, 150)
      }

      bookmarkStore.fetchBookmarks(newId).finally(() => {
        // API 完成后（无论成功失败），清除定时器并隐藏骨架屏
        clearTimeout(skeletonTimer)
        showSkeleton.value = false

        // 仅非缓存路径触发 stagger 入场动画
        // 缓存路径跳过动画：数据已同步填充（零延迟），动画会导致闪烁
        // （卡片先渲染为全透明 opacity:0，再动画滑入 = 视觉上先消失再出现）
        if (!isCached) {
          triggerCardAnimation()
        }
      })
    } else {
      // 未选择文件夹时清空书签列表
      bookmarkStore.bookmarks = []
      bookmarkStore.clearSelection()
    }
  },
  { immediate: true }
)

/**
 * 组件卸载时清理骨架屏定时器和 stagger 动画定时器，防止内存泄漏
 */
onUnmounted(() => {
  clearTimeout(skeletonTimer)
  clearTimeout(staggerTimer)
})

// ---- Stagger 入场动画控制 ----

/**
 * 触发书签卡片的 stagger 滑入动画
 *
 * 原理：
 * 1. 先将 animateCards 设为 false，确保干净的初始状态
 * 2. 在 nextTick 后设为 true，此时 CSS animation 生效
 * 3. 每张卡片的 animation-delay = --stagger-index * 40ms，形成级联效果
 * 4. 动画结束后（300ms 动画 + 最多 8 张卡片 * 40ms stagger ≈ 620ms）关闭动画类
 *
 * 触发时机：每次文件夹切换时（包括回访已缓存的文件夹）
 * 不触发的场景：拖拽排序、CRUD 操作后刷新
 */
function triggerCardAnimation() {
  animateCards.value = false
  nextTick(() => {
    animateCards.value = true
    // 300ms 动画时长 + 8 张卡片 * 40ms stagger = 620ms
    // 动画结束后移除动画类，避免影响后续交互（如拖拽排序后的 re-render）
    staggerTimer = setTimeout(() => {
      animateCards.value = false
    }, 620)
  })
}

// ---- 卡片交互 ----

/**
 * 处理卡片点击
 * - Ctrl/Cmd + 点击：切换选中
 * - 普通点击：打开 URL
 */
function handleCardClick({ bookmark, ctrlKey }) {
  if (ctrlKey) {
    bookmarkStore.toggleSelect(bookmark.id)
  } else {
    // 在新标签页打开链接
    window.open(bookmark.url, '_blank', 'noopener,noreferrer')
  }
}

/**
 * 处理卡片右键菜单
 */
function handleCardContext({ bookmark, x, y }) {
  contextMenu.value = {
    visible: true,
    x,
    y,
    bookmark
  }
  // 点击任意位置关闭菜单
  const close = () => {
    contextMenu.value.visible = false
    document.removeEventListener('click', close)
  }
  setTimeout(() => document.addEventListener('click', close), 0)
}

// ---- 批量操作 ----

/**
 * 批量删除书签
 */
async function handleBatchDelete() {
  const count = bookmarkStore.selectedIds.size
  try {
    await ElMessageBox.confirm(
      t('bookmarks.batch.confirmMessage', { count }),
      t('bookmarks.batch.confirmTitle'),
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await bookmarkStore.batchDelete([...bookmarkStore.selectedIds])
    ElMessage.success(t('bookmarks.batch.deleted', { count }))
  } catch {
    // 用户取消
  }
}

/**
 * 批量复制链接到剪贴板
 */
async function handleBatchCopy() {
  try {
    const text = await bookmarkStore.batchCopyLinks([...bookmarkStore.selectedIds])
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('bookmarks.batch.linksCopied'))
  } catch {
    ElMessage.error(t('bookmarks.batch.copyFailed'))
  }
}

/**
 * 全选 / 取消全选
 */
function handleToggleSelectAll() {
  if (bookmarkStore.isAllSelected) {
    bookmarkStore.clearSelection()
  } else {
    bookmarkStore.selectAll()
  }
}

// ---- 对话框操作 ----

/**
 * 打开创建对话框
 */
function openCreateDialog() {
  dialogMode.value = 'create'
  dialogForm.value = { title: '', url: '', iconUrl: '', id: null }
  dialogVisible.value = true
}

/**
 * 从右键菜单打开编辑对话框
 */
function handleEdit() {
  const bm = contextMenu.value.bookmark
  if (!bm) return
  contextMenu.value.visible = false
  dialogMode.value = 'edit'
  dialogForm.value = {
    title: bm.title,
    url: bm.url,
    iconUrl: bm.iconUrl || '',
    id: bm.id
  }
  dialogVisible.value = true
}

/**
 * 从右键菜单复制书签网址到剪贴板
 */
async function handleCopyUrl() {
  const bm = contextMenu.value.bookmark
  if (!bm) return
  contextMenu.value.visible = false
  try {
    await navigator.clipboard.writeText(bm.url)
    ElMessage.success(t('bookmarks.contextMenu.copyUrlSuccess'))
  } catch {
    ElMessage.error(t('bookmarks.contextMenu.copyUrlFailed'))
  }
}

/**
 * 刷新当前文件夹的书签列表
 */
function handleRefresh() {
  if (props.folderId) {
    bookmarkStore.fetchBookmarks(props.folderId)
  }
}

/**
 * 从右键菜单删除书签
 */
async function handleContextDelete() {
  const bm = contextMenu.value.bookmark
  if (!bm) return
  contextMenu.value.visible = false
  try {
    await ElMessageBox.confirm(
      t('bookmarks.deleteConfirm.title', { title: bm.title }),
      t('bookmarks.deleteConfirm.confirmTitle'),
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await bookmarkStore.deleteBookmark(bm.id)
    ElMessage.success(t('bookmarks.toast.deleted'))
  } catch {
    // 用户取消
  }
}

/**
 * 打开移动到文件夹弹窗
 */
function handleMoveToFolder() {
  const bm = contextMenu.value.bookmark
  if (!bm) return
  contextMenu.value.visible = false

  if (bookmarkStore.hasSelection) {
    moveBookmarkIds.value = [...bookmarkStore.selectedIds]
  } else {
    moveBookmarkIds.value = [bm.id]
  }
  moveDialogVisible.value = true
}

/**
 * 确认移动书签到目标文件夹
 */
async function handleMoveConfirm(targetFolderId) {
  try {
    await bookmarkStore.moveBookmarks(moveBookmarkIds.value, targetFolderId)
    moveDialogVisible.value = false
    bookmarkStore.clearSelection()
    ElMessage.success(
      moveBookmarkIds.value.length > 1
        ? t('bookmarks.toast.movedBatch', { count: moveBookmarkIds.value.length })
        : t('bookmarks.toast.moved')
    )
  } catch {
    ElMessage.error(t('bookmarks.toast.moveFailed'))
  }
}

/**
 * 对话框确认：创建或编辑
 */
async function handleDialogConfirm() {
  const { title, url, iconUrl, id } = dialogForm.value
  if (!title.trim()) {
    ElMessage.warning(t('bookmarks.validation.titleRequired'))
    return
  }
  if (!url.trim()) {
    ElMessage.warning(t('bookmarks.validation.urlRequired'))
    return
  }

  dialogLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      await bookmarkStore.createBookmark({
        title: title.trim(),
        url: url.trim(),
        iconUrl: iconUrl.trim() || null,
        folderId: props.folderId
      })
      ElMessage.success(t('bookmarks.toast.added'))
    } else {
      await bookmarkStore.updateBookmark(id, {
        title: title.trim(),
        url: url.trim(),
        iconUrl: iconUrl.trim() || null,
        folderId: props.folderId
      })
      ElMessage.success(t('bookmarks.toast.updated'))
    }
    dialogVisible.value = false
  } finally {
    dialogLoading.value = false
  }
}

/**
 * 打开导入书签对话框
 * 默认目标文件夹为当前文件夹，重复模式为覆盖更新
 */
function openImportDialog() {
  importTargetFolderId.value = props.folderId
  importDuplicateMode.value = 'OVERWRITE'
  importFileList.value = []
  importSelectedFile.value = null
  importDialogVisible.value = true
}

/**
 * 重置导入对话框状态
 * 在对话框关闭时调用，清空文件选择等
 */
function resetImportDialog() {
  importFileList.value = []
  importSelectedFile.value = null
  importTargetFolderId.value = props.folderId
  importDuplicateMode.value = 'OVERWRITE'
}

/**
 * 处理文件选择变化
 * el-upload 的 on-change 回调，保存用户选中的文件对象
 * @param {Object} file - Element Plus 上传文件对象（包含 raw 属性）
 */
function handleImportFileChange(file) {
  importSelectedFile.value = file
}

/**
 * 处理文件移除
 * 清空已选文件
 */
function handleImportFileRemove() {
  importSelectedFile.value = null
}

/**
 * 确认导入书签
 * 构造 FormData 发送到后端，导入成功后刷新文件夹树和书签列表
 */
async function handleImportConfirm() {
  if (!importSelectedFile.value) {
    ElMessage.warning(t('bookmarks.importDialog.noFile'))
    return
  }

  importLoading.value = true
  try {
    // 构建 multipart/form-data 表单数据
    const formData = new FormData()
    formData.append('file', importSelectedFile.value.raw)
    formData.append('targetFolderId', importTargetFolderId.value)
    formData.append('duplicateMode', importDuplicateMode.value)

    const res = await importBookmarks(formData)
    const stats = res.data

    // 导入成功后关闭对话框
    importDialogVisible.value = false

    // 显示导入结果统计
    const modeLabel = importDuplicateMode.value === 'SKIP'
      ? t('bookmarks.importDialog.skipped')
      : t('bookmarks.importDialog.overwritten')
    ElMessage.success(
      t('bookmarks.importDialog.result', {
        folders: stats.foldersCreated || 0,
        bookmarks: stats.bookmarksCreated || 0,
        duplicates: importDuplicateMode.value === 'SKIP'
          ? (stats.bookmarksSkipped || 0)
          : (stats.bookmarksUpdated || 0),
        mode: modeLabel
      })
    )

    // 刷新文件夹树（因为导入可能创建了新文件夹）
    await folderStore.fetchTree()
    // 刷新当前文件夹的书签列表
    if (props.folderId) {
      await bookmarkStore.fetchBookmarks(props.folderId)
    }
  } catch {
    ElMessage.error(t('bookmarks.importDialog.failed'))
  } finally {
    importLoading.value = false
  }
}

/**
 * 拖拽结束：提交排序到后端
 */
async function handleDragEnd() {
  const sortData = bookmarkStore.bookmarks.map((bm, index) => ({
    id: bm.id,
    sortOrder: index
  }))
  try {
    await bookmarkStore.sortBookmarks(sortData)
  } catch {
    ElMessage.error(t('bookmarks.toast.orderFailed'))
  }
}
</script>

<style scoped>
/* ---- 网格容器 ---- */
.bookmark-grid-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ---- 内容区域容器（包裹进度条、骨架屏、实际内容） ---- */
.grid-content-area {
  position: relative;
  flex: 1;
}

/* ---- 内容加载进度条 ---- */
/* 2px 细线指示器，位于内容区顶部，滑动动画模拟加载进度 */
.content-progress-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 10;
  overflow: hidden;
  background: transparent;
}

.progress-indicator {
  height: 100%;
  background: var(--hlaia-primary);
  animation: progressSlide 1.5s ease-in-out infinite;
}

@keyframes progressSlide {
  0% { transform: translateX(-100%); width: 40%; }
  50% { width: 60%; }
  100% { transform: translateX(300%); width: 40%; }
}

.progress-fade-enter-active,
.progress-fade-leave-active {
  transition: opacity 0.2s ease;
}

.progress-fade-enter-from,
.progress-fade-leave-to {
  opacity: 0;
}

/* ---- 延迟骨架屏 ---- */
/* 网格布局与实际书签网格一致，让占位卡片的位置和大小匹配真实内容 */
.bookmark-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  animation: fadeIn 0.2s ease;
}

@media (max-width: 1200px) {
  .bookmark-skeleton-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 768px) {
  .bookmark-skeleton-grid { grid-template-columns: repeat(2, 1fr); }
}

.skeleton-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--hlaia-surface);
  border-radius: var(--hlaia-radius-lg);
  border: 1px solid var(--hlaia-border);
}

.skeleton-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  flex-shrink: 0;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  border-radius: 6px;
}

/* 微光扫描动画：从左到右的渐变扫光效果，与 StagingList 保持一致 */
.shimmer {
  background: var(--hlaia-surface-light);
  background-image: linear-gradient(
    90deg,
    var(--hlaia-surface-light) 0%,
    #EDECEA 50%,
    var(--hlaia-surface-light) 100%
  );
  background-size: 200% 100%;
  animation: shimmerMove 1.8s ease-in-out infinite;
}

@keyframes shimmerMove {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ---- 加载中内容变暗 ---- */
/* 在 API 请求发出但骨架屏尚未显示的窗口期（0-150ms），将现有内容变暗，
   给用户视觉反馈"正在切换"，同时保留旧内容避免空白闪烁 */
.bookmark-grid-content.is-loading {
  opacity: 0.5;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

/* ---- 顶部标题栏 ---- */
.grid-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.grid-title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.grid-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: var(--hlaia-text);
  margin: 0;
}

.bookmark-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: rgba(74, 127, 199, 0.08);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  font-weight: 600;
}

/* ---- 顶部按钮组 ---- */
.grid-top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---- 刷新按钮 ---- */
.refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.refresh-btn:hover {
  background: var(--hlaia-surface-light);
  border-color: var(--hlaia-primary-light);
  color: var(--hlaia-primary);
}

.refresh-btn:active {
  transform: rotate(180deg);
  transition: transform 0.3s ease;
}

/* ---- 导入书签按钮 ---- */
.import-bookmark-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.import-bookmark-btn:hover {
  background: var(--hlaia-surface-light);
  border-color: var(--hlaia-primary-light);
  color: var(--hlaia-primary);
}

/* ---- 新增书签按钮 ---- */
.add-bookmark-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--hlaia-radius);
  border: none;
  background: var(--hlaia-primary);
  color: #fff;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-bookmark-btn:hover {
  background: var(--hlaia-primary-dark);
  box-shadow: 0 4px 12px rgba(74, 127, 199, 0.25);
  transform: translateY(-1px);
}

/* ---- CSS Grid 响应式布局 ---- */
.bookmark-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

/* 中等屏幕：3列 */
@media (max-width: 1200px) {
  .bookmark-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

/* 小屏幕：2列 */
@media (max-width: 768px) {
  .bookmark-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .grid-top-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

/* 拖拽占位 */
.grid-drag-ghost {
  opacity: 0.4;
}

/* ---- 书签卡片 Stagger 入场动画 ---- */
/* 当 animateCards 为 true 时，每张卡片依次从下方滑入淡出
   使用 CSS 自定义属性 --stagger-index 控制每张卡片的延迟（40ms 间隔）
   最多 8 张卡片参与 stagger（超过 8 张的卡片使用与第 8 张相同的延迟）
   动画尊重 prefers-reduced-motion 设置（在 App.vue 全局样式中禁用） */
.card-stagger {
  animation: cardStaggerIn 0.3s ease both;
  animation-delay: calc(var(--stagger-index, 0) * 40ms);
}

@keyframes cardStaggerIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---- 空状态 ---- */
.grid-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--hlaia-text-muted);
  gap: 8px;
}

.empty-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--hlaia-text);
  margin: 12px 0 0;
}

.empty-desc {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  margin: 0;
}

.empty-add-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 8px 16px;
  border-radius: var(--hlaia-radius);
  border: 1px dashed var(--hlaia-primary-light);
  background: rgba(74, 127, 199, 0.04);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.empty-add-btn:hover {
  background: rgba(74, 127, 199, 0.08);
  border-color: var(--hlaia-primary);
}

/* ---- 导入对话框 ---- */
.import-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.import-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.import-label {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 600;
  color: var(--hlaia-text);
}

.import-upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  gap: 4px;
  color: var(--hlaia-text-muted);
}

.import-upload-text {
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--hlaia-text);
  margin: 4px 0 0;
}

.import-upload-tip {
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  color: var(--hlaia-text-muted);
  margin: 0;
}

.import-hint {
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  color: var(--hlaia-text-muted);
  margin: 0;
}

.import-folder-picker {
  max-height: 200px;
  overflow-y: auto;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
}

.import-folder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--hlaia-text-muted);
  border-bottom: 1px solid var(--hlaia-border);
}

.import-folder-item:last-child {
  border-bottom: none;
}

.import-folder-item:hover {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text);
}

.import-folder-item.is-selected {
  background: rgba(74, 127, 199, 0.08);
  color: var(--hlaia-primary);
}

.import-folder-item span {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.import-radio-group {
  display: flex;
  gap: 16px;
}
</style>

<!-- 全局样式：右键菜单和对话框需要穿透 scoped -->
<style>
/* ---- 右键上下文菜单（全局，因为用 teleport 到 body） ---- */
.card-context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 150px;
  padding: 4px;
  border-radius: var(--hlaia-radius-lg, 12px);
  background: #FFFFFF;
  border: 1px solid #E8E4DF;
  box-shadow: 0 4px 16px rgba(44, 62, 80, 0.12);
  animation: ctxMenuIn 0.15s ease;
}

@keyframes ctxMenuIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: #2C3E50;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ctx-item:hover {
  background: #F5F4F0;
  color: #4A7FC7;
}

.ctx-item-danger {
  color: #e74c3c;
}

.ctx-item-danger:hover {
  background: rgba(231, 76, 60, 0.08);
  color: #e74c3c;
}

/* ---- 书签对话框样式覆盖 ---- */
.bookmark-dialog .el-dialog {
  background: #FFFFFF;
  border: 1px solid #E8E4DF;
  border-radius: 12px;
}

.bookmark-dialog .el-dialog__header {
  padding: 20px 24px 0;
}

.bookmark-dialog .el-dialog__title {
  font-family: 'DM Sans', sans-serif;
  font-weight: 600;
  color: #2C3E50;
}

.bookmark-dialog .el-dialog__body {
  padding: 16px 24px;
}

.bookmark-dialog .el-dialog__footer {
  padding: 0 24px 20px;
}

.bookmark-dialog .el-form-item__label {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: #8B9DAF;
}

.bookmark-dialog .el-input__wrapper {
  background: #F5F4F0;
  border: 1px solid #E8E4DF;
  border-radius: 8px;
  box-shadow: none;
}

.bookmark-dialog .el-input__wrapper.is-focus {
  border-color: #4A7FC7;
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.12);
}

.bookmark-dialog .el-input__inner {
  color: #2C3E50;
  font-family: 'DM Sans', sans-serif;
}

@media (max-width: 768px) {
  .bookmark-dialog .el-dialog {
    width: calc(100vw - 32px) !important;
    margin: 10vh auto 0 !important;
  }
  .import-dialog .el-dialog {
    width: calc(100vw - 32px) !important;
  }
}
</style>
