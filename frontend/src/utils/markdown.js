/**
 * 共享 Markdown 渲染工具
 *
 * 职责：
 * - 提供全项目唯一的 markdown-it 实例（悬浮备注弹窗 + 备注编辑预览共用，
 *   单例避免每处渲染重复构建解析器）
 * - 渲染书签备注（bookmark.description）为 HTML 片段
 *
 * 安全设计：
 * - html: false —— 渲染前转义备注中的原始 HTML 片段，防脚本注入（XSS 防线内建，
 *   无需额外引入 DOMPurify）
 * - linkify: true —— 裸 URL（如 www.example.com）自动转成可点击链接
 * - breaks: true —— 单个换行渲染为 <br>，贴合备注这类短文本的书写习惯
 */

import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

// linkify-it v6（markdown-it 15 依赖）把 fuzzyLink 默认值改为 false，
// "www.example.com" 这类无协议裸域名不再自动链接。这里显式开启，
// 恢复备注中裸域名可点击的行为（markdown-it 会自动补 http:// 前缀，安全）
md.linkify.set({ fuzzyLink: true })

// 保留默认的 link_open 渲染规则：先补属性再委托原实现，行为对齐官方文档推荐写法
const defaultLinkOpen =
  md.renderer.rules.link_open ||
  function (tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options)
  }

/**
 * 覆盖链接渲染：备注中的链接按项目外链约定统一新标签页打开
 * target="_blank" 新窗口；rel="noopener noreferrer" 防止新页面反向操控
 * 本页（window.opener）且不泄露 Referrer
 */
md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/**
 * 渲染 Markdown 文本为 HTML 片段
 * 供 v-html 使用（html:false 已保证输出安全）
 * @param {string} text - Markdown 原文，null/undefined/空串均返回 ''
 * @returns {string} HTML 片段
 */
export function renderMarkdown(text) {
  return text ? md.render(text) : ''
}
