/**
 * 搜索相关 API
 */
import request from './request'

/**
 * 全文搜索书签和文件夹
 * @param {string} keyword - 搜索关键词
 * @param {number} page - 页码（从 1 开始）
 * @param {number} size - 每页大小
 */
export function searchAll(keyword, page = 1, size = 20) {
  return request.get('/search', { params: { keyword, page, size } })
}

/**
 * 搜索建议（Autocomplete）
 * @param {string} keyword - 用户正在输入的关键词
 */
export function searchSuggest(keyword) {
  return request.get('/search/suggest', { params: { keyword } })
}
