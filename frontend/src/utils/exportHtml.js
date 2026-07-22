/**
 * 把导出数据渲染成自包含可视化 HTML 字符串
 *
 * 设计取向（见 .trellis/tasks/07-22-export-visual-html/design.md §4）：
 *   - 安静、克制的「索引式」版面，单色系（项目主色 #4A7FC7），拒绝彩色拼盘。
 *   - 不用 emoji；文件夹层级靠缩进 + 引导线 + 目录(TOC) 表达。
 *   - 纯函数，无 Vue 依赖：预览页与真实下载共用同一套模板。
 *   - 单文件可用：CSS 内联 <style>，JS 内联 <script>，图标用 base64 data URI。
 *
 * 安全：所有动态文本走 escapeHtml；URL 只允许 http/https 链接化，其余降级为纯文本。
 */

const C_PRIMARY = '#4A7FC7'

/** HTML 文本转义，防注入与结构破坏 */
function escapeHtml(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 安全链接化：只允许 http(s) 的 URL 渲染成可点击 <a>，其余降级为纯文本 */
function safeLink(url) {
  const u = url == null ? '' : String(url).trim()
  const safe = /^https?:\/\//i.test(u)
  return { href: safe ? u : '#', safe }
}

/** 从完整 URL 提取可读的展示文本（去协议、保留主域+一级路径） */
function prettyUrl(url) {
  const u = url == null ? '' : String(url).trim()
  const m = u.match(/^https?:\/\/([^/]+)(\/[^?#]*)?/i)
  if (!m) return u
  return m[2] && m[2] !== '/' ? m[1] + m[2] : m[1]
}

// 为每个节点生成稳定的锚点 id（用于 TOC 跳转）
let _idSeq = 0
function nextId() { return 'f' + (++_idSeq) }

/**
 * 收集所有文件夹节点，生成带层级的目录（TOC）项
 * @returns {Array<{id,name,depth}>}
 */
function collectToc(nodes, depth = 1, acc = []) {
  for (const n of nodes) {
    acc.push({ id: n.__id, name: n.name, depth })
    if (n.children && n.children.length) collectToc(n.children, depth + 1, acc)
  }
  return acc
}

/** 渲染目录(TOC) —— 一眼看清文件夹树形结构 */
function renderToc(tocItems) {
  if (!tocItems.length) return ''
  const items = tocItems.map((it) => {
    const indent = 'margin-left:' + ((it.depth - 1) * 16) + 'px'
    const cls = it.depth === 1 ? 'toc-item toc-item--root' : 'toc-item'
    return `<a class="${cls}" style="${indent}" href="#${it.id}">${escapeHtml(it.name)}</a>`
  }).join('')
  return `<nav class="toc"><div class="toc-label">目录</div>${items}</nav>`
}

/**
 * 递归渲染一个文件夹节点为 HTML 片段
 * @param {Object} node - ExportFolderNode（含预生成的 __id）
 * @param {number} depth - 层级（根=1）
 */
function renderFolder(node, depth) {
  const id = node.__id
  const name = escapeHtml(node.name || '(未命名)')
  const titleTag = depth === 1 ? 'h2' : depth === 2 ? 'h3' : 'h4'
  const count = (node.bookmarks || []).length

  const cards = (node.bookmarks || []).map((bm) => renderCard(bm)).join('')
  const childSections = (node.children || [])
    .map((child) => renderFolder(child, depth + 1))
    .join('')
  const isEmpty = !cards && !childSections

  // 层级表达：depth=1 是区块(白卡)；depth>=2 用左缩进 + 引导线嵌套
  const wrapClass = depth === 1 ? 'folder folder--root' : 'folder folder--sub'
  const countBadge = count ? `<span class="folder-count">${count}</span>` : ''

  return `
    <section class="${wrapClass}" data-depth="${depth}" id="${id}">
      <${titleTag} class="folder-title">
        <span class="folder-mark" aria-hidden="true"></span>
        <span class="folder-name">${name}</span>
        ${countBadge}
      </${titleTag}>
      ${cards ? `<div class="card-grid">${cards}</div>` : ''}
      ${childSections ? `<div class="sub-folders">${childSections}</div>` : ''}
      ${isEmpty ? `<p class="folder-empty">暂无书签</p>` : ''}
    </section>`
}

/** 渲染单个书签卡片 */
function renderCard(bm) {
  const title = escapeHtml(bm.title || '(无标题)')
  const { href, safe } = safeLink(bm.url)
  const urlText = escapeHtml(prettyUrl(bm.url))
  const desc = bm.description ? escapeHtml(bm.description) : ''
  const hasIcon = bm.iconUrl && String(bm.iconUrl).trim()
  const fallbackLetter = escapeHtml((bm.title || '?').trim().charAt(0).toUpperCase() || '?')

  // 图标加载失败时降级为首字母占位。用 data-fallback 属性携带占位字符，
  // 由全局 img error 监听处理（见下方脚本），避免把动态值内联进 onerror JS 字符串。
  const iconHtml = hasIcon
    ? `<img class="favicon" src="${escapeHtml(bm.iconUrl)}" alt="" data-fallback="${fallbackLetter}">`
    : `<span class="favicon favicon--fallback">${fallbackLetter}</span>`

  const tag = safe ? 'a' : 'div'
  const linkAttrs = safe
    ? ` href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer"`
    : ''
  const dataAttrs = `data-title="${escapeHtml((bm.title || '').toLowerCase())}" data-url="${escapeHtml((bm.url || '').toLowerCase())}"`

  return `<${tag} class="card"${linkAttrs} ${dataAttrs}>
    ${iconHtml}
    <div class="card-body">
      <span class="card-title">${title}</span>
      <span class="card-url">${urlText}</span>
      ${desc ? `<span class="card-desc">${desc}</span>` : ''}
    </div>
  </${tag}>`
}

/** 预处理：给每个文件夹节点打上 __id，供 TOC 锚点使用 */
function stampIds(nodes) {
  for (const n of nodes) {
    n.__id = nextId()
    if (n.children && n.children.length) stampIds(n.children)
  }
}

/**
 * 主入口：把 ExportDataResponse 渲染成完整 HTML 文档
 * @param {Object} data - { exportedAt, folders: ExportFolderNode[] }
 * @returns {string} 完整 HTML 文档
 */
export function renderExportHtml(data) {
  _idSeq = 0
  const folders = (data && data.folders) || []
  stampIds(folders)
  const exportedAt = escapeHtml(data && data.exportedAt) || ''

  let folderCount = 0
  let bookmarkCount = 0
  const count = (nodes) => {
    for (const n of nodes) {
      folderCount++
      bookmarkCount += (n.bookmarks || []).length
      if (n.children && n.children.length) count(n.children)
    }
  }
  count(folders)

  const tocItems = collectToc(folders)
  const toc = renderToc(tocItems)
  const body = folders.map((f) => renderFolder(f, 1)).join('')
  const emptyState = !folders.length
    ? `<div class="page-empty"><p>还没有任何书签</p></div>`
    : ''

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>我的书签</title>
<style>
  :root {
    --primary: ${C_PRIMARY};
    --primary-soft: rgba(74,127,199,.10);
    --bg: #F6F7F9;
    --surface: #FFFFFF;
    --line: #E6E9EE;
    --line-soft: #EEF1F5;
    --title: #1F2A37;
    --text: #4B5563;
    --muted: #94A0B0;
    --radius-card: 8px;
    --radius-section: 10px;
    --shadow: 0 1px 2px rgba(31,42,55,.05);
  }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
  }

  /* ===== 顶部 ===== */
  .page-header {
    position: sticky; top: 0; z-index: 20;
    background: rgba(246,247,249,.9);
    backdrop-filter: saturate(180%) blur(10px);
    -webkit-backdrop-filter: saturate(180%) blur(10px);
    border-bottom: 1px solid var(--line);
  }
  .header-inner {
    max-width: 100%; margin: 0 auto;
    padding: 18px 24px;
    display: flex; align-items: center; justify-content: space-between; gap: 20px;
  }
  .page-title { margin: 0; font-size: 22px; font-weight: 800; color: var(--title); letter-spacing: -.01em; }
  .page-meta { margin: 4px 0 0; font-size: 13px; color: var(--muted); }
  .page-meta strong { color: var(--title); font-weight: 600; }
  .search-wrap { position: relative; flex: 0 1 320px; width: 320px; }
  .search-wrap input {
    width: 100%; padding: 9px 14px 9px 36px;
    font-size: 14px; color: var(--text);
    background: var(--surface); border: 1px solid var(--line); border-radius: 999px;
    outline: none; transition: border-color .15s, box-shadow .15s;
  }
  .search-wrap input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-soft); }
  .search-wrap input::placeholder { color: var(--muted); }
  .search-icon { position: absolute; left: 13px; top: 50%; transform: translateY(-50%); width: 14px; height: 14px; opacity: .55; }

  /* ===== 布局：目录 + 正文 ===== */
  .layout { width: 100%; max-width: 100%; margin: 0; padding: 20px 24px; display: grid; grid-template-columns: 190px 1fr; gap: 28px; align-items: start; }
  .toc { position: sticky; top: 88px; font-size: 13px; }
  .toc-label { font-size: 11px; font-weight: 700; letter-spacing: .08em; color: var(--muted); text-transform: uppercase; margin-bottom: 10px; padding-left: 8px; }
  .toc-item { display: block; padding: 5px 8px; border-radius: 5px; color: var(--text); text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; transition: background .12s, color .12s; }
  .toc-item:hover { background: var(--primary-soft); color: var(--primary); }
  .toc-item--root { font-weight: 600; color: var(--title); }
  .content { min-width: 0; }

  /* ===== 文件夹区块 ===== */
  .folder { margin-bottom: 30px; }
  .folder--root { scroll-margin-top: 80px; }
  .folder-title {
    display: flex; align-items: center; gap: 9px;
    margin: 0 0 14px; color: var(--title); line-height: 1.3;
  }
  h2.folder-title { font-size: 17px; font-weight: 700; padding-bottom: 10px; border-bottom: 2px solid var(--line); }
  h3.folder-title, h4.folder-title { font-size: 14px; font-weight: 600; }
  /* 根用短实线、子用圆点 —— 层级一眼可辨 */
  .folder-mark { flex: 0 0 auto; width: 3px; height: 16px; background: var(--primary); border-radius: 2px; }
  .folder--sub .folder-mark { width: 6px; height: 6px; border-radius: 50%; background: var(--muted); }
  .folder-name { flex: 1 1 auto; }
  .folder-count { flex: 0 0 auto; font-size: 11px; font-weight: 600; color: var(--muted); background: var(--line-soft); padding: 1px 7px; border-radius: 999px; }

  /* 子文件夹嵌套：缩进 + 引导线 */
  .sub-folders { margin-top: 16px; padding-left: 20px; border-left: 1px solid var(--line-soft); }
  .sub-folders .folder { margin-bottom: 22px; }
  .folder-empty { color: var(--muted); font-size: 13px; margin: 4px 0 0; padding-left: 15px; }

  /* ===== 卡片 ===== */
  .card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
  .card {
    display: flex; align-items: center; gap: 9px; min-width: 0;
    padding: 10px 12px; background: var(--surface);
    border: 1px solid var(--line); border-radius: var(--radius-card);
    text-decoration: none; color: var(--text); box-shadow: var(--shadow);
    transition: border-color .14s, box-shadow .14s, transform .14s;
  }
  a.card:hover { border-color: var(--primary); box-shadow: 0 4px 14px rgba(74,127,199,.14); transform: translateY(-1px); }
  a.card:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
  .card--nolink { opacity: .7; }
  .favicon { flex: 0 0 auto; width: 26px; height: 26px; border-radius: 6px; object-fit: contain; background: var(--line-soft); }
  .favicon--fallback { display: inline-flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; color: #fff; background: var(--primary); }
  .card-body { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
  .card-title { font-size: 13.5px; font-weight: 600; color: var(--title); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .card-url { font-size: 11.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .card-desc { font-size: 11.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

  .page-empty, .search-empty { text-align: center; color: var(--muted); padding: 64px 20px; font-size: 14px; }
  .page-footer { padding: 0 24px 40px; text-align: center; font-size: 12px; color: var(--muted); }
  .folder.is-hidden, .card.is-hidden { display: none; }

  /* ===== 响应式：窄屏目录折叠到顶部横排 ===== */
  @media (max-width: 860px) {
    .layout { grid-template-columns: 1fr; gap: 0; }
    .toc { position: static; margin-bottom: 18px; padding: 12px 14px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-section); }
    .toc-item { display: inline-block; margin: 0 6px 4px 0; }
  }
  @media (max-width: 640px) {
    .header-inner { flex-wrap: wrap; }
    .search-wrap { flex: 1 1 100%; width: auto; }
    .card-grid { grid-template-columns: 1fr; }
  }
  @media (prefers-reduced-motion: reduce) { .card { transition: none; } a.card:hover { transform: none; } }
</style>
</head>
<body>
  <header class="page-header">
    <div class="header-inner">
      <div>
        <h1 class="page-title">我的书签</h1>
        <p class="page-meta"><strong>${folderCount}</strong> 个文件夹 · <strong>${bookmarkCount}</strong> 个书签${exportedAt ? ' · 导出于 ' + exportedAt : ''}</p>
      </div>
      <div class="search-wrap">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input id="search" type="search" placeholder="搜索书签标题或网址…" autocomplete="off" aria-label="搜索书签">
      </div>
    </div>
  </header>
  <div class="layout">
    ${toc}
    <main class="content">
      ${emptyState}
      ${body}
    </main>
  </div>
  <footer class="page-footer">由 NavigationBar 导出</footer>
  <div id="search-empty" class="search-empty" hidden>没有匹配「<span id="search-term"></span>」的书签</div>

<script>
// 图标加载失败 → 首字母占位（事件委托，替代内联 onerror，避免动态值进 JS 上下文）
document.addEventListener('error', function (e) {
  var img = e.target;
  if (img && img.tagName === 'IMG' && img.classList.contains('favicon') && img.dataset.fallback != null) {
    var span = document.createElement('span');
    span.className = 'favicon favicon--fallback';
    span.textContent = img.dataset.fallback;
    img.replaceWith(span);
  }
}, true);

(function () {
  var input = document.getElementById('search');
  var empty = document.getElementById('search-empty');
  var emptyTerm = document.getElementById('search-term');
  var cards = Array.prototype.slice.call(document.querySelectorAll('.card'));
  var folders = Array.prototype.slice.call(document.querySelectorAll('.folder'));

  input.addEventListener('input', function () {
    var q = input.value.trim().toLowerCase();
    var anyVisible = false;
    cards.forEach(function (card) {
      var t = card.getAttribute('data-title') || '';
      var u = card.getAttribute('data-url') || '';
      var match = !q || t.indexOf(q) > -1 || u.indexOf(q) > -1;
      card.classList.toggle('is-hidden', !match);
      if (match) anyVisible = true;
    });
    // 隐藏没有可见卡片的文件夹区块（子区块先判，父区块据此再判 —— 倒序保证父能感知子）
    folders.slice().reverse().forEach(function (f) {
      var ownVisible = Array.prototype.some.call(f.querySelectorAll(':scope > .card-grid .card'), function (c) { return !c.classList.contains('is-hidden'); });
      var childVisible = Array.prototype.some.call(f.querySelectorAll(':scope > .sub-folders .folder'), function (c) { return !c.classList.contains('is-hidden'); });
      f.classList.toggle('is-hidden', !(ownVisible || childVisible));
    });
    empty.hidden = anyVisible;
    if (!anyVisible) emptyTerm.textContent = input.value;
  });
  input.addEventListener('keydown', function (e) { if (e.key === 'Escape') { input.value = ''; input.dispatchEvent(new Event('input')); } input.focus(); });
})();
</script>
</body>
</html>`
}
