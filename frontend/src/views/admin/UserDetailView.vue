<!--
  UserDetailView.vue — 管理员用户详情页面

  设计风格：
  - 温暖极简浅色主题（Warm Minimal Light）
  - 白色导航栏 + 浅色背景
  - 用户信息头部卡片（白色 + 阴影）
  - 左侧：白色文件夹树侧边栏
  - 右侧：白色书签列表内容区
  - 状态徽章与 UserListView 一致
  - 支持 i18n 中英文切换

  功能：
  - 加载指定用户的文件夹树
  - 点击文件夹查看该文件夹下的书签
  - 管理员可删除任意用户的文件夹
  - 返回按钮回到用户列表
-->
<template>
  <div class="detail-layout">
    <!-- 顶部导航栏（共享组件） -->
    <NavBar />

    <!-- 主内容区 -->
    <main class="detail-content">
      <!-- 返回按钮 -->
      <button class="back-btn" @click="goBack">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M10 12L6 8l4-4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        {{ t('admin.userDetail.backToUsers') }}
      </button>

      <!-- 加载中 -->
      <div v-if="pageLoading" class="page-loading">
        <div class="loading-spinner"></div>
        <span>{{ t('admin.userDetail.loading') }}</span>
      </div>

      <template v-else-if="userData">
        <!-- 用户信息卡片 -->
        <div class="user-info-card">
          <div class="user-info-avatar">
            {{ (userData.username || '?').charAt(0).toUpperCase() }}
          </div>
          <div class="user-info-main">
            <h2 class="user-info-name">{{ userData.username }}</h2>
            <p class="user-info-email">{{ userData.email || t('admin.userDetail.noEmail') }}</p>
          </div>
          <div class="user-info-meta">
            <div class="meta-item">
              <span class="meta-label">{{ t('admin.userDetail.labels.role') }}</span>
              <span class="role-badge" :class="userData.role === 'ADMIN' ? 'role-admin' : 'role-user'">
                {{ userData.role }}
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('admin.userDetail.labels.status') }}</span>
              <span class="status-badge" :class="userData.status === 'BANNED' ? 'status-banned' : 'status-active'">
                {{ userData.status === 'BANNED' ? t('admin.userDetail.labels.banned') : t('admin.userDetail.labels.active') }}
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('admin.userDetail.labels.joined') }}</span>
              <span class="meta-value">{{ formatDate(userData.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 文件夹树 + 书签列表 -->
        <div class="detail-body">
          <!-- 左侧：文件夹树 -->
          <aside class="detail-sidebar">
            <div class="sidebar-header">
              <h3 class="sidebar-title">{{ t('admin.userDetail.sidebar.folders') }}</h3>
              <span v-if="flatFolders.length > 0" class="sidebar-count">{{ flatFolders.length }}</span>
            </div>

            <div v-if="folderTree.length > 0" class="readonly-tree">
              <div
                v-for="node in flatFolders"
                :key="node.id"
                class="tree-item"
                :class="{ 'is-selected': selectedFolderId === node.id }"
                :style="{ paddingLeft: `${12 + node.depth * 20}px` }"
                @click="handleFolderSelect(node)"
              >
                <span v-if="node.hasChildren" class="tree-toggle" @click.stop="toggleNode(node.id)">
                  <svg
                    width="10" height="10" viewBox="0 0 10 10" fill="none"
                    :style="{ transform: expandedNodes.has(node.id) ? 'rotate(90deg)' : 'rotate(0)' }"
                    style="transition: transform 0.2s ease"
                  >
                    <path d="M3.5 2L6.5 5L3.5 8" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </span>
                <span v-else class="tree-toggle-spacer"></span>
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" class="tree-folder-icon">
                  <path d="M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span class="tree-item-name">{{ node.name }}</span>
                <button
                  class="tree-item-delete"
                  :title="t('common.delete')"
                  @click.stop="handleDeleteFolder(node)"
                >
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M1.5 3h9M4.5 3V2.25a.75.75 0 0 1 .75-.75h1.5a.75.75 0 0 1 .75.75V3m1.5 0v6.75a.75.75 0 0 1-.75.75H3.75a.75.75 0 0 1-.75-.75V3h6.75z" stroke="currentColor" stroke-width="1" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </button>
              </div>
            </div>

            <div v-else class="sidebar-empty">
              <p>{{ t('admin.userDetail.sidebar.noFolders') }}</p>
              <span>{{ t('admin.userDetail.empty.noFolders') }}</span>
            </div>
          </aside>

          <!-- 右侧：书签列表 -->
          <section class="detail-main">
            <div class="main-header">
              <h3 class="main-title">
                {{ selectedFolderName || t('admin.userDetail.content.selectFolder') }}
              </h3>
              <span v-if="bookmarks.length > 0" class="main-count">{{ bookmarks.length }}</span>
            </div>

            <!-- 书签列表 -->
            <div v-if="bookmarkLoading" class="bookmark-loading">
              <div class="loading-spinner-sm"></div>
            </div>
            <div v-else-if="bookmarks.length > 0" class="bookmark-list">
              <div v-for="bm in bookmarks" :key="bm.id" class="bookmark-row">
                <div class="bm-favicon">
                  <img
                    v-if="bm.icon"
                    :src="getFaviconUrl(bm)"
                    :alt="bm.title"
                    class="bm-favicon-img"
                    @error="$event.target.style.display = 'none'"
                  />
                  <span v-else class="bm-favicon-letter">{{ (bm.title || '?').charAt(0).toUpperCase() }}</span>
                </div>
                <div class="bm-info">
                  <a :href="bm.url" target="_blank" rel="noopener noreferrer" class="bm-title">{{ bm.title }}</a>
                  <span class="bm-url">{{ bm.url }}</span>
                </div>
              </div>
            </div>
            <div v-else-if="selectedFolderId" class="main-empty">
              <p>{{ t('admin.userDetail.content.noBookmarks') }}</p>
            </div>
            <div v-else class="main-empty">
              <p>{{ t('admin.userDetail.content.chooseFolder') }}</p>
            </div>
          </section>
        </div>
      </template>

      <!-- 用户未找到 -->
      <div v-else class="user-not-found">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
          <circle cx="28" cy="28" r="24" stroke="currentColor" stroke-width="1.5" opacity="0.3"/>
          <path d="M20 20l16 16M36 20L20 36" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.4"/>
        </svg>
        <p class="empty-title">{{ t('admin.userDetail.error.notFound') }}</p>
        <p class="empty-desc">{{ t('admin.userDetail.error.notFoundDescription') }}</p>
      </div>
    </main>
  </div>
</template>

<script setup>
/**
 * UserDetailView — 管理员查看用户详情页面
 *
 * 职责：
 * - 展示指定用户的个人信息
 * - 展示用户的文件夹树（只读，可展开/折叠）
 * - 点击文件夹查看该文件夹下的书签
 * - 管理员可删除任意文件夹（带确认）
 *
 * 路由参数：
 * - /admin/users/:id — id 为用户 ID
 *
 * API：
 * - GET /api/admin/users/{userId}/folders/tree
 * - GET /api/folders/{folderId}/bookmarks（复用书签 API）
 * - DELETE /api/admin/folders/{folderId}
 */
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getUserFolderTree, deleteUserFolder } from '@/api/admin'
import { getBookmarks } from '@/api/bookmark'
import { ElMessage, ElMessageBox } from 'element-plus'
import NavBar from '@/components/NavBar.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

// ---- 状态 ----

const userId = computed(() => Number(route.params.id))
const pageLoading = ref(true)
const userData = ref(null)

/** 文件夹树 */
const folderTree = ref([])

/** 展开/折叠状态（用 Set 存储已展开的节点 ID） */
const expandedNodes = ref(new Set())

/** 当前选中的文件夹 ID */
const selectedFolderId = ref(null)

/** 当前选中文件夹名称 */
const selectedFolderName = ref('')

/** 书签列表 */
const bookmarks = ref([])
const bookmarkLoading = ref(false)

// ---- 计算属性 ----

/**
 * 将嵌套的文件夹树扁平化为一维列表
 * 根据展开状态过滤不可见的节点
 */
const flatFolders = computed(() => {
  return flattenVisibleNodes(folderTree.value, 0)
})

// ---- 生命周期 ----

onMounted(async () => {
  await loadUserData()
})

// ---- 数据加载 ----

/**
 * 加载用户数据
 * 当前使用简化的方式：从用户列表 API 获取基本信息
 * 这里先从路由 state 中获取用户数据（如果通过 router.push 传递了 state）
 * 否则重新获取
 */
async function loadUserData() {
  pageLoading.value = true
  try {
    // 从 router state 或 history state 获取用户基本信息
    const stateUser = history.state?.user
    if (stateUser) {
      userData.value = stateUser
    } else {
      // 如果没有 state（例如直接访问 URL），用基础信息构造
      userData.value = {
        id: userId.value,
        username: `User #${userId.value}`,
        email: '',
        role: 'USER',
        status: 'ACTIVE',
        createdAt: null
      }
    }

    // 加载文件夹树
    const res = await getUserFolderTree(userId.value)
    folderTree.value = res.data || []

    // 默认展开根节点
    for (const node of folderTree.value) {
      if (node.children && node.children.length > 0) {
        expandedNodes.value.add(node.id)
      }
    }
  } catch {
    ElMessage.error(t('admin.userDetail.toast.loadFailed'))
  } finally {
    pageLoading.value = false
  }
}

/**
 * 加载指定文件夹的书签
 */
async function loadBookmarks(folderId) {
  bookmarkLoading.value = true
  bookmarks.value = []
  try {
    const res = await getBookmarks(folderId)
    bookmarks.value = res.data || []
  } catch {
    ElMessage.error(t('admin.userDetail.toast.bookmarksLoadFailed'))
  } finally {
    bookmarkLoading.value = false
  }
}

// ---- 文件夹树操作 ----

function toggleNode(nodeId) {
  const newSet = new Set(expandedNodes.value)
  if (newSet.has(nodeId)) {
    newSet.delete(nodeId)
  } else {
    newSet.add(nodeId)
  }
  expandedNodes.value = newSet
}

function handleFolderSelect(node) {
  selectedFolderId.value = node.id
  selectedFolderName.value = node.name
  loadBookmarks(node.id)
}

async function handleDeleteFolder(node) {
  try {
    await ElMessageBox.confirm(
      t('admin.userDetail.deleteConfirm.message', { name: node.name }),
      t('admin.userDetail.deleteConfirm.title'),
      {
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await deleteUserFolder(node.id)
    ElMessage.success(t('admin.userDetail.toast.folderDeleted'))

    // 如果删除的是当前选中的文件夹，清空选中状态
    if (selectedFolderId.value === node.id) {
      selectedFolderId.value = null
      selectedFolderName.value = ''
      bookmarks.value = []
    }

    // 重新加载文件夹树
    const res = await getUserFolderTree(userId.value)
    folderTree.value = res.data || []
  } catch {
    // 用户取消
  }
}

// ---- 导航 ----

function goBack() {
  router.push('/admin/users')
}

// ---- 辅助函数 ----

/**
 * 递归扁平化文件夹树，只保留可见节点（根据展开状态）
 */
function flattenVisibleNodes(nodes, depth) {
  const result = []
  for (const node of nodes) {
    const hasChildren = node.children && node.children.length > 0
    result.push({
      id: node.id,
      name: node.name,
      depth,
      hasChildren
    })
    // 仅当展开时才递归子节点
    if (hasChildren && expandedNodes.value.has(node.id)) {
      result.push(...flattenVisibleNodes(node.children, depth + 1))
    }
  }
  return result
}

function formatDate(dateStr) {
  if (!dateStr) return 'N/A'
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

function getFaviconUrl(bookmark) {
  if (!bookmark.icon) return ''
  if (bookmark.icon.startsWith('http')) return bookmark.icon
  try {
    const url = new URL(bookmark.url)
    return `https://www.google.com/s2/favicons?domain=${url.hostname}&sz=32`
  } catch {
    return ''
  }
}
</script>

<style scoped>
.detail-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--hlaia-bg);
}

/* ---- 主内容区 ---- */
.detail-content {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 24px 28px;
  overflow-y: auto;
}

/* ---- 返回按钮 ---- */
/* 使用 primary 蓝色，与应用整体配色统一 */
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  border: none;
  background: var(--hlaia-surface-light);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 20px;
}

.back-btn:hover {
  background: rgba(74, 127, 199, 0.1);
  color: var(--hlaia-primary-dark);
}

/* ---- 加载状态 ---- */
.page-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
  color: var(--hlaia-text-muted);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
}

.loading-spinner {
  width: 24px; height: 24px;
  border: 2px solid var(--hlaia-border);
  border-top-color: var(--hlaia-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner-sm {
  width: 18px; height: 18px;
  border: 2px solid var(--hlaia-border);
  border-top-color: var(--hlaia-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ---- 用户信息卡片 ---- */
/* 白色卡片 + 阴影 */
.user-info-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 24px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
  margin-bottom: 24px;
}

.user-info-avatar {
  width: 56px; height: 56px; border-radius: 14px;
  background: rgba(74, 127, 199, 0.1);
  display: flex; align-items: center; justify-content: center;
  font-family: 'DM Sans', sans-serif; font-size: 22px; font-weight: 700;
  color: var(--hlaia-primary); flex-shrink: 0;
}

.user-info-main { flex: 1; min-width: 0; }

.user-info-name {
  font-family: 'DM Sans', sans-serif; font-size: 20px; font-weight: 700;
  color: var(--hlaia-text); margin: 0 0 4px;
}

.user-info-email {
  font-family: 'DM Sans', sans-serif; font-size: 13px;
  color: var(--hlaia-text-muted); margin: 0;
}

.user-info-meta {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.meta-item { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
.meta-label {
  font-family: 'DM Sans', sans-serif; font-size: 10px; font-weight: 600;
  letter-spacing: 0.5px; text-transform: uppercase; color: var(--hlaia-text-muted);
}
.meta-value {
  font-family: 'DM Sans', sans-serif; font-size: 13px;
  color: var(--hlaia-text);
}

/* ---- 角色和状态徽章 ---- */
.role-badge {
  display: inline-flex; padding: 3px 10px; border-radius: 6px;
  font-family: 'DM Sans', sans-serif; font-size: 11px;
  font-weight: 600; letter-spacing: 0.3px;
}
.role-admin {
  background: rgba(74, 127, 199, 0.1); color: var(--hlaia-primary);
  border: 1px solid rgba(74, 127, 199, 0.2);
}
.role-user {
  background: var(--hlaia-surface-light); color: var(--hlaia-text-muted);
  border: 1px solid var(--hlaia-border);
}

.status-badge {
  display: inline-flex; padding: 3px 10px; border-radius: 6px;
  font-family: 'DM Sans', sans-serif; font-size: 11px;
  font-weight: 600; letter-spacing: 0.3px;
}
.status-active {
  background: rgba(39, 174, 96, 0.1); color: var(--hlaia-success);
  border: 1px solid rgba(39, 174, 96, 0.2);
}
.status-banned {
  background: rgba(231, 76, 60, 0.1); color: var(--hlaia-danger);
  border: 1px solid rgba(231, 76, 60, 0.2);
}

/* ---- 双栏布局：文件夹树 + 书签列表 ---- */
.detail-body {
  display: flex;
  gap: 20px;
  min-height: 0;
}

/* ---- 左侧边栏：文件夹树 ---- */
/* 白色背景 + 边框 */
.detail-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-radius: var(--hlaia-radius-lg);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  box-shadow: var(--hlaia-shadow);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 260px);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--hlaia-border);
}

.sidebar-title {
  font-family: 'DM Sans', sans-serif; font-size: 14px; font-weight: 600;
  color: var(--hlaia-text); margin: 0;
}

.sidebar-count {
  font-family: 'DM Sans', sans-serif; font-size: 11px;
  color: var(--hlaia-text-muted);
}

.readonly-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
}

.tree-item:hover {
  background: var(--hlaia-surface-light);
}

.tree-item.is-selected {
  background: rgba(74, 127, 199, 0.08);
}

.tree-item.is-selected .tree-item-name {
  color: var(--hlaia-text);
  font-weight: 600;
}

.tree-item.is-selected .tree-folder-icon {
  color: var(--hlaia-primary);
}

.tree-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--hlaia-text-muted);
}

