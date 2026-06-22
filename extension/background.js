/**
 * HLAIA Navigation Bar — Background Service Worker (Manifest V3)
 *
 * ============================================================
 * 什么是 Service Worker？
 * ============================================================
 *   在 Chrome 扩展的 Manifest V3 中，background.js 以 Service Worker 的形式运行。
 *   Service Worker 是一种特殊的 JavaScript 运行环境：
 *   - 没有页面（no DOM），不能操作 document 或 window
 *   - 但可以监听浏览器事件（如右键菜单点击、标签页切换）
 *   - 不持久运行，浏览器会在空闲时终止它，需要时再唤醒
 *   - 所以不能依赖全局变量保持状态，需要用 chrome.storage 持久化
 *
 * ============================================================
 * 这个文件的核心职责
 * ============================================================
 *   1. 注册右键上下文菜单（用户右键时出现"收藏到 HLAIA 导航栏"）
 *   2. 动态创建文件夹子菜单（从后端获取文件夹树）
 *   3. 处理用户的菜单点击（保存书签到指定文件夹，或添加到暂存区）
 *   4. 通过 chrome.notifications 反馈操作结果
 *   5. 自动使用 refreshToken 刷新过期的 accessToken，避免频繁重新登录
 */

// ============================================================
// 常量定义
// ============================================================

/** 顶级上下文菜单的 ID */
const PARENT_MENU_ID = 'hlaia-save';

/** 暂存区菜单项的 ID */
const STAGING_MENU_ID = 'hlaia-staging';

/** 动态文件夹菜单项的前缀（文件夹菜单 ID = 前缀 + folderId） */
const FOLDER_MENU_PREFIX = 'hlaia-folder-';

/** 文件夹菜单的刷新间隔（毫秒），标签页切换时的节流 */
const FOLDER_REFRESH_INTERVAL = 30 * 1000; // 30 秒

/** 上次刷新文件夹菜单的时间戳 */
let lastFolderRefresh = 0;

// ============================================================
// 网络异常统一处理
// ============================================================

/**
 * 构造一个表示"网络层失败"的伪 Response 对象
 *
 * 为什么需要这个？
 *   fetch() 在断网、DNS 解析失败、服务器完全不可达、CORS 被拒等情况下
 *   不是返回带状态码的 Response，而是直接 reject（抛 TypeError）。
 *   如果这个异常逃逸到 chrome 事件监听器，会变成 unhandled rejection，
 *   在 chrome://extensions 卡片上留下红色错误标志——它不会自动清除，
 *   看起来就像"扩展坏了，必须移除重装"，但功能其实仍正常。
 *
 *   这里把网络异常包装成一个 status: 0 的伪 Response，让上层调用方
 *   可以用与处理 HTTP 错误相同的分支（!response.ok）来处理它，
 *   不必在每个调用点都写 try/catch。
 *
 * @returns {Response} - status === 0 的只读伪响应
 */
function makeNetworkErrorResponse() {
  return new Response(JSON.stringify({ code: 0, message: '网络连接失败' }), {
    status: 0,
    headers: { 'Content-Type': 'application/json' }
  });
}

// ============================================================
// Token 自动刷新机制
// ============================================================

/**
 * 带自动 Token 刷新的 API 请求封装
 *
 * 为什么需要这个函数？
 *   accessToken 有效期较短（24 小时），过期后需要用 refreshToken（7 天有效）换新的。
 *   之前没有自动刷新逻辑，导致 Token 过期后用户必须手动重新登录。
 *   这个函数在收到 401 时自动尝试刷新 Token 并重试请求，用户无感知。
 *
 * 工作流程：
 *   1. 从 chrome.storage.local 读取 accessToken
 *   2. 发送请求，如果返回 401（Token 过期），自动调用 refreshAccessToken()
 *   3. 刷新成功后用新 Token 重试原始请求
 *   4. 刷新也失败则返回 401 响应，让调用方处理（提示用户重新登录）
 *
 * @param {string} url - 请求的完整 URL
 * @param {RequestInit} options - fetch 的选项（method, headers, body 等）
 * @returns {Response|null} - fetch 响应对象；未登录（无 Token）时返回 null
 */
