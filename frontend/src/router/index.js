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
import { getToken, getUserFromToken, getRefreshToken, setToken, setRefreshToken, clearTokens } from '@/utils/auth'
import axios from 'axios'

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
 * 全局前置守卫
 *
 * to: 即将进入的目标路由
 * from: 当前导航正要离开的路由
 *
 * 返回 false 或路由路径可以取消/重定向当前导航
 *
 * Token 过期时的处理流程：
 * 1. getUserFromToken() 会检查 exp 字段，过期返回 null
 * 2. 如果 access token 过期但 refresh token 还在，尝试静默刷新
 * 3. 刷新成功则继续导航，失败则跳转登录页
 */
router.beforeEach(async (to, from) => {
  const token = getToken()
  // 只解码一次 JWT，后续复用 user 变量，避免重复的同步 base64 解码阻塞主线程
  const user = getUserFromToken()

  // 需要认证的路由
  if (to.meta.auth) {
    // 完全没有 token → 去登录页
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    // token 过期（getUserFromToken 返回 null），尝试用 refresh token 静默续期
    if (!user) {
      const refreshTokenValue = getRefreshToken()
      if (!refreshTokenValue) {
        clearTokens()
        return { path: '/login', query: { redirect: to.fullPath } }
      }

      try {
        // 直接用 axios 调用，避免循环依赖（request.js → auth.js → request.js）
        const res = await axios.post('/api/auth/refresh?refreshToken=' + encodeURIComponent(refreshTokenValue))
        if (res.data?.code === 200 && res.data?.data) {
          setToken(res.data.data.accessToken)
          if (res.data.data.refreshToken) setRefreshToken(res.data.data.refreshToken)
          // 刷新成功，继续导航
        } else {
          clearTokens()
          return { path: '/login', query: { redirect: to.fullPath } }
        }
      } catch {
        clearTokens()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
  }

  // 已登录用户访问 guest 页面（登录/注册）→ 去首页
  if (to.meta.guest && token && user) {
    return { path: '/' }
  }

  // 需要管理员权限的路由：从 JWT 中解码角色做前端检查
  // 真正的安全控制始终在后端，前端只是 UI 层面的引导
  if (to.meta.admin) {
    if (!user || user.role !== 'ADMIN') {
      return { path: '/' }
    }
  }
})

export default router