.tree-toggle-spacer {
  width: 14px;
  flex-shrink: 0;
}

.tree-folder-icon {
  flex-shrink: 0;
  color: var(--hlaia-text-muted);
}

.tree-item-name {
  flex: 1;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 删除按钮（悬停时显示） */
.tree-item-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--hlaia-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.tree-item:hover .tree-item-delete {
  opacity: 1;
}

.tree-item-delete:hover {
  background: rgba(231, 76, 60, 0.1);
  color: var(--hlaia-danger);
}

/* 侧边栏空状态 */
.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}

.sidebar-empty p {
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--hlaia-text);
  margin: 0 0 4px;
}

.sidebar-empty span {
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  color: var(--hlaia-text-muted);
}

/* ---- 右侧：书签列表 ---- */
/* 白色卡片 + 阴影 */
.detail-main {
  flex: 1;
  border-radius: var(--hlaia-radius-lg);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  box-shadow: var(--hlaia-shadow);
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-height: calc(100vh - 260px);
}

.main-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--hlaia-border);
}

.main-title {
  font-family: 'DM Sans', sans-serif; font-size: 14px; font-weight: 600;
  color: var(--hlaia-text); margin: 0;
}

.main-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: rgba(74, 127, 199, 0.1);
  color: var(--hlaia-primary);
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  font-weight: 600;
}

