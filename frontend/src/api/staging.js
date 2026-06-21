/**
 * 暂存区相关 API
 *
 * 暂存区是一个临时存放网页链接的区域，类似"稍后阅读"功能。
 * 暂存项有过期时间，到期后会被系统自动清理。
 */
import request from './request'

/**
 * 获取当前用户的暂存区列表
 * @returns {Promise} - { code, data: [staging items] }
 */
export function getStagingItems() {
  return request.get('/staging')
}

/**
 * 添加网页到暂存区
 * @param {Object} data - { title, url, icon, expireMinutes }
 * @returns {Promise}
 */
export function addStagingItem(data) {
  return request.post('/staging', data)
}

/**
 * 修改暂存项的过期时间
 * @param {number} id - 暂存项 ID
 * @param {number} expireMinutes - 新的过期时间（分钟）
 * @returns {Promise}
 */
export function updateStagingExpiry(id, expireMinutes) {
  return request.put(`/staging/${id}`, { expireMinutes })
}

/**
 * 删除暂存项
 * @param {number} id - 暂存项 ID
 * @returns {Promise}
 */
export function deleteStagingItem(id) {
  return request.delete(`/staging/${id}`)
}

/**
 * 将暂存项移动到文件夹（变成正式书签）
 * @param {number} id - 暂存项 ID
 * @param {number} folderId - 目标文件夹 ID
 * @returns {Promise}
 */
export function moveToFolder(id, folderId) {
  return request.post(`/staging/${id}/move-to-folder`, { folderId })
}