async function authFetch(url, options = {}) {
  const { token, refreshToken } = await chrome.storage.local.get(['token', 'refreshToken']);

  // 没有 Token：返回 null，调用方据此判断用户未登录
  if (!token) return null;

  // 合并 Authorization 请求头
  options.headers = {
    ...options.headers,
    'Authorization': `Bearer ${token}`
  };

  // 用 try/catch 包住 fetch：断网、DNS 失败、服务器不可达、CORS 拒绝时
  // fetch 会抛 TypeError，统一转成 status:0 伪响应交给上层处理。
  // 这样异常就不会逃逸到 chrome 事件监听器，避免留下无法清除的红色错误标志。
  let response;
  try {
    response = await fetch(url, options);
  } catch (error) {
    console.warn('Network error during fetch:', error?.message || error);
    return makeNetworkErrorResponse();
  }

  // 收到 401 且有 refreshToken：尝试刷新
  if (response.status === 401 && refreshToken) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      // 用新 Token 重试原始请求
      options.headers['Authorization'] = `Bearer ${newToken}`;
      try {
        response = await fetch(url, options);
      } catch (error) {
        // 重试也可能遇到网络问题，同样转成伪响应
        console.warn('Network error during retry:', error?.message || error);
        return makeNetworkErrorResponse();
      }
    }
  }

  return response;
}

/**
 * 使用 refreshToken 获取新的 accessToken
 *
 * 调用后端接口：POST /api/auth/refresh?refreshToken=xxx
 * 后端返回：{ code: 200, data: { accessToken, refreshToken, username, role } }
 *
 * 为什么刷新后还要保存新的 refreshToken？
 *   后端采用"旋转刷新令牌"策略——每次使用 refreshToken 后，旧的即失效，返回新的。
 *   所以每次刷新后必须保存最新的 refreshToken，否则下次刷新会失败。
 *
 * @returns {string|null} - 新的 accessToken；刷新失败返回 null
 */
async function refreshAccessToken() {
  const { refreshToken, serverUrl } = await chrome.storage.local.get(['refreshToken', 'serverUrl']);
  const baseUrl = serverUrl || 'https://nav.hlaia.top';

  if (!refreshToken) return null;

  try {
    // encodeURIComponent 确保 refreshToken 中的特殊字符不会破坏 URL
    const response = await fetch(
      `${baseUrl}/api/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`,
      { method: 'POST' }
    );

    if (response.ok) {
      const result = await response.json();
      if (result.code === 200 && result.data) {
        const authData = result.data;
        // 保存新的 Token 和用户信息到 chrome.storage.local
        await chrome.storage.local.set({
          token: authData.accessToken,
          refreshToken: authData.refreshToken,
          username: authData.username,
          role: authData.role
        });
        return authData.accessToken;
      }
    }

    // refreshToken 也过期了（或无效），清除所有认证数据
    await clearAuthData();
    showNotification('登录已过期', '请打开扩展设置页重新登录');
    chrome.runtime.openOptionsPage();
    return null;
  } catch (error) {
    // 用 warn 而非 error：网络抖动导致的 refresh 失败是预期内的偶发状况，
    // 不应触发 chrome://extensions 卡片的红色错误标志。
    // 注意此处不清理 token——refresh 本身可能是临时网络问题，
    // 留着 token 下次请求成功后仍可用。
    console.warn('Failed to refresh token:', error?.message || error);
    return null;
  }
}

/**
 * 清除 chrome.storage.local 中的认证数据（保留 serverUrl 配置）
 *
 * 为什么保留 serverUrl？
 *   服务器地址是用户手动配置的，与登录状态无关。
 *   如果清除后丢失，用户需要重新输入地址才能登录，体验不好。
 */
async function clearAuthData() {
  const { serverUrl } = await chrome.storage.local.get('serverUrl');
  await chrome.storage.local.clear();
  if (serverUrl) {
    await chrome.storage.local.set({ serverUrl });
  }
}

// ============================================================
// 扩展安装 / 更新时：注册右键上下文菜单
// ============================================================

/**
 * chrome.runtime.onInstalled 事件在以下情况触发：
 *   - 扩展首次安装（reason === 'install'）
 *   - 扩展更新到新版本（reason === 'update'）
 *   - Chrome 浏览器更新（reason === 'chrome_update'）
 *
 * 我们在安装时创建顶级菜单项。子菜单项（文件夹列表）会在用户
 * 切换标签页时动态创建，这样可以确保菜单中的文件夹是最新的。
 */
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: PARENT_MENU_ID,
    title: '收藏到 HLAIA 导航栏',
    contexts: ['page', 'link']
  });
});

// ============================================================
// 标签页切换时：刷新文件夹子菜单
// ============================================================

