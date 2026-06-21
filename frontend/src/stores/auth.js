/**
 * Pinia Auth Store — 认证状态管理
 *
 * Pinia 是 Vue 3 官方推荐的状态管理库（替代 Vuex）。
 * 这里用 "Setup Store" 风格（ref + function），类似 Vue 3 Composition API 的写法。
 *
 * 职责：
 * - 管理登录状态（isLoggedIn）、用户名（username）、角色（role）
 * - 提供 login / register / logout 三个 action
 * - 页面刷新时从 sessionStorage 恢复登录状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, register as registerApi, logout as logoutApi } from '@/api/auth'
import { getToken, setToken, getRefreshToken, setRefreshToken, clearTokens, getUserFromToken } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  // ---- State ----

  /** 是否已登录：页面加载时检查 sessionStorage 中是否存在 Token */
  const isLoggedIn = ref(!!getToken())

  /** 从 JWT 中恢复用户信息，避免刷新页面后丢失用户名和角色 */
  const savedUser = getUserFromToken()
  const username = ref(savedUser?.username || savedUser?.sub || '')
  const nickname = ref(savedUser?.nickname || '')
  const role = ref(savedUser?.role || '')

  /** 是否正在请求中（用于按钮 loading 状态） */
  const loading = ref(false)

  // ---- Getters ----

  /** 判断当前用户是否为管理员 */
  const isAdmin = computed(() => role.value === 'ADMIN')

  // ---- Actions ----

  /**
   * 用户登录
   * 调用后端 /api/auth/login，成功后存储 Token 并更新状态
   * @param {Object} credentials - { username, password }
   */
  async function loginAction(credentials) {
    loading.value = true
    try {
      const res = await loginApi(credentials)
      const { accessToken, refreshToken, username: name, nickname: nick, role: userRole } = res.data

      // 将 Token 保存到 sessionStorage（按标签页隔离，支持多账号同时在线）
      setToken(accessToken)
      setRefreshToken(refreshToken)

      // 更新 Store 状态
      username.value = name
      nickname.value = nick || ''
      role.value = userRole
      isLoggedIn.value = true

      return res
    } finally {
      // 无论成功或失败都关闭 loading
      // 使用 finally 而不是在 then/catch 中各写一遍，是更简洁的写法
      loading.value = false
    }
  }

  /**
   * 用户注册
   * 注册成功后自动完成登录（后端直接返回 Token）
   * @param {Object} userData - { username, password }
   */
  async function registerAction(userData) {
    loading.value = true
    try {
      const res = await registerApi(userData)
      const { accessToken, refreshToken, username: name, nickname: nick, role: userRole } = res.data

      setToken(accessToken)
      setRefreshToken(refreshToken)

      username.value = name
      nickname.value = nick || ''
      role.value = userRole
      isLoggedIn.value = true

      return res
    } finally {
      loading.value = false
    }
  }

  /**
   * 用户登出
   * 调用后端 /api/auth/logout（后端会将 Token 加入黑名单），
   * 然后清除本地存储和状态
   */
  async function logoutAction() {
    try {
      // 即使后端登出接口报错，前端也要清除本地状态（防御性编程）
      await logoutApi()
    } finally {
      clearTokens()
      username.value = ''
      nickname.value = ''
      role.value = ''
      isLoggedIn.value = false
    }
  }

  return {
    // state
    isLoggedIn,
    username,
    nickname,
    role,
    loading,
    // getters
    isAdmin,
    // actions
    loginAction,
    registerAction,
    logoutAction
  }
})
