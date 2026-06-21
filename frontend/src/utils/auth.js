/**
 * Token 存储工具模块
 *
 * 使用 sessionStorage 存储令牌，实现标签页级别的会话隔离。
 * sessionStorage 按标签页隔离：同一浏览器不同标签页拥有独立的存储空间，
 * 因此同一台电脑可以在不同标签页中同时登录不同账号，互不干扰。
 * 代价是关闭标签页后需要重新登录，但对于书签导航栏这种常驻标签页场景是合理的。
 */

const TOKEN_KEY = 'hlaia_access_token'
const REFRESH_KEY = 'hlaia_refresh_token'

/** 获取访问令牌 */
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

/** 设置访问令牌 */
export function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

/** 获取刷新令牌 */
export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_KEY)
}

/** 设置刷新令牌 */
export function setRefreshToken(token) {
  sessionStorage.setItem(REFRESH_KEY, token)
}

/** 清除所有令牌（登出时调用） */
export function clearTokens() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}

/** 解码 JWT payload，提取用户信息（不验证签名，前端仅用于读取） */
export function decodeToken(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

/** 从当前 Token 中提取用户信息（含过期检查） */
export function getUserFromToken() {
  const token = getToken()
  if (!token) return null
  const decoded = decodeToken(token)
  if (!decoded) return null
  if (decoded.exp && decoded.exp * 1000 < Date.now()) {
    clearTokens()
    return null
  }
  return decoded
}