/**
 * 当用户切换到新的标签页时，刷新右键菜单中的文件夹列表。
 *
 * 为什么不在 onInstalled 时一次性创建所有子菜单？
 *   因为用户可能在 Web 端新增/删除了文件夹，扩展需要同步最新的文件夹结构。
 *   Service Worker 可能被浏览器回收后又唤醒，全局变量会丢失，
 *   所以每次标签页切换时检查是否需要刷新。
 *
 * 为什么用 lastFolderRefresh 做节流？
 *   用户快速切换多个标签页时，onActivated 会连续触发多次。
 *   如果每次都请求后端，会造成不必要的网络开销。
 *   节流机制：距离上次刷新不到 30 秒则跳过。
 */
chrome.tabs.onActivated.addListener(async (activeInfo) => {
  await refreshFolderMenus();
});

/**
 * 监听右键菜单即将显示的事件（Chrome 116+）
 *
 * 为什么需要这个监听？
 *   tabs.onActivated 的节流可能导致用户创建文件夹后右键时菜单还没刷新。
 *   onShown 在菜单实际渲染前触发，可以确保每次右键都能拿到最新的文件夹列表。
 *   这比缩短节流间隔更可靠，因为只在用户真正要使用菜单时才请求后端。
 *
 * 为什么用 if (chrome.contextMenus.onShown)？
 *   这个 API 从 Chrome 116 开始提供，做个兼容性检查避免旧版本报错。
 */
if (chrome.contextMenus.onShown) {
  chrome.contextMenus.onShown.addListener(async (info, tab) => {
    const showing = info.menuIds || [];
    if (!showing.includes(PARENT_MENU_ID)) return;

    // 重置节流计时器，强制立即刷新
    lastFolderRefresh = 0;
    await refreshFolderMenus();
    chrome.contextMenus.refresh();
  });
}

/**
 * 刷新文件夹子菜单的核心逻辑
 *
 * 流程：
 *   1. 检查是否在节流时间窗口内（30 秒内不重复刷新）
 *   2. 使用 authFetch 调用 GET /api/ext/folders/tree（自动处理 Token 刷新）
 *   3. authFetch 返回 null：用户未登录，移除子菜单
 *   4. 请求成功：移除旧的子菜单，根据文件夹树创建新的子菜单
 */
async function refreshFolderMenus() {
  const now = Date.now();
  if (now - lastFolderRefresh < FOLDER_REFRESH_INTERVAL) {
    return;
  }

  const { serverUrl } = await chrome.storage.local.get('serverUrl');
  const baseUrl = serverUrl || 'https://nav.hlaia.top';

  // 整体用 try/catch 兜底：authFetch 内部已把网络异常转成 status:0 伪响应，
  // 但仍可能因 JSON 解析、storage 等原因抛错。这里捕获后只打 warn，
  // 不让异常逃逸到 chrome.tabs.onActivated / contextMenus.onShown 监听器，
  // 避免在 chrome://extensions 留下无法清除的红色错误标志。
  // 保留旧菜单（不调用 removeDynamicMenus），下次切换标签页会自动重试。
  try {
    // 使用 authFetch 发送请求（自动附加 Token，401 时自动刷新）
    const response = await authFetch(`${baseUrl}/api/ext/folders/tree`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' }
    });

    // authFetch 返回 null：没有 Token（用户未登录）
    if (!response) {
      await removeDynamicMenus();
      return;
    }

    // 0：网络层失败（断网、服务器不可达）。不清理旧菜单，下次自动重试。
    // 用 warn 而非 error——这是预期的偶发状况，不应触发扩展卡片红标。
    if (response.status === 0) {
      console.warn('Network unreachable, skipped folder refresh');
      return;
    }

    // 401：Token 已过期且刷新也失败（refreshAccessToken 已处理清理和提示）
    if (response.status === 401) {
      await removeDynamicMenus();
      return;
    }

    if (!response.ok) {
      console.warn('Failed to fetch folders:', response.status);
      return;
    }

    // response.json() 在服务器返回非 JSON（如网关 502 的 HTML 错误页）时
    // 会抛 SyntaxError，这里单独保护，避免污染上层 try。
    let result;
    try {
      result = await response.json();
    } catch (e) {
      console.warn('Invalid JSON from folders/tree:', e?.message || e);
      return;
    }

    const folders = result.data || [];

    await removeDynamicMenus();

    // 创建"暂存区"菜单项
    chrome.contextMenus.create({
      id: STAGING_MENU_ID,
      parentId: PARENT_MENU_ID,
      title: '保存到暂存区（稍后整理）',
      contexts: ['page', 'link']
    });

    // 分隔线
    chrome.contextMenus.create({
      id: 'hlaia-separator',
      parentId: PARENT_MENU_ID,
      type: 'separator',
      contexts: ['page', 'link']
    });

    // 扁平化创建文件夹菜单项
    createFlatFolderMenus(folders, PARENT_MENU_ID);

    lastFolderRefresh = now;
  } catch (error) {
    // 兜底：任何未预期的异常都不应污染 chrome 事件监听器。
    console.warn('refreshFolderMenus failed:', error?.message || error);
  }
}

