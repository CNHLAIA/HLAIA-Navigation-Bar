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
import { getToken, getRefreshToken, clearTokens } from '@/utils/auth'
import { silentRefresh } from './refresh'
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
    // blob 响应（文件下载，如书签导出）绕过业务码解包
    // 这类响应没有 { code, message, data } 结构，response.data 是 Blob，
    // 直接返回完整 response 对象，让调用方自行处理下载
    if (response.config.responseType === 'blob') {
      return response
    }

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
        // 后端未配置 AuthenticationEntryPoint 时，token 过期会返回 403 而非 401
        // 尝试一次 refresh，如果刷新后仍然 403 则是真正的权限不足
        if (!error.config._retry && getRefreshToken()) {
          error.config._retry = true
          return handleTokenExpiredOnHttpLevel(error)
        }
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
 * HTTP 层 401/403 处理
 * 尝试用 refreshToken 续期，成功则重试原请求。
 * 失败时按 silentRefresh 的分类决策（rejected / transient，见 api/refresh.js）：
 *   - rejected（后端明确拒绝）→ 清凭据 + 跳登录页
 *   - transient（断网/5xx/限流）→ 保留凭据，仅提示稍后重试，
 *     网络恢复后路由守卫/下一次请求会自动恢复登录态
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

  // silentRefresh 走裸 axios（无拦截器），避免 401 → 刷新 → 又 401 的递归
  return silentRefresh()
    .then(result => {
      if (!result.ok) {
        // 抛给下方 catch 统一按失败分类处理
        throw result
      }
      processQueue(null, result.auth.accessToken)

      originalRequest.headers.Authorization = `Bearer ${result.auth.accessToken}`
      return request(originalRequest)
    })
    .catch(result => {
      // 区分两种入错来源：
      //   1. 上面主动 throw 的 {ok:false, reason} —— 刷新失败，需要在这里决策
      //   2. request(originalRequest) 重试自身的 axios 错误 ——
      //      已经过响应拦截器提示过，直接透传，不再重复弹错
      if (!result || typeof result.reason !== 'string') {
        return Promise.reject(result)
      }

      processQueue(result, null)
      if (result.reason === 'rejected') {
        ElMessage.error('登录已过期，请重新登录')
        clearTokens()
        router.replace('/login')
      } else {
        // 瞬态失败：凭据仍在 localStorage（最长一年有效），绝不能清
        ElMessage.error('登录状态暂时无法刷新，请检查网络后重试')
      }
      return Promise.reject(new Error('token refresh failed: ' + result.reason))
    })
    .finally(() => {
      isRefreshing = false
    })
}

export default request
