# Design — 持久登录（去轮换 + 365d + 容错 + 扩展同步）

## 总体架构决策

维持"JWT 双 token + Redis 黑名单"架构，只改变三个策略参数：

| 策略 | 现状 | 新设计 | 理由 |
|------|------|--------|------|
| Refresh token 轮换 | 一次性使用，用后拉黑 | 不轮换，自然过期 | 多标签页/扩展共享同一 token 并发刷新必然互踢；单人部署无盗用回放顾虑 |
| Refresh TTL | 7 天绝对 | 365 天滑动（每次刷新重签） | 满足"一年不重登"；滑动窗口=只要一年内用过一次就永不过期 |
| 登出 | 仅拉黑 access token | 同时拉黑 refresh token（新增可选参数） | 长期共享凭据若不拉黑，登出后其余端仍可续期，登出形同虚设 |

失败处理策略（两端统一）：**只有后端明确拒绝才清凭据**。
明确拒绝的判定 = HTTP 200 + body `code===1004`（TOKEN_INVALID）/ `code===401`，或 HTTP 401/403。
其余（网络异常、超时、5xx、非 JSON）一律视为瞬态故障：保留凭据、本次操作失败、下次事件触发时自动重试。

> 背景：本后端业务异常统一返回 HTTP 200 + body code（GlobalExceptionHandler.handleBusinessException，
> HttpStatus.OK）。所以"明确拒绝"主要靠 body code 判定，不能只看 HTTP 状态码。

## 后端改动

### 1. `AuthService.refresh()` 去轮换
- 删除第四步"旧 refresh token 加入黑名单"（AuthService.java:310-315）。
- 保留：validateToken、黑名单检查（登出拉黑依赖它）、用户存在性检查、重签 token 对。
- 每次刷新仍返回**新的** token 对（刷新 TTL 滑动）；旧 refresh token 不作废，自然过期。多个 token 并存属预期（都持有在用户自己的浏览器里）。
- 更新方法级教学注释：解释为什么去掉"用完即废"（多标签页共享 + 并发竞态 + 单人部署取舍）。

### 2. `AuthService.logout()` 支持拉黑 refresh token
- 签名改为 `logout(String accessToken, String refreshToken)`，refreshToken 可为 null。
- 提取私有辅助 `blacklist(String token)`（算剩余 TTL、写 Redis），access/refresh 共用，消除重复。
- AuthController.logout 增加可选参数 `@RequestParam(required = false) String refreshToken`。
- 兼容性：老客户端不传 refreshToken 仍只拉黑 access token，不破坏。

### 3. 配置默认值 7 天 → 365 天
- `application-dev.yml` / `application-prod.yml`：`refresh-token-expiration` 默认 `604800000` → `31536000000`。
- `.env.example`、`.env`（本机部署实际生效值）、`README.md` 环境变量表同步。

## Web 前端改动

### 4. 新增共享静默刷新模块 `frontend/src/api/refresh.js`
绕过 axios 拦截器（裸 axios、无 ElMessage、无循环依赖），供路由守卫与 request.js 复用：

```js
// 返回 { ok: true, auth } 或 { ok: false, reason: 'rejected' | 'transient' }
export async function silentRefresh()
```
- 成功：`setToken/setRefreshToken` 并返回 auth。
- `rejected`：HTTP 200 + code 1004/401，或 HTTP 401/403。
- `transient`：网络错误、超时、5xx、JSON 解析失败。

### 5. `router/index.js` `tryRefresh()` 接入
- 替换现有裸 axios 内联实现 → 调 `silentRefresh()`。
- `rejected` → `clearTokens()` + return false（跳登录页）。
- `transient` → 保留凭据 + return false（仍跳登录页，但网络恢复后下次导航守卫用保留的 token 静默回登录态）。
- 修复现状缺陷：现在 catch 里无差别 `clearTokens()`，502 也会清凭据。

