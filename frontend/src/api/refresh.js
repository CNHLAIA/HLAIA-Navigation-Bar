/**
 * 静默刷新模块 —— 用 refresh token 换取新的 token 对
 *
 * ============================================================
 * 为什么独立成模块，而不用 api/auth.js 里的 refreshToken()？
 * ============================================================
 *   本模块的调用方是 axios 响应拦截器（request.js）和路由守卫（router/index.js），
 *   它们处理的是"认证本身失效"的场景，绝不能再走带拦截器的 request 实例——
 *   那会造成拦截器递归（401 → 刷新 → 又 401 → 又刷新）和重复弹错。
 *   所以这里直接用裸 axios，不经过任何拦截器，也不弹任何 UI 提示，
 *   把"成功/明确拒绝/瞬态失败"的结构化结果交还给调用方决策。
 *
 * ============================================================
 * 失败分类（本次"持久登录"改造的核心约定）
 * ============================================================
 *   'no-token'   本地没有 refresh token（未登录过）
 *   'rejected'   后端明确拒绝：token 无效/已登出拉黑/用户已删除。
 *                这是永久性失败，调用方应清除本地凭据并引导重新登录。
 *   'transient'  瞬态失败：断网、超时、5xx、被限流（code 2007）。
 *                凭据本身没问题，调用方必须保留凭据，稍后自动重试即可恢复。
 *
 *   为什么瞬态失败绝不能清凭据？
 *     曾经的实现里 502/断网也会 clearTokens()，导致后端一次临时部署
 *     就把用户踢下线。localStorage 里的 refresh token 有效期长达一年，
 *     保留它，网络恢复后下一次路由守卫就能静默换回 access token。
 *
 * ============================================================
 * 后端契约（见 AuthService.refresh）
 * ============================================================
 *   业务异常统一返回 HTTP 200 + body {code, message}：
 *     code 200  → 成功，data 含新的 accessToken/refreshToken
 *     code 1004 → TOKEN_INVALID（无效或已登出拉黑）
 *     code 2007 → RATE_LIMITED（限流，瞬态）
 *   刷新不再轮换：旧 refresh token 刷新后依然有效（多标签页并发安全），
 *   每次刷新返回的新 refresh token 用于滑动延长有效期。
 */
import axios from 'axios'
import { getRefreshToken, setToken, setRefreshToken } from '@/utils/auth'

/** 后端业务码：请求过于频繁（限流），瞬态失败 */
const CODE_RATE_LIMITED = 2007

/**
 * 静默刷新 access token
 *
 * 成功时顺带完成新 token 的持久化（副作用集中在一处，两个调用方共享）。
 *
 * @returns {Promise<{ok: true, auth: Object} | {ok: false, reason: 'no-token'|'rejected'|'transient'}>}
 */
export async function silentRefresh() {
  const refreshTokenValue = getRefreshToken()
  if (!refreshTokenValue) {
    return { ok: false, reason: 'no-token' }
  }

  try {
    // 裸 axios + 相对路径：同源部署下直连后端，开发环境走 Vite 代理
    const res = await axios.post('/api/auth/refresh', null, {
      params: { refreshToken: refreshTokenValue },
      // 刷新失败按瞬态处理的前提是"尽快放弃"：超时视为网络异常而非拒绝
      timeout: 10000
    })

    const body = res.data
    if (body?.code === 200 && body.data) {
      setToken(body.data.accessToken)
      if (body.data.refreshToken) setRefreshToken(body.data.refreshToken)
      return { ok: true, auth: body.data }
    }

    // HTTP 200 但业务码非 200：可能是 1004（token 无效/已拉黑）、
    // 401（未认证）、用户已删除等——都是服务端的最终裁定，归为明确拒绝；
    // 唯一例外是限流（2007），它只说明"请求太勤"，不代表凭据有问题
    if (body?.code === CODE_RATE_LIMITED) {
      return { ok: false, reason: 'transient' }
    }
    return { ok: false, reason: 'rejected' }
  } catch (error) {
    // axios 只对非 2xx 状态码抛错：
    //   401/403 → 认证被明确拒绝；其余（5xx/超时/断网）→ 瞬态失败
    const status = error.response?.status
    if (status === 401 || status === 403) {
      return { ok: false, reason: 'rejected' }
    }
    return { ok: false, reason: 'transient' }
  }
}
