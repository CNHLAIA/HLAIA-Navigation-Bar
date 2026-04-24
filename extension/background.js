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

/** 文件夹菜单的刷新间隔（毫秒），避免频繁请求后端 */
const FOLDER_REFRESH_INTERVAL = 5 * 60 * 1000; // 5 分钟

/** 上次刷新文件夹菜单的时间戳 */
let lastFolderRefresh = 0;

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
  // 创建顶级上下文菜单项
  // title: 菜单中显示的文字
  // id: 菜单项的唯一标识符，后续通过这个 ID 判断用户点击了哪个菜单
  // contexts: ['page'] 表示只在页面空白处右键时出现
  //           ['link'] 表示在链接上右键时也出现，方便直接收藏链接
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
 *   节流机制：距离上次刷新不到 5 分钟则跳过。
 */
chrome.tabs.onActivated.addListener(async (activeInfo) => {
  await refreshFolderMenus();
});

/**
 * 刷新文件夹子菜单的核心逻辑
 *
 * 流程：
 *   1. 检查是否在节流时间窗口内（5 分钟内不重复刷新）
 *   2. 从 chrome.storage.local 获取 JWT Token 和服务器地址
 *   3. 如果没有 Token，移除所有子菜单（用户需要先登录）
 *   4. 有 Token 则调用 GET /api/ext/folders/tree 获取文件夹树
 *   5. 移除旧的子菜单，根据文件夹树创建新的子菜单
 */
