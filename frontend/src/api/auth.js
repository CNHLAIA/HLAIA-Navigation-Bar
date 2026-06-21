/**
 * 认证相关 API
 *
 * 封装了注册、登录、登出、刷新 Token 四个接口。
 * 这些接口都走 /api/auth 路径前缀。
 */
import request from './request'

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
 * 需要在 Header 中携带当前 Token，后端会将该 Token 加入 Redis 黑名单
 * @returns {Promise}
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 刷新访问令牌
 * 当 accessToken 过期时，用 refreshToken 换取新的 token 对
 * @param {string} refreshToken
 * @returns {Promise} - { code, data: { accessToken, refreshToken, username, role } }
 */
export function refreshToken(token) {
  return request.post('/auth/refresh', null, {
    params: { refreshToken: token }
  })
}
