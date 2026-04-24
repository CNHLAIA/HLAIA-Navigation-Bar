<!--
  StagingView.vue — 暂存区页面

  设计风格：
  - Warm Minimal Light 主题
  - 顶部导航栏：Logo、导航链接（Bookmarks/Staging/Admin）、语言切换、用户下拉
  - 主内容区展示暂存条目网格
  - 添加暂存项对话框、移动到文件夹对话框、设置过期时间对话框
  - 每 60 秒自动刷新倒计时显示
-->
<template>
  <div class="staging-layout">
    <!-- 顶部导航栏（共享组件） -->
    <NavBar />

    <!-- 主内容区 -->
    <main class="staging-content">
      <!-- 顶部标题栏 -->
      <div class="staging-top-bar">
        <div class="staging-title-area">
          <h1 class="staging-title">{{ t('staging.view.title') }}</h1>
          <span v-if="stagingStore.items.length > 0" class="staging-count">
            {{ stagingStore.items.length }}
          </span>
        </div>
        <button class="add-staging-btn" @click="openAddDialog">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 3v8M3 7h8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ t('staging.view.addToStaging') }}
        </button>
      </div>

      <!-- 暂存条目列表 -->
      <StagingList
        :items="stagingStore.items"
        :loading="stagingStore.loading"
        @move-to-folder="openMoveDialog"
        @set-expiry="openExpiryDialog"
        @delete="handleDelete"
      />
    </main>

    <!-- 添加暂存项对话框 -->
    <el-dialog
      v-model="addDialogVisible"
      :title="t('staging.view.addDialog.title')"
      width="420px"
      :append-to-body="true"
      class="staging-dialog"
      @close="addDialogVisible = false"
    >
      <el-form :model="addForm" label-position="top" class="staging-form">
        <el-form-item :label="t('bookmarks.form.title')">
          <el-input
            v-model="addForm.title"
            :placeholder="t('staging.view.addDialog.titlePlaceholder')"
            maxlength="255"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('bookmarks.form.url')">
          <el-input
            v-model="addForm.url"
            :placeholder="t('staging.view.addDialog.urlPlaceholder')"
            maxlength="2048"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('bookmarks.form.iconUrl')">
          <el-input
            v-model="addForm.icon"
            :placeholder="t('staging.view.addDialog.iconUrlPlaceholder')"
            maxlength="500"
            clearable
          />
        </el-form-item>
        <el-form-item :label="t('staging.view.expiryDialog.label')">
          <el-input-number
            v-model="addForm.expireMinutes"
            :min="1"
            :max="10080"
            :step="60"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addDialogLoading" @click="handleAddConfirm">
          {{ t('common.add') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 移动到文件夹对话框 -->
    <el-dialog
      v-model="moveDialogVisible"
      :title="t('staging.view.moveDialog.title')"
      width="400px"
      :append-to-body="true"
      class="staging-dialog"
      @close="moveDialogVisible = false"
    >
      <p class="move-dialog-hint">{{ t('staging.view.moveDialog.description') }}</p>
      <div v-if="folderTree.length > 0" class="folder-picker">
        <div
          v-for="node in flattenTree(folderTree)"
          :key="node.id"
          class="folder-picker-item"
          :class="{ 'is-selected': selectedFolderId === node.id }"
          :style="{ paddingLeft: `${12 + node.depth * 20}px` }"
          @click="selectedFolderId = node.id"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>{{ node.name }}</span>
        </div>
      </div>
      <div v-else class="folder-picker-empty">
        <p>{{ t('staging.view.moveDialog.empty') }}</p>
      </div>
      <template #footer>
        <el-button @click="moveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="moveDialogLoading"
          :disabled="!selectedFolderId"
          @click="handleMoveConfirm"
        >
          {{ t('common.add') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 设置过期时间对话框 -->
    <el-dialog
      v-model="expiryDialogVisible"
      :title="t('staging.view.expiryDialog.title')"
      width="380px"
      :append-to-body="true"
      class="staging-dialog"
      @close="expiryDialogVisible = false"
    >
      <p class="expiry-dialog-hint">
        {{ t('staging.view.expiryDialog.label') }}: <strong>{{ expiryItem?.title }}</strong>
      </p>
      <el-form label-position="top" class="staging-form">
        <el-form-item :label="t('staging.view.expiryDialog.label')">
          <el-input-number
            v-model="expiryMinutes"
            :min="1"
            :max="10080"
            :step="60"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <!-- 快捷选项 -->
        <div class="expiry-presets">
          <button
            v-for="preset in expiryPresets"
            :key="preset.value"
            class="preset-btn"
            :class="{ active: expiryMinutes === preset.value }"
            @click="expiryMinutes = preset.value"
          >
            {{ preset.label }}
          </button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="expiryDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="expiryDialogLoading" @click="handleExpiryConfirm">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * StagingView — 暂存区页面
 *
 * 职责：
 * - 展示暂存区条目列表
 * - 提供"添加"、"移动到文件夹"、"设置过期"、"删除"功能
 * - 每 60 秒自动刷新列表以更新倒计时显示
 * - 复用与 MainView 一致的导航栏和视觉风格
 *
 * 对话框设计：
 * - 添加对话框：输入标题、URL、图标、过期时间
 * - 移动对话框：展示用户文件夹树，选择目标文件夹
 * - 过期对话框：设置新的过期时间，提供快捷预设
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStagingStore } from '@/stores/staging'
import { useFolderStore } from '@/stores/folder'
import { ElMessage, ElMessageBox } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import StagingList from '@/components/StagingList.vue'

const { t } = useI18n()
const stagingStore = useStagingStore()
const folderStore = useFolderStore()

/** 文件夹树（用于"移动到文件夹"对话框） */
const folderTree = computed(() => folderStore.folderTree)

/** 过期时间快捷预设（使用 i18n 翻译） */
const expiryPresets = computed(() => [
  { label: t('staging.view.expiryDialog.presets.30min'), value: 30 },
  { label: t('staging.view.expiryDialog.presets.1hour'), value: 60 },
  { label: t('staging.view.expiryDialog.presets.6hours'), value: 360 },
  { label: t('staging.view.expiryDialog.presets.1day'), value: 1440 },
  { label: t('staging.view.expiryDialog.presets.3days'), value: 4320 },
  { label: t('staging.view.expiryDialog.presets.7days'), value: 10080 }
])

// ---- 定时刷新 ----

/** 定时器 ID，用于组件卸载时清除 */
let refreshTimer = null

// ---- 添加对话框状态 ----
const addDialogVisible = ref(false)
const addDialogLoading = ref(false)
const addForm = ref({
  title: '',
  url: '',
  icon: '',
  expireMinutes: 1440 // 默认 24 小时
})

// ---- 移动到文件夹对话框状态 ----
const moveDialogVisible = ref(false)
const moveDialogLoading = ref(false)
const moveItem = ref(null)
const selectedFolderId = ref(null)

// ---- 设置过期时间对话框状态 ----
const expiryDialogVisible = ref(false)
const expiryDialogLoading = ref(false)
const expiryItem = ref(null)
const expiryMinutes = ref(1440)

// ---- 生命周期 ----

onMounted(async () => {
  // 加载暂存区数据和文件夹树
  try {
    await Promise.all([
      stagingStore.fetchItems(),
      folderStore.fetchTree()
    ])
  } catch {
    ElMessage.error(t('staging.view.toast.loadFailed'))
  }

  // 每 60 秒刷新列表（更新倒计时）
  refreshTimer = setInterval(() => {
    stagingStore.fetchItems()
  }, 60000)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

// ---- 添加暂存项 ----

function openAddDialog() {
  addForm.value = {
    title: '',
    url: '',
    icon: '',
    expireMinutes: 1440
  }
  addDialogVisible.value = true
}

async function handleAddConfirm() {
  const { title, url, icon, expireMinutes } = addForm.value
  if (!title.trim()) {
    ElMessage.warning(t('staging.view.validation.titleRequired'))
    return
  }
  if (!url.trim()) {
    ElMessage.warning(t('staging.view.validation.urlRequired'))
    return
  }

  addDialogLoading.value = true
  try {
    await stagingStore.addItem({
      title: title.trim(),
      url: url.trim(),
      icon: icon.trim() || null,
      expireMinutes
    })
    ElMessage.success(t('staging.view.toast.added'))
    addDialogVisible.value = false
  } finally {
    addDialogLoading.value = false
  }
}

// ---- 移动到文件夹 ----

function openMoveDialog(item) {
  moveItem.value = item
  selectedFolderId.value = null
  moveDialogVisible.value = true
}

async function handleMoveConfirm() {
  if (!selectedFolderId.value || !moveItem.value) return

  moveDialogLoading.value = true
  try {
    await stagingStore.moveToFolder(moveItem.value.id, selectedFolderId.value)
    ElMessage.success(t('staging.view.toast.moved'))
    moveDialogVisible.value = false
  } finally {
    moveDialogLoading.value = false
  }
}

// ---- 设置过期时间 ----

function openExpiryDialog(item) {
  expiryItem.value = item
  expiryMinutes.value = 1440
  expiryDialogVisible.value = true
}

async function handleExpiryConfirm() {
  if (!expiryItem.value) return

  expiryDialogLoading.value = true
  try {
    await stagingStore.updateExpiry(expiryItem.value.id, expiryMinutes.value)
    ElMessage.success(t('staging.view.toast.expiryUpdated'))
    expiryDialogVisible.value = false
  } finally {
    expiryDialogLoading.value = false
  }
}

// ---- 删除暂存项 ----

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(
      t('staging.view.removeConfirm.message'),
      t('staging.view.removeConfirm.title'),
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await stagingStore.deleteItem(item.id)
    ElMessage.success(t('staging.view.toast.removed'))
  } catch {
    // 用户取消
  }
}

// ---- 辅助函数 ----

/**
 * 将嵌套的文件夹树扁平化为一维数组
 * 用于在"移动到文件夹"对话框中渲染列表
 * @param {Array} nodes - 树节点数组
 * @param {number} depth - 当前深度
 * @returns {Array} - 扁平化的节点数组 [{ id, name, depth }]
 */
function flattenTree(nodes, depth = 0) {
  const result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name, depth })
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}
</script>

<style scoped>
.staging-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--hlaia-bg);
  position: relative;
  overflow: hidden;
}
.staging-content {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 24px 28px;
  overflow-y: auto;
  background: var(--hlaia-bg);
}

