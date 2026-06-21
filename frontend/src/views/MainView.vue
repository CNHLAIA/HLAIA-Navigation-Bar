<!--
  MainView.vue -- 主页面双栏布局

  设计风格：Warm Minimal Light 主题
  - 左侧边栏（~250px）：文件夹树 + 用户信息
  - 右侧内容区：书签网格 + 面包屑导航
  - 顶部导航栏：应用标题、导航链接、语言切换、用户下拉菜单（共享 NavBar 组件）

  响应式：
  - 小屏幕下侧边栏可折叠（汉堡菜单）
  - CSS Grid 实现整体布局
-->
<template>
  <div class="main-layout">
    <!-- 顶部导航栏（共享组件） -->
    <NavBar show-sidebar-toggle @toggle-sidebar="sidebarOpen = !sidebarOpen" />

    <!-- 主体内容区 -->
    <div class="main-body">
      <!-- 左侧边栏 -->
      <aside class="main-sidebar" :class="{ 'is-open': sidebarOpen }">
        <div class="sidebar-content">
          <FolderTree />
        </div>

        <!-- 底部用户信息 -->
        <div class="sidebar-footer">
          <div class="sidebar-user-info">
            <div class="sidebar-user-avatar">{{ avatarLetter }}</div>
            <div class="sidebar-user-details">
              <div class="sidebar-user-name">{{ authStore.nickname || authStore.username || 'User' }}</div>
              <div class="sidebar-user-role">{{ authStore.role || 'USER' }}</div>
            </div>
          </div>
          <button class="sidebar-logout-btn" @click="handleLogout" :title="t('nav.signOut')">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M6 14H3.333A1.333 1.333 0 0 1 2 12.667V3.333A1.333 1.333 0 0 1 3.333 2H6M10.667 11.333L14 8l-3.333-3.333M14 8H6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </aside>

      <!-- 侧边栏遮罩（移动端） -->
      <div
        v-if="sidebarOpen"
        class="sidebar-overlay"
        @click="sidebarOpen = false"
      ></div>

      <!-- 右侧内容区 -->
      <main class="main-content">
        <FolderBreadcrumb
          :path="folderStore.folderPath"
          @navigate="handleBreadcrumbNavigate"
        />
        <BookmarkGrid
          :folder-id="folderStore.currentFolderId"
          :folder-name="currentFolderName"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useFolderStore } from '@/stores/folder'
import { ElMessage } from 'element-plus'
import NavBar from '@/components/NavBar.vue'
import FolderTree from '@/components/FolderTree.vue'
import FolderBreadcrumb from '@/components/FolderBreadcrumb.vue'
import BookmarkGrid from '@/components/BookmarkGrid.vue'

const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()
const folderStore = useFolderStore()

const sidebarOpen = ref(false)

const avatarLetter = computed(() => {
  const name = authStore.nickname || authStore.username || 'U'
  return name.charAt(0).toUpperCase()
})

const currentFolderName = computed(() => {
  return folderStore.currentFolder?.name || ''
})

onMounted(async () => {
  try {
    await folderStore.fetchTree()
  } catch {
    ElMessage.error(t('mainView.toast.foldersLoadFailed'))
  }
})

function handleBreadcrumbNavigate(folderId) {
  folderStore.setCurrentFolder(folderId)
  sidebarOpen.value = false
}

async function handleLogout() {
  try {
    await authStore.logoutAction()
    router.replace('/login')
  } catch {
    // 静默处理
  }
}
</script>

<style scoped>
.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--hlaia-bg);
  position: relative;
}

.main-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  overflow: hidden;
}

.main-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--hlaia-surface);
  border-right: 1px solid var(--hlaia-border);
}

.sidebar-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--hlaia-border);
}

.sidebar-user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.sidebar-user-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.sidebar-user-details { min-width: 0; }

.sidebar-user-name {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-user-role {
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  color: var(--hlaia-text-muted);
  letter-spacing: 0.5px;
}

.sidebar-logout-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.sidebar-logout-btn:hover {
  background: rgba(231, 76, 60, 0.08);
  color: #e74c3c;
}

.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  z-index: 14;
  background: rgba(0, 0, 0, 0.3);
}

.main-content {
  flex: 1;
  padding: 20px 28px;
  overflow-y: auto;
  min-width: 0;
  background: var(--hlaia-bg);
}

@media (max-width: 768px) {
  .main-sidebar {
    position: fixed;
    top: 56px;
    left: 0;
    bottom: 0;
    z-index: 15;
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    background: var(--hlaia-surface);
  }
  .main-sidebar.is-open { transform: translateX(0); }
  .sidebar-overlay { display: block; }
  .main-content { padding: 16px; }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .main-sidebar { width: 220px; }
  .main-content { padding: 16px 20px; }
}
</style>
