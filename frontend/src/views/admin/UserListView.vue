<!--
  UserListView.vue — 管理员用户列表页面

  设计风格：
  - 温暖极简浅色主题（Warm Minimal Light），与应用整体风格统一
  - 白色导航栏 + 浅色背景
  - 白色表格卡片，使用 --hlaia-border 边框
  - 状态徽章：Active 绿色 / Banned 红色（浅色背景）
  - 分页控件：浅色主题
  - 支持 i18n 中英文切换

  功能：
  - 分页表格展示用户列表
  - 操作：查看详情、封禁/解封
  - 封禁操作需要二次确认
-->
<template>
  <div class="admin-layout">
    <!-- 顶部导航栏（共享组件） -->
    <NavBar />

    <!-- 主内容区 -->
    <main class="admin-content">
      <!-- 顶部标题栏 -->
      <div class="admin-top-bar">
        <div class="admin-title-area">
          <h1 class="admin-title">{{ t('admin.users.title') }}</h1>
          <span v-if="totalUsers > 0" class="admin-count">{{ totalUsers }} {{ t('admin.users.countSuffix') }}</span>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="tableLoading" class="table-loading">
        <div class="loading-spinner"></div>
        <span>{{ t('admin.users.loading') }}</span>
      </div>

      <!-- 用户表格 -->
      <template v-else>
        <div v-if="users.length > 0" class="table-wrapper">
          <table class="user-table">
            <thead>
              <tr>
                <th>{{ t('admin.users.tableHeaders.username') }}</th>
                <th>{{ t('admin.users.tableHeaders.email') }}</th>
                <th>{{ t('admin.users.tableHeaders.role') }}</th>
                <th>{{ t('admin.users.tableHeaders.status') }}</th>
                <th>{{ t('admin.users.tableHeaders.joined') }}</th>
                <th class="th-actions">{{ t('admin.users.tableHeaders.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>
                  <div class="user-cell">
                    <div class="user-cell-avatar">{{ (user.username || '?').charAt(0).toUpperCase() }}</div>
                    <span class="user-cell-name">{{ user.username }}</span>
                  </div>
                </td>
                <td class="td-email">{{ user.email || '-' }}</td>
                <td>
                  <span class="role-badge" :class="user.role === 'ADMIN' ? 'role-admin' : 'role-user'">
                    {{ user.role }}
                  </span>
                </td>
                <td>
                  <span class="status-badge" :class="user.status === 'BANNED' ? 'status-banned' : 'status-active'">
                    {{ user.status === 'BANNED' ? t('admin.users.status.banned') : t('admin.users.status.active') }}
                  </span>
                </td>
                <td class="td-date">{{ formatDate(user.createdAt) }}</td>
                <td>
                  <div class="action-cell">
                    <button
                      class="table-action-btn btn-view"
                      @click="goToDetail(user.id)"
                      :title="t('admin.users.tooltips.viewDetails')"
                    >
                      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <path d="M7 2.333C3.5 2.333 1.167 7 1.167 7s2.333 4.667 5.833 4.667S12.833 7 12.833 7s-2.333-4.667-5.833-4.667z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                        <circle cx="7" cy="7" r="1.75" stroke="currentColor" stroke-width="1.2"/>
                      </svg>
                    </button>
                    <button
                      v-if="user.role !== 'ADMIN'"
                      class="table-action-btn"
                      :class="user.status === 'BANNED' ? 'btn-unban' : 'btn-ban'"
                      :title="user.status === 'BANNED' ? t('admin.users.tooltips.unban') : t('admin.users.tooltips.ban')"
                      @click="handleToggleBan(user)"
                    >
                      <svg v-if="user.status === 'BANNED'" width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <circle cx="7" cy="7" r="5.25" stroke="currentColor" stroke-width="1.2"/>
                        <path d="M7 4.375V7l1.75 1.75" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                        <path d="M2.5 2.5l9 9" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                      </svg>
                      <svg v-else width="14" height="14" viewBox="0 0 14 14" fill="none">
                        <circle cx="7" cy="7" r="5.25" stroke="currentColor" stroke-width="1.2"/>
                        <path d="M7 4.375V7l1.75 1.75" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                      </svg>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <!-- 分页控件 -->
          <div class="pagination-wrapper">
            <button
              class="page-btn"
              :disabled="currentPage <= 1"
              @click="loadPage(currentPage - 1)"
            >
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M8.75 3.5L5.25 7l3.5 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              {{ t('common.previous') }}
            </button>
            <div class="page-info">
              {{ currentPage }} / {{ totalPages }}
            </div>
            <button
              class="page-btn"
              :disabled="currentPage >= totalPages"
              @click="loadPage(currentPage + 1)"
            >
              {{ t('common.next') }}
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M5.25 3.5L8.75 7l-3.5 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="admin-empty">
          <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
            <path d="M28 28a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM8 48s4-12 20-12 20 12 20 12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <p class="empty-title">{{ t('admin.users.empty.noUsers') }}</p>
          <p class="empty-desc">{{ t('admin.users.empty.description') }}</p>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
/**
 * UserListView — 管理员用户列表页面
 *
 * 职责：
 * - 分页展示所有用户
 * - 提供封禁/解封操作
 * - 点击查看详情跳转到 UserDetailView
 *
 * API：
 * - GET /api/admin/users?page=1&size=20
 * - PUT /api/admin/users/{id}/ban
 * - PUT /api/admin/users/{id}/unban
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getUsers, banUser, unbanUser } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import NavBar from '@/components/NavBar.vue'

const { t } = useI18n()

// ---- 状态 ----

const users = ref([])
const tableLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalUsers = ref(0)

// ---- 计算属性 ----

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(totalUsers.value / pageSize.value))
})

// ---- 生命周期 ----

onMounted(() => {
  loadPage(1)
})

// ---- 数据加载 ----

async function loadPage(page) {
  tableLoading.value = true
  try {
    const res = await getUsers(page, pageSize.value)
    const data = res.data
    users.value = data.records || []
    totalUsers.value = data.total || 0
    currentPage.value = data.current || page
  } catch {
    ElMessage.error(t('admin.users.toast.loadFailed'))
  } finally {
    tableLoading.value = false
  }
}

// ---- 操作 ----

function goToDetail(userId) {
  // 将当前用户数据通过 router state 传递给详情页
  // 这样详情页无需额外请求即可展示基本信息
  const user = users.value.find(u => u.id === userId)
  router.push({
    path: `/admin/users/${userId}`,
    state: { user: user || null }
  })
}

async function handleToggleBan(user) {
  const isBanned = user.status === 'BANNED'

  if (!isBanned) {
    // 封禁需要二次确认
    try {
      await ElMessageBox.confirm(
        t('admin.users.banConfirm.message', { username: user.username }),
        t('admin.users.banConfirm.title'),
        {
          confirmButtonText: t('common.delete'),
          cancelButtonText: t('common.cancel'),
          type: 'warning'
        }
      )
    } catch {
      return // 用户取消
    }
  }

  try {
    if (isBanned) {
      await unbanUser(user.id)
      ElMessage.success(t('admin.users.toast.unbanned', { username: user.username }))
    } else {
      await banUser(user.id)
      ElMessage.success(t('admin.users.toast.banned', { username: user.username }))
    }
    // 刷新当前页
    await loadPage(currentPage.value)
  } catch {
    ElMessage.error(t('admin.users.toast.actionFailed'))
  }
}

// ---- 辅助 ----

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--hlaia-bg);
}

/* ---- 主内容区 ---- */
.admin-content {
  position: relative;
  z-index: 1;
  flex: 1;
  padding: 24px 28px;
  overflow-y: auto;
}

.admin-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.admin-title-area { display: flex; align-items: center; gap: 10px; }

.admin-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--hlaia-text);
  margin: 0;
}

.admin-count {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

/* ---- 加载状态 ---- */
.table-loading {
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

@keyframes spin { to { transform: rotate(360deg); } }

/* ---- 表格容器 ---- */
/* 白色卡片 + 阴影 */
.table-wrapper {
  border-radius: var(--hlaia-radius-lg);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  box-shadow: var(--hlaia-shadow);
  overflow: hidden;
}

/* ---- 自定义表格 ---- */
.user-table {
  width: 100%;
  border-collapse: collapse;
  font-family: 'DM Sans', sans-serif;
}

.user-table thead {
  background: var(--hlaia-surface-light);
}

.user-table th {
  padding: 12px 16px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  color: var(--hlaia-text-muted);
  border-bottom: 1px solid var(--hlaia-border);
}

.user-table th.th-actions {
  text-align: right;
}

.user-table td {
  padding: 14px 16px;
  font-size: 13px;
  color: var(--hlaia-text);
  border-bottom: 1px solid var(--hlaia-border);
  vertical-align: middle;
}

.user-table tbody tr {
  transition: background 0.15s ease;
}

.user-table tbody tr:hover {
  background: var(--hlaia-surface-light);
}

.user-table tbody tr:last-child td {
  border-bottom: none;
}

/* 用户单元格 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell-avatar {
  width: 32px; height: 32px; border-radius: 8px;
  background: rgba(74, 127, 199, 0.1);
  display: flex; align-items: center; justify-content: center;
  font-family: 'DM Sans', sans-serif; font-size: 13px; font-weight: 700;
  color: var(--hlaia-primary); flex-shrink: 0;
}

.user-cell-name {
  font-weight: 500;
  color: var(--hlaia-text);
}

.td-email { color: var(--hlaia-text-muted); }
.td-date { color: var(--hlaia-text-muted); font-size: 12px; }

/* ---- 角色徽章 ---- */
.role-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.role-admin {
  background: rgba(74, 127, 199, 0.1);
  color: var(--hlaia-primary);
  border: 1px solid rgba(74, 127, 199, 0.2);
}

.role-user {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  border: 1px solid var(--hlaia-border);
}

/* ---- 状态徽章 ---- */
/* Active = 绿色浅底，Banned = 红色浅底 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.status-active {
  background: rgba(39, 174, 96, 0.1);
  color: var(--hlaia-success);
  border: 1px solid rgba(39, 174, 96, 0.2);
}

.status-banned {
  background: rgba(231, 76, 60, 0.1);
  color: var(--hlaia-danger);
  border: 1px solid rgba(231, 76, 60, 0.2);
}

/* ---- 操作按钮 ---- */
.action-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.table-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.table-action-btn:hover {
  background: var(--hlaia-surface);
  color: var(--hlaia-text);
}

.btn-view:hover {
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.08);
  border-color: rgba(74, 127, 199, 0.2);
}

.btn-ban:hover {
  color: var(--hlaia-danger);
  background: rgba(231, 76, 60, 0.08);
  border-color: rgba(231, 76, 60, 0.2);
}

.btn-unban:hover {
  color: var(--hlaia-success);
  background: rgba(39, 174, 96, 0.08);
  border-color: rgba(39, 174, 96, 0.2);
}

/* ---- 分页控件 ---- */
/* 浅色主题分页 */
.pagination-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-top: 1px solid var(--hlaia-border);
}

.page-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--hlaia-surface);
  color: var(--hlaia-primary);
  border-color: var(--hlaia-primary-light);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

/* ---- 空状态 ---- */
.admin-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--hlaia-text-muted);
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
  margin: 4px 0 0;
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .admin-content { padding: 16px; }

  .user-table th, .user-table td {
    padding: 10px 10px;
  }

  .td-email, .td-date {
    display: none;
  }
}
</style>
