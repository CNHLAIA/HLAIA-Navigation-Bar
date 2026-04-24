/**
 * Axios 请求封装 + JWT 拦截器
 *
 * 这里创建了一个预配置的 Axios 实例，统一处理：
 * 1. 请求拦截：自动在每个请求的 Header 中附带 JWT Token
 * 2. 响应拦截：统一处理后端返回的 { code, message, data } 格式
 * 3. Token 自动刷新：401 时尝试用 refreshToken 续期，避免强制重新登录
 *
 * 设计模式：拦截器模式（Interceptor Pattern）
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, getRefreshToken, setRefreshToken, clearTokens } from '@/utils/auth'
import { refreshToken as refreshTokenApi } from './auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Token 刷新状态管理，防止多个请求同时刷新
let isRefreshing = false
let failedQueue = []

/** 处理等待中的请求队列 */
function processQueue(error, token = null) {
  failedQueue.forEach(cb => {
    if (error) cb.reject(error)
    else cb.resolve(token)
  })
  failedQueue = []
}

/**
 * 请求拦截器
 */
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data

    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')

      if (res.code === 401) {
        handleTokenExpired()
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  (error) => {
    if (error.response) {
      const status = error.response.status

      if (status === 401) {
        return handleTokenExpiredOnHttpLevel(error)
      } else if (status === 403) {
        ElMessage.error('没有操作权限')
      } else if (status === 500) {
        ElMessage.error('服务器内部错误')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接异常，请检查网络')
    }

    return Promise.reject(error)
  }
)

/**
 * 业务层 401 处理（res.code === 401）
 * 直接清除并跳转，这种情况通常是 Token 被后端明确拒绝
 */
function handleTokenExpired() {
  clearTokens()
  router.replace('/login')
}

/**
 * HTTP 层 401 处理
 * 尝试用 refreshToken 续期，成功则重试原请求；失败则清除并跳转
 */
function handleTokenExpiredOnHttpLevel(error) {
  const originalRequest = error.config

  // 防止重复刷新
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject })
    }).then(token => {
      originalRequest.headers.Authorization = `Bearer ${token}`
      return request(originalRequest)
    })
  }

  const refreshTokenValue = getRefreshToken()
  if (!refreshTokenValue) {
    ElMessage.error('登录已过期，请重新登录')
    clearTokens()
    router.replace('/login')
    return Promise.reject(error)
  }

  isRefreshing = true

  return refreshTokenApi(refreshTokenValue)
    .then(res => {
      const { accessToken, refreshToken: newRefreshToken } = res.data
      setToken(accessToken)
      if (newRefreshToken) setRefreshToken(newRefreshToken)

      processQueue(null, accessToken)

      originalRequest.headers.Authorization = `Bearer ${accessToken}`
      return request(originalRequest)
    })
    .catch(err => {
      processQueue(err, null)
      ElMessage.error('登录已过期，请重新登录')
      clearTokens()
      router.replace('/login')
      return Promise.reject(err)
    })
    .finally(() => {
      isRefreshing = false
    })
}

export default request
