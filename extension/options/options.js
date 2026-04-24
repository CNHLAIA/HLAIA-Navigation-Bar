/**
 * HLAIA Navigation Bar — Options Page Script
 *
 * ============================================================
 * 这个文件的作用
 * ============================================================
 *   options.js 是扩展"设置页"的脚本。用户在设置页中可以：
 *   1. 输入用户名和密码登录（获取 JWT Token）
 *   2. 查看当前登录状态（用户名、角色）
 *   3. 退出登录（清除 Token）
 *   4. 配置 API 服务器地址（默认 http://localhost:8080）
 *
 * ============================================================
 * chrome.storage.local vs localStorage
 * ============================================================
 *   chrome.storage.local 是 Chrome 扩展专用的持久化存储 API：
 *   - 数据在扩展的所有页面（options page、popup、background）之间共享
 *   - 数据持久化，关闭浏览器后不会丢失
 *   - 通过 chrome.storage.local.get() / set() 异步访问
 *
 *   普通网页的 localStorage：
 *   - 数据按域名隔离，扩展的不同页面无法共享
 *   - Service Worker（background.js）中没有 localStorage
 *   - 所以扩展必须使用 chrome.storage.local
 */

// ============================================================
// DOM 元素引用
// ============================================================

const loginSection = document.getElementById('loginSection');
const loggedInSection = document.getElementById('loggedInSection');
const messageEl = document.getElementById('message');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const serverUrlInput = document.getElementById('serverUrl');
const loginBtn = document.getElementById('loginBtn');
const logoutBtn = document.getElementById('logoutBtn');
const displayUsername = document.getElementById('displayUsername');
const displayRole = document.getElementById('displayRole');

// ============================================================
// 页面加载时：恢复存储的设置和登录状态
// ============================================================

/**
 * DOMContentLoaded 事件在 HTML 文档完全加载和解析后触发。
 * 在这里初始化页面状态。
 */
document.addEventListener('DOMContentLoaded', async () => {
  // 从 chrome.storage.local 读取之前保存的设置
  const data = await chrome.storage.local.get(['token', 'username', 'role', 'serverUrl']);

  // 恢复服务器地址（如果有保存过的话）
  if (data.serverUrl) {
    serverUrlInput.value = data.serverUrl;
  }

  // 根据是否有 Token 来显示登录/已登录状态
  if (data.token && data.username) {
    showLoggedIn(data.username, data.role);
  } else {
    showLoginForm();
  }
});

// ============================================================
// 登录逻辑
// ============================================================

/**
 * 登录按钮点击事件
 *
 * 流程：
 *   1. 获取用户输入的用户名、密码和服务器地址
 *   2. 调用 POST /api/auth/login 接口
 *   3. 后端返回 { code: 200, data: { accessToken, refreshToken, username, role } }
 *   4. 将 Token 和用户信息保存到 chrome.storage.local
 *   5. 通知 background.js 刷新文件夹菜单
 *   6. 更新页面显示为"已登录"状态
 */