async function refreshFolderMenus() {
  // 节流：距离上次刷新不足 5 分钟，跳过
  const now = Date.now();
  if (now - lastFolderRefresh < FOLDER_REFRESH_INTERVAL) {
    return;
  }

  // 从 chrome.storage.local 获取存储的认证信息和服务器配置
  const { token, serverUrl } = await chrome.storage.local.get(['token', 'serverUrl']);
  const baseUrl = serverUrl || 'http://localhost:8080';

  // 没有 Token：移除所有子菜单，用户需要到选项页登录
  if (!token) {
    await removeDynamicMenus();
    return;
  }

  try {
    // 调用后端接口获取文件夹树
    // headers 中的 Authorization: Bearer {token} 是 JWT 的标准认证方式
    const response = await fetch(`${baseUrl}/api/ext/folders/tree`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    // 处理 401 未授权：Token 已过期或无效
    if (response.status === 401) {
      // 清除无效的 Token，引导用户重新登录
      await chrome.storage.local.remove('token');
      await removeDynamicMenus();
      showNotification('登录已过期', '请打开扩展设置页重新登录');
      return;
    }

    if (!response.ok) {
      console.error('Failed to fetch folders:', response.status);
      return;
    }

    const result = await response.json();
    // 后端返回格式：{ code: 200, message: "success", data: [...] }
    const folders = result.data || [];

    // 先移除旧的动态菜单项
    await removeDynamicMenus();

    // 创建"暂存区"菜单项（在顶级菜单下）
    chrome.contextMenus.create({
      id: STAGING_MENU_ID,
      parentId: PARENT_MENU_ID,
      title: '保存到暂存区（稍后整理）',
      contexts: ['page', 'link']
    });

    // 分隔线（视觉上区分"暂存区"和"文件夹"）
    chrome.contextMenus.create({
      id: 'hlaia-separator',
      parentId: PARENT_MENU_ID,
      type: 'separator',
      contexts: ['page', 'link']
    });

    // 递归创建文件夹菜单项
    // 文件夹树是嵌套结构：FolderTreeResponse.children 包含子文件夹
    createFolderMenus(folders, PARENT_MENU_ID);

    // 记录刷新时间
    lastFolderRefresh = now;

  } catch (error) {
    console.error('Error refreshing folder menus:', error);
  }
}

/**
 * 递归创建文件夹的上下文菜单项
 *
 * @param {Array} folders - 文件夹列表（FolderTreeResponse 数组）
 * @param {string} parentId - 父菜单项的 ID（用于创建嵌套子菜单）
 *
 * 文件夹树是递归结构：
 *   工作资料 (id=1)
 *   ├── 前端 (id=2)
 *   │   └── Vue (id=4)
 *   └── 后端 (id=3)
 *
 * 对应的菜单结构：
 *   收藏到 HLAIA 导航栏
 *   ├── 保存到暂存区
 *   ├── ──────────
 *   ├── 工作资料
 *   │   ├── 前端
 *   │   │   └── Vue
 *   │   └── 后端
 */
function createFolderMenus(folders, parentId) {
  if (!folders || folders.length === 0) return;

  for (const folder of folders) {
    const menuId = `${FOLDER_MENU_PREFIX}${folder.id}`;

    chrome.contextMenus.create({
      id: menuId,
      parentId: parentId,
      title: folder.name || '未命名文件夹',
      contexts: ['page', 'link']
    });

    // 递归创建子文件夹的菜单项
    if (folder.children && folder.children.length > 0) {
      createFolderMenus(folder.children, menuId);
    }
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
      // removeAll 会移除所有菜单（包括顶级菜单），需要重新创建
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
  // 获取存储的认证信息
  const { token, serverUrl } = await chrome.storage.local.get(['token', 'serverUrl']);
  const baseUrl = serverUrl || 'http://localhost:8080';

  // 没有 Token：打开选项页引导用户登录
  if (!token) {
    chrome.runtime.openOptionsPage();
    return;
  }

  // 确定要保存的 URL 和标题
  // 如果用户右键点击的是链接（info.linkUrl 存在），则收藏该链接
  // 如果用户右键点击的是页面空白处，则收藏当前页面
  const url = info.linkUrl || tab.url || info.pageUrl;
  const title = info.linkUrl ? url : (tab.title || info.pageUrl);

  const menuItemId = info.menuItemId;

  try {
    if (menuItemId === STAGING_MENU_ID) {
      // 用户点击了"保存到暂存区"
      await saveToStaging(baseUrl, token, title, url);
    } else if (typeof menuItemId === 'string' && menuItemId.startsWith(FOLDER_MENU_PREFIX)) {
      // 用户点击了某个文件夹菜单项
      // 从菜单 ID 中提取 folderId（如 "hlaia-folder-5" → 5）
      const folderId = parseInt(menuItemId.replace(FOLDER_MENU_PREFIX, ''), 10);
      await saveBookmark(baseUrl, token, folderId, title, url);
    }
    // 其他情况（如点击顶级菜单或分隔线），不做处理
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
 * @param {string} baseUrl - API 服务器地址
 * @param {string} token - JWT Token
 * @param {number} folderId - 目标文件夹 ID
 * @param {string} title - 网页标题
 * @param {string} url - 网页地址
 */
async function saveBookmark(baseUrl, token, folderId, title, url) {
  const response = await fetch(`${baseUrl}/api/ext/bookmarks`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ folderId, title, url })
  });

  await handleApiResponse(response, '书签已保存', '保存书签失败');
}

/**
 * 保存到暂存区（稍后整理）
 *
 * 调用后端接口：POST /api/ext/staging
 * 请求体：{ title: string, url: string }
 * expireMinutes 不传，使用服务端默认值
 *
 * @param {string} baseUrl - API 服务器地址
 * @param {string} token - JWT Token
 * @param {string} title - 网页标题
 * @param {string} url - 网页地址
 */
async function saveToStaging(baseUrl, token, title, url) {
  const response = await fetch(`${baseUrl}/api/ext/staging`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ title, url })
  });

  await handleApiResponse(response, '已保存到暂存区', '保存到暂存区失败');
}

/**
 * 统一处理 API 响应
 *
 * 后端统一响应格式：{ code: number, message: string, data: T }
 *   code === 200 表示成功
 *   code !== 200 表示业务错误（如参数校验失败）
 *
 * HTTP 状态码 401 表示 Token 过期或无效，需要引导用户重新登录。
 *
 * @param {Response} response - fetch 返回的 Response 对象
 * @param {string} successMsg - 成功时的通知标题
 * @param {string} failMsg - 失败时的通知标题
 */
async function handleApiResponse(response, successMsg, failMsg) {
  // Token 过期或无效
  if (response.status === 401) {
    await chrome.storage.local.remove('token');
    showNotification('登录已过期', '请打开扩展设置页重新登录');
    chrome.runtime.openOptionsPage();
    return;
  }

  const result = await response.json();

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
    // 用户登录成功，立即刷新文件夹菜单
    lastFolderRefresh = 0; // 重置节流计时器，强制立即刷新
    refreshFolderMenus();
    sendResponse({ success: true });
  } else if (message.type === 'LOGOUT') {
    // 用户登出，移除所有子菜单
    removeDynamicMenus();
    sendResponse({ success: true });
  }
  // 返回 true 表示 sendResponse 会被异步调用
  return true;
});