/* ---- 顶部标题栏 ---- */
.staging-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.staging-title-area {
  display: flex;
  align-items: center;
  gap: 10px;
}

.staging-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--hlaia-text);
  margin: 0;
}

.staging-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 12px;
  background: rgba(74, 127, 199, 0.1);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  font-weight: 600;
}

.add-staging-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border-radius: 8px;
  border: none;
  background: var(--hlaia-primary);
  color: #fff;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.add-staging-btn:hover {
  background: var(--hlaia-primary-dark);
  box-shadow: 0 4px 12px rgba(74, 127, 199, 0.25);
  transform: translateY(-1px);
}

/* ---- 移动到文件夹对话框 ---- */
.move-dialog-hint {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  margin: 0 0 12px;
}

.folder-picker {
  max-height: 300px;
  overflow-y: auto;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
}

.folder-picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--hlaia-text-muted);
  border-bottom: 1px solid var(--hlaia-border);
}

.folder-picker-item:last-child {
  border-bottom: none;
}

.folder-picker-item:hover {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text);
}

.folder-picker-item.is-selected {
  background: rgba(74, 127, 199, 0.08);
  color: var(--hlaia-primary);
}

.folder-picker-item span {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.folder-picker-empty {
  padding: 24px;
  text-align: center;
}

.folder-picker-empty p {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

/* ---- 过期时间设置 ---- */
.expiry-dialog-hint {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  margin: 0 0 16px;
}

.expiry-dialog-hint strong {
  color: var(--hlaia-text);
}

.expiry-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.preset-btn {
  padding: 5px 12px;
  border-radius: 6px;
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.preset-btn:hover {
  border-color: var(--hlaia-primary-light);
  color: var(--hlaia-text);
}

.preset-btn.active {
  background: rgba(74, 127, 199, 0.1);
  border-color: var(--hlaia-primary);
  color: var(--hlaia-primary);
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .staging-content { padding: 16px; }
  .staging-top-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

/* ---- Element Plus 下拉菜单样式覆盖（Light theme） ---- */
:deep(.el-dropdown-menu) {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
  box-shadow: var(--hlaia-shadow-hover);
  padding: 4px;
  min-width: 140px;
}

:deep(.el-dropdown-menu__item) {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text);
  padding: 8px 12px;
  border-radius: 6px;
}

:deep(.el-dropdown-menu__item:hover) {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-primary);
}

:deep(.el-dropdown-menu__item.is-disabled) {
  cursor: default;
  opacity: 1;
}

:deep(.el-dropdown-menu__item--divided::before) {
  background-color: var(--hlaia-border);
}
</style>
<style>
.staging-dialog .el-dialog {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
  box-shadow: var(--hlaia-shadow-hover);
}

.staging-dialog .el-dialog__header {
  padding: 20px 24px 0;
}

.staging-dialog .el-dialog__title {
  font-family: 'DM Sans', sans-serif;
  font-weight: 600;
  color: var(--hlaia-text);
}

.staging-dialog .el-dialog__body {
  padding: 16px 24px;
}

.staging-dialog .el-dialog__footer {
  padding: 0 24px 20px;
}

.staging-dialog .el-form-item__label {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

.staging-dialog .el-input__wrapper {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: 8px;
  box-shadow: none;
}

.staging-dialog .el-input__wrapper.is-focus {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.12);
}

.staging-dialog .el-input__inner {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
}

.staging-dialog .el-input-number {
  width: 100%;
}

.staging-dialog .el-input-number .el-input__wrapper {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: 8px;
  box-shadow: none;
}

.staging-dialog .el-input-number .el-input__inner {
  color: var(--hlaia-text);
  font-family: 'DM Sans', sans-serif;
}
</style>