loginBtn.addEventListener('click', async () => {
  const username = usernameInput.value.trim();
  const password = passwordInput.value.trim();
  const serverUrl = serverUrlInput.value.trim() || 'http://localhost:8080';

  // 前端校验：用户名和密码不能为空
  if (!username || !password) {
    showMessage('请输入用户名和密码', 'error');
    return;
  }

  // 禁用登录按钮，防止重复点击
  loginBtn.disabled = true;
  loginBtn.textContent = '登录中...';
  hideMessage();

  try {
    // 调用后端登录接口
    const response = await fetch(`${serverUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const result = await response.json();

    if (response.ok && result.code === 200) {
      // 登录成功
      const authData = result.data;

      // 保存到 chrome.storage.local
      // 这些数据会在 background.js 中被读取（用于 API 认证）
      await chrome.storage.local.set({
        token: authData.accessToken,
        refreshToken: authData.refreshToken,
        username: authData.username,
        role: authData.role,
        serverUrl: serverUrl
      });

      // 通知 background.js 用户已登录，让它立即刷新文件夹菜单
      chrome.runtime.sendMessage({ type: 'LOGIN_SUCCESS' });

      // 更新页面显示
      showLoggedIn(authData.username, authData.role);
      showMessage('登录成功！', 'success');

      // 清空密码输入框（安全考虑）
      passwordInput.value = '';
    } else {
      // 登录失败（用户名或密码错误等）
      showMessage(result.message || '登录失败，请检查用户名和密码', 'error');
    }
  } catch (error) {
    // 网络错误（服务器不可达等）
    console.error('Login error:', error);
    showMessage('无法连接到服务器，请检查网络和服务器地址', 'error');
  } finally {
    // 恢复登录按钮状态
    loginBtn.disabled = false;
    loginBtn.textContent = '登录';
  }
});

// ============================================================
// 登出逻辑
// ============================================================

/**
 * 退出登录按钮点击事件
 *
 * 流程：
 *   1. 清除 chrome.storage.local 中的所有认证数据
 *   2. 通知 background.js 用户已登出（让它清除文件夹菜单）
 *   3. 更新页面显示为"未登录"状态
 *
 * 注意：这里不调用后端的 /api/auth/logout 接口
 *   因为后端的 logout 会把 Token 加入黑名单（存入 Redis），
 *   对于扩展来说，直接清除本地存储就足够了。
 *   如果后续需要更严格的登出策略，可以加上后端调用。
 */
logoutBtn.addEventListener('click', async () => {
  // 保留 serverUrl 配置，只清除认证相关数据
  const { serverUrl } = await chrome.storage.local.get('serverUrl');
  await chrome.storage.local.clear();
  if (serverUrl) {
    await chrome.storage.local.set({ serverUrl });
  }

  // 通知 background.js 用户已登出
  chrome.runtime.sendMessage({ type: 'LOGOUT' });

  // 更新页面显示
  showLoginForm();
  showMessage('已退出登录', 'success');
});

// ============================================================
// 服务器地址配置
// ============================================================

/**
 * 服务器地址输入框的 change 事件
 *
 * 用户修改服务器地址后自动保存到 chrome.storage.local。
 * background.js 每次发请求前都会读取最新的 serverUrl。
 * 使用 'change' 事件而非 'input' 事件，避免每次按键都触发保存。
 */
serverUrlInput.addEventListener('change', async () => {
  const serverUrl = serverUrlInput.value.trim();
  if (serverUrl) {
    await chrome.storage.local.set({ serverUrl });
  }
});

// ============================================================
// UI 更新函数
// ============================================================

/**
 * 切换到"已登录"状态的界面
 * @param {string} username - 用户名
 * @param {string} role - 用户角色（如 'ADMIN', 'USER'）
 */
function showLoggedIn(username, role) {
  loginSection.style.display = 'none';
  loggedInSection.style.display = 'block';
  displayUsername.textContent = username;
  displayRole.textContent = role === 'ADMIN' ? '管理员' : '普通用户';
}

/**
 * 切换到"未登录"状态的界面（显示登录表单）
 */
function showLoginForm() {
  loginSection.style.display = 'block';
  loggedInSection.style.display = 'none';
}

/**
 * 显示提示消息
 * @param {string} text - 消息文本
 * @param {string} type - 消息类型：'error' 或 'success'
 */
function showMessage(text, type) {
  messageEl.textContent = text;
  messageEl.className = `message ${type} visible`;
}

/**
 * 隐藏提示消息
 */
function hideMessage() {
  messageEl.className = 'message';
}

// ============================================================
// 回车键快捷登录
// ============================================================

/**
 * 在密码输入框中按 Enter 键时，自动触发登录按钮点击。
 * 这是一种常见的用户体验优化，用户不需要用鼠标点击登录按钮。
 */
passwordInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    loginBtn.click();
  }
});