/**
 * 将文件夹树扁平化，创建单层上下文菜单项
 *
 * 为什么用扁平化而不是嵌套菜单？
 *   Chrome 右键菜单有嵌套深度限制（约 6-7 层），超过限制的子文件夹会被静默丢弃。
 *   扁平化后，所有文件夹都显示在一级菜单下，用路径名表示层级关系，
 *   例如 "工作资料 > 前端 > Vue" 表示 Vue 是"工作资料/前端"下的子文件夹。
 *
 * @param {Array} folders - 文件夹树（FolderTreeResponse 数组，含 children）
 * @param {string} parentId - 父菜单项的 ID（固定为 PARENT_MENU_ID）
 */
function createFlatFolderMenus(folders, parentId) {
  if (!folders || folders.length === 0) return;

  function flatten(nodes, path, result) {
    for (const node of nodes) {
      const displayName = path ? `${path} > ${node.name || '未命名文件夹'}` : (node.name || '未命名文件夹');
      result.push({ id: node.id, displayName });
      if (node.children && node.children.length > 0) {
        flatten(node.children, displayName, result);
      }
    }
  }

  const flatList = [];
  flatten(folders, '', flatList);
  flatList.sort((a, b) => a.displayName.localeCompare(b.displayName, 'zh-CN'));

  for (const item of flatList) {
    chrome.contextMenus.create({
      id: `${FOLDER_MENU_PREFIX}${item.id}`,
      parentId: parentId,
      title: item.displayName,
      contexts: ['page', 'link']
    });
  }
}

/**
 * 移除所有动态创建的菜单项
 *
 * 为什么需要这个函数？
 *   每次刷新文件夹列表时，需要先移除旧的菜单再创建新的。
 *   否则菜单项会不断累积，出现重复的文件夹。
 */
async function removeDynamicMenus() {
  return new Promise((resolve) => {
    chrome.contextMenus.removeAll(() => {
      chrome.contextMenus.create({
        id: PARENT_MENU_ID,
        title: '收藏到 HLAIA 导航栏',
        contexts: ['page', 'link']
      });
      resolve();
    });
  });
}

// ============================================================
// 右键菜单点击事件处理
// ============================================================

/**
 * 当用户点击右键菜单项时触发
 *
 * @param {chrome.contextMenus.OnClickData} info - 点击事件的详细信息
 * @param {chrome.tabs.Tab} tab - 当前标签页的信息
 *
 * info 对象包含：
 *   menuItemId: 被点击的菜单项 ID（用于判断是哪个菜单）
 *   linkUrl: 如果右键点击的是链接，这里是链接的 URL
 *   pageUrl: 当前页面的 URL
 *
 * tab 对象包含：
 *   title: 当前标签页的标题
 *   url: 当前标签页的 URL
 */
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  const { token, serverUrl } = await chrome.storage.local.get(['token', 'serverUrl']);
  const baseUrl = serverUrl || 'https://nav.hlaia.top';

  if (!token) {
    chrome.runtime.openOptionsPage();
    return;
  }

  const url = info.linkUrl || tab.url || info.pageUrl;
  const title = info.linkUrl ? url : (tab.title || info.pageUrl);

  const menuItemId = info.menuItemId;

  try {
    if (menuItemId === STAGING_MENU_ID) {
      await saveToStaging(baseUrl, title, url);
    } else if (typeof menuItemId === 'string' && menuItemId.startsWith(FOLDER_MENU_PREFIX)) {
      const folderId = parseInt(menuItemId.replace(FOLDER_MENU_PREFIX, ''), 10);
      await saveBookmark(baseUrl, folderId, title, url);
    }
  } catch (error) {
    console.error('Error handling menu click:', error);
    showNotification('保存失败', '发生未知错误，请重试');
  }
});

// ============================================================
// API 请求函数
// ============================================================

/**
 * 保存书签到指定文件夹
 *
 * 调用后端接口：POST /api/ext/bookmarks
 * 请求体：{ folderId: number, title: string, url: string }
 *
 * 使用 authFetch 发送请求，Token 过期时会自动刷新并重试。
 *
 * @param {string} baseUrl - API 服务器地址
 * @param {number} folderId - 目标文件夹 ID
 * @param {string} title - 网页标题
 * @param {string} url - 网页地址
 */
