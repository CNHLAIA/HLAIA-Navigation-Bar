/**
 * 用户个人信息相关 API
 *
 * 封装了获取个人信息、更新个人信息、修改密码三个接口。
 * 这些接口都走 /api/user 路径前缀，需要 JWT 认证。
 */
import request from './request'

/**
 * 获取当前登录用户的个人信息
 * @returns {Promise} - { code, data: { id, username, nickname, email, role, status, createdAt } }
 */
export function getProfile() {
  return request.get('/user/profile')
}

/**
 * 更新用户个人信息（昵称和邮箱）
 * @param {Object} data - { nickname, email }
 * @returns {Promise} - { code, data: { ...updated UserResponse } }
 */
export function updateProfile(data) {
  return request.put('/user/profile', data)
}

/**
 * 修改密码
 * 后端会验证 oldPassword 是否正确，以及 newPassword 是否符合要求
 * @param {Object} data - { oldPassword, newPassword }
 * @returns {Promise} - { code, data: null }
 */
export function changePassword(data) {
  return request.put('/user/password', data)
}
