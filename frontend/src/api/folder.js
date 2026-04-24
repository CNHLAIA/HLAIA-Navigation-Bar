/**
 * 文件夹相关 API
 *
 * 提供文件夹的 CRUD、排序、移动操作。
 * 文件夹采用邻接表模型（parent_id），通过 getFolderTree 获取完整的树形结构。
 */
import request from './request'

/**
 * 获取当前用户的文件夹树
 * 后端会递归构建树形结构，返回嵌套的节点数组
 * @returns {Promise} - { code, data: [folder tree nodes] }
 */
export function getFolderTree() {
  return request.get('/folders/tree')
}

/**
 * 创建文件夹
 * @param {Object} data - { name, parentId }
 * @returns {Promise} - { code, data: folder }
 */
export function createFolder(data) {
  return request.post('/folders', data)
}

/**
 * 更新文件夹（重命名）
 * @param {number} id - 文件夹 ID
 * @param {Object} data - { name }
 * @returns {Promise}
 */
export function updateFolder(id, data) {
  return request.put(`/folders/${id}`, data)
}

/**
 * 删除文件夹
 * 注意：会级联删除子文件夹和文件夹下的所有书签
 * @param {number} id - 文件夹 ID
 * @returns {Promise}
 */
export function deleteFolder(id) {
  return request.delete(`/folders/${id}`)
}

/**
 * 批量更新文件夹排序
 * 用于拖拽排序后，将新的顺序提交给后端
 * @param {Array} data - [{ id, sortOrder }, ...]
 * @returns {Promise}
 */
export function sortFolders(data) {
  return request.put('/folders/sort', { items: data })
}

/**
 * 移动文件夹到新的父级
 * @param {number} id - 要移动的文件夹 ID
 * @param {Object} data - { parentId }
 * @returns {Promise}
 */
export function moveFolder(id, data) {
  return request.put(`/folders/${id}/move`, data)
}
