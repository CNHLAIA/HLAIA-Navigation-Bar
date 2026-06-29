/**
 * Token 存储工具模块
 *
 * 混合存储策略（解决浏览器重启后会话丢失的问题）：
 * - access token → sessionStorage：按标签页隔离，支持不同标签页登录不同账号。
 *   代价是关闭标签页即失效，但下面会用 refresh token 静默续期来弥补。
 * - refresh token → localStorage：跨浏览器/系统重启存活。
 *   这样重启电脑后重新打开网站时，access token 虽然没了，但 router 守卫能用
 *   localStorage 里的 refresh token 换回新的 access token，无需再次登录。
 *
 * 安全性说明：refresh token 由后端在登出时加入黑名单（Redis），
 * 前端仅作为"免重登"凭据存储；真正的权限校验始终在后端。
 */

const TOKEN_KEY = 'hlaia_access_token'
const REFRESH_KEY = 'hlaia_refresh_token'

/** 获取访问令牌（sessionStorage，按标签页隔离） */
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

/** 设置访问令牌 */
export function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

/**
 * 获取刷新令牌（localStorage，跨重启存活）
 * 注意：与 access token 不同，这里用 localStorage，重启后仍在
 */
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

/** 设置刷新令牌 */
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_KEY, token)
}

/**
 * 清除所有令牌（登出 / token 彻底失效时调用）
 * 必须同时清两处存储，否则残留的 refresh token 会导致已登出账号被静默拉回
 */
export function clearTokens() {
  sessionStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
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
    // access token 过期只清自身，保留 localStorage 里的 refresh token 以便续期
    sessionStorage.removeItem(TOKEN_KEY)
    return null
  }
  return decoded
}
