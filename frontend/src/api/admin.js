/**
 * 管理员相关 API
 *
 * 这些接口需要 ADMIN 角色，普通用户调用会被 Spring Security 拒绝（403）。
 * 后端通过 @PreAuthorize("hasRole('ADMIN')") 注解做权限控制。
 */
import request from './request'

/**
 * 获取用户列表（分页）
 * @param {number} page - 页码，从 1 开始
 * @param {number} size - 每页数量
 * @returns {Promise} - 分页用户列表
 */
export function getUsers(page = 1, size = 20) {
  return request.get('/admin/users', {
    params: { page, size }
  })
}

/**
 * 获取指定用户的文件夹树
 * 管理员可以查看任意用户的导航栏结构
 * @param {number} userId - 用户 ID
 * @returns {Promise} - { code, data: [folder tree nodes] }
 */
export function getUserFolderTree(userId) {
  return request.get(`/admin/users/${userId}/folders/tree`)
}

/**
 * 删除指定文件夹（管理员操作）
 * 管理员可以删除任意用户的文件夹
 * @param {number} folderId - 文件夹 ID
 * @returns {Promise}
 */
export function deleteUserFolder(folderId) {
  return request.delete(`/admin/folders/${folderId}`)
}

/**
 * 封禁用户
 * 被封禁的用户无法登录（后端会检查 user.status）
 * @param {number} userId - 用户 ID
 * @returns {Promise}
 */
export function banUser(userId) {
  return request.put(`/admin/users/${userId}/ban`)
}

/**
 * 解封用户
 * @param {number} userId - 用户 ID
 * @returns {Promise}
 */
export function unbanUser(userId) {
  return request.put(`/admin/users/${userId}/unban`)
}
