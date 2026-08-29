/**
 * 认证相关 API
 *
 * 封装注册、登录、登出三个接口，走 /api/auth 路径前缀。
 * （token 刷新不走这里——认证失效场景由 api/refresh.js 的裸 axios 静默处理，
 *   避免经过 request 拦截器造成 401 → 刷新 → 再 401 的递归。）
 */
import request from './request'
import { getRefreshToken } from '@/utils/auth'

/**
 * 用户登录
 * @param {Object} data - { username, password }
 * @returns {Promise} - { code, data: { accessToken, refreshToken, username, role } }
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 用户注册
 * @param {Object} data - { username, password }
 * @returns {Promise} - { code, data: { accessToken, refreshToken, username, role } }
 */
export function register(data) {
  return request.post('/auth/register', data)
}

/**
 * 用户登出
 * 需要在 Header 中携带当前 Token，后端会将 Token 加入 Redis 黑名单
 *
 * 为什么要把 refreshToken 也传给后端？
 *   refresh token 已改为"不轮换 + 一年有效期"的长期凭据，
 *   只拉黑 access token 的话，本机存的 refresh token 仍能静默续期，
 *   登出就形同虚设。带上它，后端一并拉黑，实现"一处登出，全端下线"
 *   （其他标签页和浏览器扩展的下一次刷新会立即失效）。
 *
 * @returns {Promise}
 */
export function logout() {
  return request.post('/auth/logout', null, {
    params: { refreshToken: getRefreshToken() || undefined }
  })
}
