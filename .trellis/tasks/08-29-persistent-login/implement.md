# Implement — 执行清单

## 顺序与验证

按依赖排序：后端先行（客户端容错逻辑依赖"明确拒绝"语义稳定），再 Web，再扩展。

### Step 1 后端：去轮换 + logout 拉黑 refresh token
- [ ] `AuthService.refresh()`：删除"旧 token 入黑名单"步骤；更新教学注释（为什么去掉用完即废）
- [ ] `AuthService.logout(String accessToken, String refreshToken)`：新增可选参数；提取 `blacklist()` 私有辅助消除重复
- [ ] `AuthController.logout`：`@RequestParam(required = false) String refreshToken`
- [ ] 新增 `src/test/java/com/hlaia/service/AuthServiceTest.java`：
  - refresh 成功后旧 refresh token **不**在黑名单
  - logout 拉黑 access + refresh 两个 token
  - refresh 命中黑名单 → BusinessException(TOKEN_INVALID)
- [ ] 验证：`mvn -q test`（AGENTS.local.md 若有 java25 环境变量先 source）

### Step 2 后端：365 天配置
- [ ] `application-dev.yml` / `application-prod.yml`：`refresh-token-expiration` 默认 `31536000000`
- [ ] `.env.example`、`.env`：`JWT_REFRESH_TOKEN_EXPIRATION=31536000000`
- [ ] `README.md` 环境变量表默认值 + 说明
- [ ] 验证：`mvn -q test`

### Step 3 Web：共享静默刷新 + 容错
- [ ] 新增 `frontend/src/api/refresh.js`：`silentRefresh()` 返回 `{ok, auth} | {ok:false, reason:'rejected'|'transient'}`
- [ ] `router/index.js` `tryRefresh()`：接入 silentRefresh；rejected→clearTokens，transient→保留凭据
- [ ] `api/request.js` `handleTokenExpiredOnHttpLevel()`：接入 silentRefresh；rejected→清+跳转，transient→保留+提示"稍后重试"；修复 setToken(undefined) bug
- [ ] `api/auth.js` `logout()`：携带 refreshToken 查询参数
- [ ] 验证：`cd frontend && npm run build`

### Step 4 扩展：doRefresh 容错
- [ ] `background.js` `doRefresh()`：按判定矩阵区分明确拒绝（code 1004/401 或 HTTP 401/403）与瞬态故障（5xx/网络/解析失败），瞬态不清凭据
- [ ] 验证：node --check extension/background.js

### Step 5 扩展：content script 同步登录态
- [ ] 新增 `extension/content.js`：读 `hlaia_refresh_token` → `SYNC_LOGIN` 消息
- [ ] `manifest.json`：注册 content_scripts（nav.hlaia.top / localhost:5173 / 192.168.8.6:13566）
- [ ] `background.js`：`SYNC_LOGIN` 分支（幂等比较、JWT 解码补 username/role、触发 refresh+菜单刷新）
- [ ] `options/options.js`：logout 请求携带 refreshToken
- [ ] 验证：node --check；manifest JSON 解析；人工加载扩展实测（验收 6/7）

### Step 6 收尾
- [ ] 对照 prd.md 验收标准 1-5、8 逐条核对（6/7 需人工浏览器验证，列出操作步骤交用户）
- [ ] trellis-check 全量检查
- [ ] 更新 spec（若沉淀出新约定）
- [ ] 提交（分主题 conventional commits：backend / frontend / extension）

## 回滚点

- 每个 Step 独立可回滚；Step 2 改 env 值注意部署时 `.env` 是否随 compose 注入（提交信息中提醒）。
- 全量回滚：revert 全部提交；已签发的 365d token 仍有效，必要时轮换 JWT_SECRET 强制下线。

## 风险提示

- 扩展为未打包本地加载，manifest 改动（content_scripts）需在 chrome://extensions 手动重载后才生效——验收时别忘。
- `.env` 若被 .gitignore 忽略则只改本地文件不进提交，README/.env.example 承担文档职责。
