/**
 * 书签相关 API
 *
 * 提供书签的 CRUD、排序、批量删除、批量复制链接操作。
 * 书签属于某个文件夹，通过 folderId 关联。
 */
import request from './request'

/**
 * 获取指定文件夹下的书签列表
 * @param {number} folderId - 文件夹 ID
 * @returns {Promise} - { code, data: [bookmarks] }
 */
export function getBookmarks(folderId) {
  return request.get(`/folders/${folderId}/bookmarks`)
}

/**
 * 创建书签
 * @param {Object} data - { title, url, folderId, icon }
 * @returns {Promise}
 */
export function createBookmark(data) {
  return request.post('/bookmarks', data)
}

/**
 * 更新书签
 * @param {number} id - 书签 ID
 * @param {Object} data - { title, url, folderId, icon }
 * @returns {Promise}
 */
export function updateBookmark(id, data) {
  return request.put(`/bookmarks/${id}`, data)
}

/**
 * 删除书签
 * @param {number} id - 书签 ID
 * @returns {Promise}
 */
export function deleteBookmark(id) {
  return request.delete(`/bookmarks/${id}`)
}

/**
 * 批量更新书签排序
 * @param {Array} data - [{ id, sortOrder }, ...]
 * @returns {Promise}
 */
export function sortBookmarks(data) {
  return request.put('/bookmarks/sort', { items: data })
}

/**
 * 批量删除书签
 * @param {Array} ids - 书签 ID 数组
 * @returns {Promise}
 */
export function batchDeleteBookmarks(ids) {
  return request.post('/bookmarks/batch-delete', { ids })
}

/**
 * 批量复制书签链接
 * 将多个书签的标题和 URL 格式化为文本，方便粘贴分享
 * @param {Array} ids - 书签 ID 数组
 * @returns {Promise} - { code, data: "copied text" }
 */
export function batchCopyLinks(ids) {
  return request.post('/bookmarks/batch-copy', { ids })
}

/**
 * 批量移动书签到目标文件夹
 * @param {Array} bookmarkIds - 书签 ID 数组
 * @param {number} targetFolderId - 目标文件夹 ID
 * @returns {Promise}
 */
export function moveBookmarks(bookmarkIds, targetFolderId) {
  return request.put('/bookmarks/move', { bookmarkIds, targetFolderId })
}

/**
 * 导入浏览器书签
 * 上传 Chrome 导出的 Netscape Bookmark HTML 文件，后端解析并批量创建文件夹和书签
 * @param {FormData} formData - 包含 file、targetFolderId、duplicateMode 的表单数据
 * @returns {Promise} - { code, data: { foldersCreated, bookmarksCreated, bookmarksUpdated, bookmarksSkipped } }
 */
export function importBookmarks(formData) {
  return request.post('/bookmarks/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * 导出当前用户全部书签为 Netscape Bookmark HTML 文件
 *
 * 为什么用 responseType: 'blob'？
 *   后端返回的是 HTML 文件二进制流（带 Content-Disposition: attachment），
 *   不是统一的 { code, message, data } JSON。Axios 用 blob 接收，
 *   且 request.js 响应拦截器对 blob 请求直接透传完整 response（不做业务码解包），
 *   所以本函数返回的是完整 response 对象，调用方通过 response.data 拿到 Blob，
 *   通过 response.headers['content-disposition'] 拿到文件名。
 *
 * @returns {Promise} - 完整 response 对象，response.data 为 Blob
 */
export function exportBookmarks() {
  return request.get('/bookmarks/export', {
    responseType: 'blob',
    timeout: 60000
  })
}