/* 书签列表 */
.bookmark-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.bookmark-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  transition: background 0.15s ease;
}

.bookmark-row:hover {
  background: var(--hlaia-surface-light);
}

.bm-favicon {
  width: 32px; height: 32px; border-radius: 6px;
  background: rgba(74, 127, 199, 0.08);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; overflow: hidden;
}

.bm-favicon-img { width: 20px; height: 20px; object-fit: contain; }

.bm-favicon-letter {
  font-family: 'DM Sans', sans-serif; font-size: 14px;
  font-weight: 700; color: var(--hlaia-primary);
}

.bm-info { flex: 1; min-width: 0; }

.bm-title {
  display: block;
  font-family: 'DM Sans', sans-serif; font-size: 13px;
  font-weight: 500; color: var(--hlaia-text);
  text-decoration: none; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis;
  transition: color 0.2s ease;
}

.bm-title:hover { color: var(--hlaia-primary); }

.bm-url {
  display: block;
  font-family: 'DM Sans', sans-serif; font-size: 11px;
  color: var(--hlaia-text-muted); white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis;
  margin-top: 2px;
}

.bookmark-loading {
  padding: 40px;
}

.main-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.main-empty p {
  font-family: 'DM Sans', sans-serif; font-size: 13px;
  color: var(--hlaia-text-muted); margin: 0;
}

/* ---- 用户未找到 ---- */
.user-not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--hlaia-text-muted);
}

.empty-title {
  font-family: 'DM Sans', sans-serif; font-size: 16px;
  font-weight: 600; color: var(--hlaia-text); margin: 12px 0 0;
}

.empty-desc {
  font-family: 'DM Sans', sans-serif; font-size: 13px;
  color: var(--hlaia-text-muted); margin: 4px 0 0;
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .detail-content { padding: 16px; }

  .user-info-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .user-info-meta {
    flex-wrap: wrap;
    gap: 12px;
  }

  .detail-body {
    flex-direction: column;
  }

  .detail-sidebar {
    width: 100%;
    max-height: 300px;
  }

  .detail-main {
    max-height: 400px;
  }
}
</style>
