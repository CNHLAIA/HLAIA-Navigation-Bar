/**
 * HLAIA Navigation Bar — Content Script（登录态自动同步）
 *
 * ============================================================
 * 这个文件的作用
 * ============================================================
 *   只在导航站页面（nav.hlaia.top 等域名）上运行。页面加载时读取
 *   网页端存在 localStorage 里的 refresh token（key: hlaia_refresh_token，
 *   见 frontend/src/utils/auth.js），如果存在就发给 background，
 *   让扩展自动获得登录态——用户在网站上登录过一次，
 *   扩展就无需再在设置页手动登录。
 *
 * ============================================================
 * 为什么可行 / 安全吗？
 * ============================================================
 *   - Content script 与页面同源运行，可以读取该站点的 localStorage。
 *   - manifest 的 host_permissions 已覆盖这些域名，注入是声明式的。
 *   - background 侧会用 sender.id === chrome.runtime.id 校验消息来源，
 *     且 token 本身是否有效由后端校验（签名/黑名单），伪造无意义。
 *
 * ============================================================
 * 设计约定
 * ============================================================
 *   - 只在有 token 时发消息。网页端登出后 localStorage 会被清空，
 *     这里不发"登出"消息——扩展持有的旧 token 会被后端黑名单拒绝，
 *     在下一次刷新时自然下线（见 background.js doRefresh 的失败分类）。
 *   - 消息是幂等的：token 没变化时 background 直接忽略，
 *     所以每次打开页面重复发送没有副作用。
 */

/** 网页端存储 refresh token 的 localStorage key（与 frontend/src/utils/auth.js 保持一致） */
const WEB_REFRESH_TOKEN_KEY = 'hlaia_refresh_token';

// 读取网页端登录态。可能为 null（未登录/已登出），此时什么都不做
const refreshToken = localStorage.getItem(WEB_REFRESH_TOKEN_KEY);

if (refreshToken) {
  // sendMessage 在扩展刚重载、context 失效时会 reject，静默忽略即可：
  // 下次页面加载会再同步，不丢状态
  chrome.runtime
    .sendMessage({ type: 'SYNC_LOGIN', refreshToken })
    .catch(e => console.warn('SYNC_LOGIN send failed:', e?.message || e));
}
