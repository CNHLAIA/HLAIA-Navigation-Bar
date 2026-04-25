# API 请求模式规范

> Axios 封装、API 模块组织、错误处理的实际模式。

---

## 概述

本项目使用 Axios 作为 HTTP 客户端，在 `frontend/src/api/request.js` 中创建了预配置的 Axios 实例，统一处理 JWT 认证、Token 自动刷新、错误提示。各业务模块的 API 函数按文件拆分，每个文件对应后端的一个 Controller。

---

## 文件组织

```
frontend/src/api/
├── request.js      # Axios 实例 + 拦截器（核心）
├── auth.js         # /api/auth/** 认证接口
├── bookmark.js     # /api/bookmarks/** + /api/folders/{id}/bookmarks
├── folder.js       # /api/folders/** 文件夹接口
├── staging.js      # /api/staging/** 暂存区接口
├── user.js         # /api/user/** 用户信息接口
└── admin.js        # /api/admin/** 管理员接口
```

每个文件对应后端一个 Controller 模块，import 同一个 `request` 实例。

---

## request.js 核心封装

### Axios 实例配置

```js
// frontend/src/api/request.js
const request = axios.create({
  baseURL: '/api',      // 所有请求自动加 /api 前缀
  timeout: 10000        // 10 秒超时
})
```

- `baseURL: '/api'` -- 开发环境通过 Vite proxy 转发到 `http://localhost:8080`
- `timeout: 10000` -- 默认 10 秒，导入书签等耗时操作在 API 函数中单独设置 60 秒

### 请求拦截器：自动附加 JWT

```js
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

每个请求自动从 `sessionStorage` 读取 Token 并放入 Authorization Header。

### 响应拦截器：统一处理后端响应格式

后端统一返回格式：`{ code: 200, message: "success", data: T }`

```js
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) handleTokenExpired()
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res  // 注意：返回 res（已解包 code 层），不是 response
  },
  (error) => {
    // HTTP 层错误处理（401/403/500/网络异常）
    if (error.response?.status === 401) return handleTokenExpiredOnHttpLevel(error)
    // ...
    return Promise.reject(error)
  }
)
```

关键设计：拦截器返回 `res`（即 `{ code, message, data }`），所以调用方拿到的 `res.data` 就是实际业务数据。

### Token 自动刷新机制

HTTP 401 时使用 refreshToken 静默续期，成功后重试原请求：

```js
let isRefreshing = false
let failedQueue = []  // 等待 Token 刷新的请求队列

function handleTokenExpiredOnHttpLevel(error) {
  if (isRefreshing) {
    // 其他请求排队等待
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject })
    }).then(token => {
      error.config.headers.Authorization = `Bearer ${token}`
      return request(error.config)  // 重试原请求
    })
  }

  isRefreshing = true
  return refreshTokenApi(refreshTokenValue)
    .then(res => {
      const { accessToken, refreshToken: newRefreshToken } = res.data
      setToken(accessToken)
      if (newRefreshToken) setRefreshToken(newRefreshToken)
      processQueue(null, accessToken)  // 唤醒排队的请求
      return request(error.config)  // 重试原请求
    })
    .catch(err => {
      processQueue(err, null)
      clearTokens()
      router.replace('/login')
    })
    .finally(() => { isRefreshing = false })
}
```

实际参考文件：`frontend/src/api/request.js`（第 22-148 行）。

---

## API 模块编写规范

### 文件头部注释

每个 API 文件开头有模块级 JSDoc 注释：

```js
/**
 * 书签相关 API
 *
 * 提供书签的 CRUD、排序、批量删除、批量复制链接操作。
 * 书签属于某个文件夹，通过 folderId 关联。
 */
```

### 函数编写模式

每个 API 函数有独立的 JSDoc 注释，说明参数、返回值和用途：

```js
/**
 * 获取指定文件夹下的书签列表
 * @param {number} folderId - 文件夹 ID
 * @returns {Promise} - { code, data: [bookmarks] }
 */
