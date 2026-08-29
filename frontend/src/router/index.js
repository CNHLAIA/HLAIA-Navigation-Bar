/**
 * Vue Router 路由配置
 *
 * 路由守卫（Navigation Guard）：
 * - beforeEach 在每次路由跳转前执行
 * - 检查目标路由的 meta.auth 字段，如果需要认证但本地没有 Token，则重定向到登录页
 * - 检查 meta.admin 字段，如果需要管理员权限但当前不是管理员，则重定向到首页
 *
 * 路由懒加载：
 * - 使用 () => import(...) 动态导入，Vite 会将每个路由打包成独立的 JS 文件
 * - 只有访问该路由时才会加载对应的代码，减少首屏加载时间
 */
import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUserFromToken, clearTokens } from '@/utils/auth'
import { silentRefresh } from '@/api/refresh'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterView.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    name: 'Main',
    component: () => import('../views/MainView.vue'),
    meta: { auth: true }
  },
  {
    path: '/staging',
    name: 'Staging',
    component: () => import('../views/StagingView.vue'),
    meta: { auth: true }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/SettingsView.vue'),
    meta: { auth: true }
  },
  {
    path: '/admin/users',
    name: 'UserList',
    component: () => import('../views/admin/UserListView.vue'),
    meta: { auth: true, admin: true }
  },
  {
    path: '/admin/users/:id',
    name: 'UserDetail',
    component: () => import('../views/admin/UserDetailView.vue'),
    meta: { auth: true, admin: true }
  },
  // 仅开发期生效的导出页预览路由；生产构建会被守卫重定向，不进入正式菜单
  ...(import.meta.env.DEV
    ? [{
        path: '/dev-export',
        name: 'DevExportPreview',
        component: () => import('../views/dev/ExportPreview.vue'),
        meta: { guest: true }
      }]
    : []),
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundView.vue'),
    meta: { guest: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 用 refresh token 静默换取新的 access token
 *
 * 统一封装，供路由守卫复用。覆盖两类场景：
 * 1. access token 过期（getUserFromToken 返回 null，但 sessionStorage 里还在）
 * 2. access token 完全丢失（如重启电脑 / 浏览器会话恢复清空了 sessionStorage），
 *    此时 refresh token 仍在 localStorage 里，可借此"免重登"恢复会话
 *
 * 实际的请求和 token 持久化由 api/refresh.js 的 silentRefresh 完成，
 * 这里只做失败分类决策（见其头部注释的 rejected / transient 约定）。
 *
 * @returns {Promise<boolean>} true=刷新成功；false=刷新失败
 */
async function tryRefresh() {
  const result = await silentRefresh()
  if (result.ok) {
    return true
  }

  if (result.reason === 'rejected') {
    // 后端明确拒绝（token 无效/已登出拉黑/用户已删除）：
    // 永久性失败，彻底清空凭据，避免守卫下次再发无意义的刷新请求
    clearTokens()
    return false
  }

  // 'no-token'（从未登录，无凭据可清）或 'transient'（断网/5xx/限流）：
  // 保留 refresh token。瞬态失败时本次导航虽仍会跳登录页，
  // 但网络恢复后下一次导航守卫会用保留的凭据静默换回登录态，
  // 用户不会被迫重新输入密码——这是"持久登录"的关键容错路径。
  return false
}

/**
 * 全局前置守卫
 *
 * to: 即将进入的目标路由
 * from: 当前导航正要离开的路由
 *
 * 返回 false 或路由路径可以取消/重定向当前导航
 *
 * 会话恢复场景（重启电脑后浏览器自动恢复标签页）的处理：
 * 1. sessionStorage 的 access token 被清空，但 localStorage 的 refresh token 还在
 * 2. 守卫检测到 access token 缺失，尝试用 refresh token 静默续期
 * 3. 续期成功则继续导航（用户无感，不会被打到登录页）
 * 4. 续期失败才跳登录页
 */
router.beforeEach(async (to) => {
  const token = getToken()
  // 只解码一次 JWT，后续复用 user 变量，避免重复的同步 base64 解码阻塞主线程
  const user = getUserFromToken()

  // 需要认证的路由
  if (to.meta.auth) {
    // 没有 access token（首次进入、标签页恢复、或已过期被 getUserFromToken 清除）
    if (!token || !user) {
      const refreshed = await tryRefresh()
      if (!refreshed) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
      // 刷新成功，继续向下走（user 变量下面会重新取，admin 检查需要新 token 的角色）
    }
  }

  // 重新从（可能已刷新的）token 解析用户，供后续 guest / admin 判断使用
  const currentUser = getUserFromToken()

  // 已登录用户访问 guest 页面（登录/注册）→ 去首页
  if (to.meta.guest && currentUser) {
    return { path: '/' }
  }

  // 需要管理员权限的路由：从 JWT 中解码角色做前端检查
  // 真正的安全控制始终在后端，前端只是 UI 层面的引导
  if (to.meta.admin) {
    if (!currentUser || currentUser.role !== 'ADMIN') {
      return { path: '/' }
    }
  }
})

export default router
