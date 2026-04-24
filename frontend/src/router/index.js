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
import { getToken } from '@/utils/auth'
import { getUserFromToken } from '@/utils/auth'

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
 */
router.beforeEach((to, from) => {
  const token = getToken()

  // 需要认证但没 Token → 去登录页
  if (to.meta.auth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录用户访问 guest 页面（登录/注册）→ 去首页
  if (to.meta.guest && token) {
    return { path: '/' }
  }

  // 需要管理员权限的路由：从 JWT 中解码角色做前端检查
  // 真正的安全控制始终在后端，前端只是 UI 层面的引导
  if (to.meta.admin) {
    const user = getUserFromToken()
    if (!user || user.role !== 'ADMIN') {
      return { path: '/' }
    }
  }
})

export default router