export function getBookmarks(folderId) {
  return request.get(`/folders/${folderId}/bookmarks`)
}
```

### HTTP 方法使用约定

| 操作 | HTTP 方法 | 示例 |
|------|-----------|------|
| 查询列表/详情 | GET | `request.get('/folders/tree')` |
| 创建 | POST | `request.post('/bookmarks', data)` |
| 全量更新 | PUT | `request.put('/folders/${id}', data)` |
| 删除 | DELETE | `request.delete('/bookmarks/${id}')` |
| 批量操作 | POST | `request.post('/bookmarks/batch-delete', { ids })` |

### URL 路径规范

```js
// RESTful 风格的路径参数用模板字符串
request.get(`/folders/${folderId}/bookmarks`)
request.put(`/bookmarks/${id}`, data)
request.delete(`/staging/${id}`)

// 查询参数用 params 对象
request.get('/admin/users', { params: { page, size } })

// 刷新 Token 的查询参数
request.post('/auth/refresh', null, { params: { refreshToken: token } })
```

### 特殊请求配置

导入书签需要更长的超时和 multipart/form-data：

```js
export function importBookmarks(formData) {
  return request.post('/bookmarks/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000  // 60 秒（覆盖默认的 10 秒）
  })
}
```

---

## 错误处理模式

### 拦截器层（全局）

`request.js` 中的拦截器处理所有 HTTP 级别的错误：
- 业务 code !== 200 -> `ElMessage.error(message)`
- HTTP 401 -> Token 刷新或跳转登录
- HTTP 403 -> "没有操作权限"
- HTTP 500 -> "服务器内部错误"
- 网络异常 -> "网络连接异常，请检查网络"

### Store/组件层（业务）

组件中用 try/catch 处理业务错误，**不需要再显示错误消息**（拦截器已处理）：

```js
async function handleLogin() {
  try {
    await authStore.loginAction({ username: form.username, password: form.password })
    ElMessage.success(t('auth.login.toast.success'))
    router.replace(redirect)
  } catch {
    // 错误已由 axios 拦截器处理，这里不需要额外处理
  }
}
```

### 需要自定义错误处理的场景

当需要覆盖拦截器的默认错误提示时：

```js
async function handleSaveProfile() {
  try {
    await updateProfile({ nickname: form.nickname, email: form.email })
    ElMessage.success(t('settings.profile.toast.saved'))
  } catch {
    ElMessage.error(t('settings.profile.toast.saveFailed'))  // 自定义错误提示
  }
}
```

实际参考文件：`frontend/src/views/SettingsView.vue`（第 263-284 行）。

### 防御性编程模式

登出操作使用 "即使 API 报错也要清除本地状态" 的防御性编程：

```js
// stores/auth.js
async function logoutAction() {
  try {
    await logoutApi()
  } finally {
    // 无论 API 是否成功，都清除本地状态
    clearTokens()
    username.value = ''
    nickname.value = ''
    role.value = ''
    isLoggedIn.value = false
  }
}
```

---

## Vite 代理配置

开发环境通过 Vite dev server 代理避免跨域：

```js
// vite.config.js
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

前端所有 `/api/xxx` 请求在开发时会被代理到后端 `http://localhost:8080/api/xxx`。

生产环境前端打包后放入 Spring Boot 的 `static/` 目录或通过 Nginx 反向代理，不需要 CORS 配置。

---

## Token 存储策略

使用 `sessionStorage`（非 localStorage），实现标签页级别的会话隔离：

```js
// frontend/src/utils/auth.js
const TOKEN_KEY = 'hlaia_access_token'
const REFRESH_KEY = 'hlaia_refresh_token'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearTokens() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}
```

设计理由：
- sessionStorage 按标签页隔离，同一浏览器不同标签页可登录不同账号
- 关闭标签页后 Token 自动清除，对书签导航栏这种常驻标签页场景合理
- 前缀 `hlaia_` 避免与其他应用的 sessionStorage 键冲突

### JWT 解码工具

前端不验证 JWT 签名（由后端验证），仅解码 payload 读取用户信息：

```js
export function decodeToken(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64).split('').map(c =>
        '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
      ).join('')
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}
```

实际参考文件：`frontend/src/utils/auth.js`。
