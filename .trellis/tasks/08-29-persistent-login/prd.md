# PRD — 持久登录：登录一次，全端保持在线

## 背景

本系统为单人部署（作者自用）：Web 前端 + Chromium 扩展 + Spring Boot 后端。
当前采用"access token 24h + refresh token 7 天 + 一次性轮换（rotation）+ Redis 黑名单"的 JWT 方案，导致三类频繁强制登出：

1. **多标签页轮换竞态（主因）**：refresh token 存 localStorage 被所有标签页共享，`isRefreshing` 去重锁是模块级变量只对单标签页生效。浏览器重启恢复多个标签页时，各标签页同时用同一个 refresh token 调 `/api/auth/refresh`，后端轮换策略只让第一个成功、其余命中黑名单失败并执行 `clearTokens()`，连带清掉共享的 refresh token → 全部标签页登出。
2. **刷新失败处理过激**：网页端 `tryRefresh()` 与扩展端 `doRefresh()` 在收到任何非成功响应（含后端部署期间的 502、网关错误）时都清空凭据强制登出。
3. **7 天绝对上限**：refresh token 最长 7 天，一周未使用必须重新登录。

扩展端还有一个诉求：登录态与网站打通——在网站上登录过，扩展自动获得登录态，无需在 options 页手动登录。

## 目标（单人使用前提下的产品决策）

- 在网站登录一次后，Web 端（任意多标签页、跨浏览器/电脑重启）与扩展端都保持登录，**有效期滑动可达 1 年**（只要一年内使用过一次即永不重登）。
- 打开导航站页面时，扩展自动从网站 localStorage 同步登录态（refresh token），免手动登录。
- 网站上登出 = 全端下线（所有标签页 + 扩展）。
- 接受的安全代价：refresh token 不再一次性作废（被盗后无法逐个吊销，只能登出拉黑 / 换 JWT secret）。单人部署可接受。

## 需求清单

### R1 后端去掉 refresh token 一次性轮换
`AuthService.refresh()` 不再将旧 refresh token 加入黑名单；黑名单检查保留（登出拉黑仍生效）。并发刷新全部成功。

### R2 refresh token 有效期调整为 365 天（滑动）
- `application-dev.yml` / `application-prod.yml` 默认值 604800000 → 31536000000。
- `.env.example`、`.env`、README 环境变量表同步更新。
- 每次刷新重新签发完整 365 天 → 滑动窗口。

### R3 登出必须拉黑 refresh token
去轮换后 refresh token 成为长期共享凭据，现有 logout 只拉黑 access token 不再足够。
- `POST /api/auth/logout` 增加可选 `refreshToken` 请求参数，后端将其一并加入黑名单。
- Web 端与扩展端登出时都携带 refresh token。
- 效果：网页登出后，其他标签页与扩展的下一次刷新即失败并清理本地凭据 → 全端下线。

### R4 刷新失败容错（Web 端）
仅当后端**明确拒绝**（HTTP 200 + body `code===1004` TOKEN_INVALID，或 401/403）时才清除凭据并跳登录页；网络错误、超时、5xx 一律保留凭据（返回失败，让用户稍后重试，下次进入页面守卫会用保留的 token 静默恢复）。
覆盖两处：`router/index.js` 的 `tryRefresh()`、`api/request.js` 的 `handleTokenExpiredOnHttpLevel()`。
顺带修复既有 bug：`request.js` 刷新收到 HTTP 200 + code≠200 时会执行 `setToken(undefined)`。

### R5 刷新失败容错（扩展端）
`extension/background.js` 的 `doRefresh()` 同样区分：明确拒绝（`result.code===1004` 或 `!response.ok` 且状态为 401/403）→ 清凭据 + 通知；网络异常/5xx → 保留凭据静默返回 null。

### R6 扩展从网站自动同步登录态
- 新增 content script（匹配导航站域名），页面加载时读取 `localStorage['hlaia_refresh_token']`，存在则发消息给 background。
- background 收到后存入 `chrome.storage.local`，立即尝试刷新出 access token 并刷新右键菜单。
- 网站未登录（localStorage 无 token）时不做任何清理——由后端黑名单机制在下次刷新时自然下线。
- 仅同步 refresh token；username/role 从 JWT payload 解码获得。

## 验收标准

1. 后端并发调用两次 `/api/auth/refresh`（同一 refresh token）均返回 200 且都签发新 token 对，旧 token 仍可用。
2. refresh 成功后，旧 refresh token **不在** Redis 黑名单中；登出（携带 refreshToken）后，该 token **在**黑名单中，再刷新返回 code 1004。
3. 模拟 refresh 接口返回 502/网络错误：Web 端不清 localStorage 凭据；扩展端不清 `chrome.storage.local` 凭据。
4. 模拟 refresh 接口返回明确拒绝（code 1004）：两端都清凭据并引导重新登录。
5. 配置生效：新签发 refresh token 的 `exp - iat` = 365 天。
6. 打开已登录的导航站页面后，扩展无需手动登录即能调用 `/api/ext/*` 接口（右键菜单出现文件夹列表）。
7. 网页端点登出后：其他标签页与扩展下一次 API 调用被引导至登录态清理，不再能静默续期。
8. 既有功能回归：登录、注册、书签/文件夹操作、扩展右键收藏不受影响。

## 非目标

- 不改造为 HttpOnly Cookie 会话（未来可选的长期架构）。
- 不引入跨标签页广播（BroadcastChannel）等前端同步机制——去轮换后已无共享状态冲突。
- 不改 access token 24h 有效期。
