<template>
  <header class="app-navbar">
    <!-- 左侧：汉堡菜单（移动端） + Logo -->
    <div class="navbar-left">
      <button v-if="showSidebarToggle" class="sidebar-toggle" @click="$emit('toggleSidebar')">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
      <div class="navbar-brand">
        <div class="brand-icon">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <rect x="1" y="1" width="16" height="16" rx="3" stroke="currentColor" stroke-width="1.5"/>
            <path d="M5 7h8M5 9h6M5 11h7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
        </div>
        <span class="brand-text">HLAIA</span>
      </div>
    </div>

    <!-- 中间：导航链接 -->
    <nav class="navbar-links">
      <router-link to="/" class="nav-link" :class="{ active: route.path === '/' }">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M2 6l6-4.5L14 6v7.5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 13.5V6z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        {{ t('nav.bookmarks') }}
      </router-link>
      <router-link to="/staging" class="nav-link" :class="{ active: route.path === '/staging' }">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <rect x="2" y="2" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.2"/>
          <path d="M5 6h6M5 8h4M5 10h5" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
        </svg>
        {{ t('nav.staging') }}
      </router-link>
      <router-link to="/settings" class="nav-link" :class="{ active: route.path === '/settings' }">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <circle cx="8" cy="8" r="3" stroke="currentColor" stroke-width="1.2"/>
          <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.05 3.05l1.41 1.41M11.54 11.54l1.41 1.41M3.05 12.95l1.41-1.41M11.54 4.46l1.41-1.41" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
        </svg>
        {{ t('nav.settings') }}
      </router-link>
      <router-link
        v-if="authStore.isAdmin"
        to="/admin/users"
        class="nav-link"
        :class="{ active: route.path.startsWith('/admin') }"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM2 14s1-4 6-4 6 4 6 4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        {{ t('nav.admin') }}
      </router-link>
    </nav>

    <!-- 右侧：语言切换 + 用户信息下拉 -->
    <div class="navbar-right">
      <button class="locale-toggle" @click="toggleLocale" :title="locale === 'zh-CN' ? 'Switch to English' : '切换到中文'">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <circle cx="8" cy="8" r="6.5" stroke="currentColor" stroke-width="1.2"/>
          <ellipse cx="8" cy="8" rx="3" ry="6.5" stroke="currentColor" stroke-width="1"/>
          <path d="M1.5 8h13M2.5 5h11M2.5 11h11" stroke="currentColor" stroke-width="0.8"/>
        </svg>
        <span class="locale-label">{{ locale === 'zh-CN' ? '中文' : 'EN' }}</span>
      </button>

      <el-dropdown trigger="click" @command="handleUserCommand">
        <button class="user-btn">
          <div class="user-avatar">{{ avatarLetter }}</div>
          <span class="user-name">{{ authStore.nickname || authStore.username || 'User' }}</span>
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <span class="dropdown-role-badge">{{ authStore.role }}</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              {{ t('nav.signOut') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
/**
 * NavBar — 共享顶部导航栏组件
 *
 * 所有已认证页面共用：主页面、暂存区、管理后台
 * 包含：Logo、导航链接、语言切换、用户下拉菜单
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

defineProps({
  showSidebarToggle: { type: Boolean, default: false }
})

defineEmits(['toggleSidebar'])

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const authStore = useAuthStore()

const avatarLetter = computed(() => {
  const name = authStore.nickname || authStore.username || 'U'
  return name.charAt(0).toUpperCase()
})

function toggleLocale() {
  const newLocale = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLocale
  localStorage.setItem('hlaia-locale', newLocale)
}

function handleUserCommand(command) {
  if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  try {
    await authStore.logoutAction()
    router.replace('/login')
  } catch {
    // 登出失败静默处理
  }
}
</script>

<style scoped>
.app-navbar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 20px;
  background: var(--hlaia-surface);
  border-bottom: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.sidebar-toggle:hover {
  background: var(--hlaia-border);
  color: var(--hlaia-text);
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-primary);
  color: #fff;
}

.brand-text {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--hlaia-text);
}

.navbar-links {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 32px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 6px;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text-muted);
  text-decoration: none;
  transition: all 0.2s ease;
}

.nav-link:hover {
  color: var(--hlaia-text);
  background: var(--hlaia-surface-light);
}

.nav-link.active {
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.08);
}

.navbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.locale-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: 'DM Sans', sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.locale-toggle:hover {
  border-color: var(--hlaia-primary);
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.04);
}

.locale-label {
  line-height: 1;
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-btn:hover {
  border-color: var(--hlaia-primary-light);
  background: var(--hlaia-surface-light);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--hlaia-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.user-name {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text);
}

.user-btn svg {
  color: var(--hlaia-text-muted);
}

.dropdown-role-badge {
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
}

@media (max-width: 768px) {
  .sidebar-toggle {
    display: flex;
  }
  .navbar-links {
    display: none;
  }
}

/* Element Plus dropdown overrides */
:deep(.el-dropdown-menu) {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
  padding: 4px;
  min-width: 140px;
  box-shadow: var(--hlaia-shadow-hover);
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