### 6. `api/request.js` `handleTokenExpiredOnHttpLevel()` 接入
- 删除对 `refreshTokenApi`（走拦截器实例）的依赖，改用 `silentRefresh()`。
- `rejected` → clearTokens + 提示 + 跳登录页（现行为）。
- `transient` → 保留凭据、`processQueue(err)`、reject；提示"登录状态刷新失败，请稍后重试"而非"登录已过期"。
- 顺带修复既有 bug：原实现刷新返回 HTTP 200 + code≠200 时 `.then` 里 `setToken(undefined)`。

### 7. `api/auth.js` logout 携带 refreshToken
`request.post('/auth/logout', null, { params: { refreshToken } })`（从 localStorage 读）。

## 扩展改动

### 8. `background.js` `doRefresh()` 容错
判定矩阵：
- `response.ok && code===200` → 成功，存 token，返回 accessToken。
- `response.ok && (code===1004 || code===401)` → 明确拒绝：`clearAuthData()` + 通知 + 打开 options。
- `!response.ok && [401,403].includes(status)` → 明确拒绝：同上。
- 其余（含 status 0 伪响应、5xx、JSON 解析失败）→ 瞬态：保留凭据，warn 日志，返回 null。

### 9. 新增 `extension/content.js` + manifest 注册
- content script（`document_idle`）在导航站页面读取 `localStorage['hlaia_refresh_token']`，存在则 `chrome.runtime.sendMessage({ type:'SYNC_LOGIN', refreshToken })`；不存在不发消息（由后端黑名单机制让扩展自然下线）。
- matches：`https://nav.hlaia.top/*`、`http://localhost:5173/*`（Vite dev）、`http://192.168.8.6:13566/*`（局域网部署）。与 host_permissions 对齐。
- background `onMessage` 新增 `SYNC_LOGIN` 分支：
  - token 与已存储的相同 → 忽略（幂等，避免每次开页面都打后端）。
  - 不同 → 存入 `chrome.storage.local`；用 JWT payload 解码补齐 `username`/`role`（复用与 `frontend/src/utils/auth.js decodeToken` 相同的 base64 解码逻辑）；`lastFolderRefresh=0` 后调 `refreshAccessToken()` → `refreshFolderMenus()`。
- 不需要新权限：host_permissions 已覆盖目标域；content script 读取页面 localStorage 无需额外权限。

### 10. `options/options.js` logout 携带 refreshToken
登出请求加 `?refreshToken=`，保证扩展端登出也全端下线。

## 数据流（改造后）

```
登录（网站）→ localStorage(hlaia_refresh_token, 365d)
  ├─ 任意标签页 sessionStorage 无 access token → 守卫 silentRefresh → 各自 mint access token
  ├─ 多标签页并发 silentRefresh → 全部成功（无轮换无互踢）
  ├─ 打开导航页 → content.js → SYNC_LOGIN → background 存 token → 右键菜单可用
  └─ 登出（web/扩展）→ POST /auth/logout?refreshToken= → Redis 拉黑 refresh token
       → 各端下次 silentRefresh 收到 code 1004 → 清本地凭据 → 全端下线
```

## 权衡与风险

- **被盗 refresh token 无法逐个吊销**：只能登出拉黑（仅拉黑当前持有的）或轮换 JWT secret（全体下线）。单人部署接受。
- **多个有效 refresh token 并存**：每次刷新签新 token，旧的自然过期（≤365d）。均在本机，接受。
- **Redis 中登出黑名单条目变长**：TTL 最长 365d，条目为小字符串，量级可忽略。
- **回滚**：改动均为行为策略，无 schema/迁移。回滚 = revert 提交 + 恢复 env 值；已签发的 365d token 在回滚后仍有效（旧代码只影响新签发），如需强制下线可换 JWT secret。

## 测试策略

- 后端：新增 `AuthServiceTest`（Mockito）覆盖：refresh 不拉黑旧 token、logout 拉黑 access+refresh、refresh 命中黑名单返回 TOKEN_INVALID。跑 `mvn test`。
- 前端：`npm run build` 通过（无单测基建，不新增）。
- 扩展：纯 JS 无构建，人工核对 manifest JSON 合法性；验收项 6/7 手工验证（加载未打包扩展实测）。