async function saveBookmark(baseUrl, folderId, title, url) {
  const response = await authFetch(`${baseUrl}/api/ext/bookmarks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ folderId, title, url })
  });

  if (!response) {
    showNotification('请先登录', '请打开扩展设置页登录');
    chrome.runtime.openOptionsPage();
    return;
  }

  await handleApiResponse(response, '书签已保存', '保存书签失败');
}

/**
 * 保存到暂存区（稍后整理）
 *
 * 调用后端接口：POST /api/ext/staging
 * 请求体：{ title: string, url: string }
 *
 * 使用 authFetch 发送请求，Token 过期时会自动刷新并重试。
 *
 * @param {string} baseUrl - API 服务器地址
 * @param {string} title - 网页标题
 * @param {string} url - 网页地址
 */
async function saveToStaging(baseUrl, title, url) {
  const response = await authFetch(`${baseUrl}/api/ext/staging`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, url })
  });

  if (!response) {
    showNotification('请先登录', '请打开扩展设置页登录');
    chrome.runtime.openOptionsPage();
    return;
  }

  await handleApiResponse(response, '已保存到暂存区', '保存到暂存区失败');
}

/**
 * 统一处理 API 响应
 *
 * 后端统一响应格式：{ code: number, message: string, data: T }
 *   code === 200 表示成功
 *   code !== 200 表示业务错误（如参数校验失败）
 *
 * 注意：401 处理已由 authFetch 自动完成（刷新 Token 并重试）。
 * 如果到这里还是 401，说明 refreshToken 也过期了，需要用户重新登录。
 *
 * @param {Response} response - fetch 返回的 Response 对象
 * @param {string} successMsg - 成功时的通知标题
 * @param {string} failMsg - 失败时的通知标题
 */
async function handleApiResponse(response, successMsg, failMsg) {
  // 0：网络层失败（断网、服务器不可达）。authFetch 把网络异常转成了 status:0 伪响应。
  if (response.status === 0) {
    showNotification(failMsg, '网络连接失败，请检查网络后重试');
    return;
  }

  if (response.status === 401) {
    // authFetch 已经尝试过刷新，到这里说明刷新也失败了
    showNotification('登录已过期', '请打开扩展设置页重新登录');
    chrome.runtime.openOptionsPage();
    return;
  }

  // response.json() 在服务器返回非 JSON（如 502 网关 HTML）时会抛 SyntaxError，
  // 单独保护，避免让上层 onClicked 的 try/catch 把"业务失败"误判为"未知错误"。
  let result;
  try {
    result = await response.json();
  } catch (e) {
    console.warn('Invalid JSON response:', e?.message || e);
    showNotification(failMsg, '服务器响应格式错误');
    return;
  }

  if (response.ok && result.code === 200) {
    showNotification(successMsg, result.message || '操作成功');
  } else {
    showNotification(failMsg, result.message || '请稍后重试');
  }
}

// ============================================================
// 通知工具函数
// ============================================================

/**
 * 显示 Chrome 通知
 *
 * chrome.notifications.create() 会在系统通知栏中显示一个通知气泡。
 * type: 'basic' 表示基本通知（图标 + 标题 + 消息）
 *
 * 注意：需要在 manifest.json 的 permissions 中声明 "notifications" 权限
 *
 * @param {string} title - 通知标题
 * @param {string} message - 通知内容
 */
function showNotification(title, message) {
  chrome.notifications.create({
    type: 'basic',
    iconUrl: 'icons/icon128.png',
    title: title,
    message: message
  });
}

// ============================================================
// 监听来自 options.js 的消息
// ============================================================

/**
 * 当用户在选项页登录/登出时，options.js 会发送消息通知 background.js。
 * background.js 收到消息后刷新文件夹菜单。
 *
 * chrome.runtime.onMessage 监听来自扩展内部其他页面的消息。
 * sendResponse 用于回复消息发送方。
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'LOGIN_SUCCESS') {
    lastFolderRefresh = 0;
    // 异步操作需 catch：Promise 抛错逃逸会变 unhandled rejection，
    // 同样会在 chrome://extensions 留下红色错误标志。
    refreshFolderMenus().catch(e => console.warn('refreshFolderMenus (login):', e?.message || e));
    sendResponse({ success: true });
  } else if (message.type === 'LOGOUT') {
    removeDynamicMenus().catch(e => console.warn('removeDynamicMenus (logout):', e?.message || e));
    sendResponse({ success: true });
  }
  return true;
});
