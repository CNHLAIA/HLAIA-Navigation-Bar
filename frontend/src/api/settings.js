/**
 * 系统设置相关 API（仅管理员可用，后端 /api/admin/settings 已做 ADMIN 角色校验）
 *
 * 图标抓取代理：影响 FaviconService/IconFetchService 的出站请求，
 * 配置存在后端数据库（system_setting 表），保存即生效、无需重启。
 */
import request from './request'

/**
 * 获取图标抓取代理配置
 * @returns {Promise} - { code, data: { enabled, host, port } }
 */
export function getFaviconProxy() {
  return request.get('/admin/settings/favicon-proxy')
}

/**
 * 保存图标抓取代理配置
 * @param {Object} data - { enabled: boolean, host: string, port: number }
 * @returns {Promise} - { code, data: { enabled, host, port } } 保存后生效的配置
 */
export function updateFaviconProxy(data) {
  return request.put('/admin/settings/favicon-proxy', data)
}

/**
 * 测试代理连通性（用传入的配置测试，不要求先保存）
 * 后端会通过该代理访问 Google generate_204 探测端点
 * @param {Object} data - { enabled, host, port } 同保存接口
 * @returns {Promise} - { code, data: { success, latencyMs, message } }
 */
export function testFaviconProxy(data) {
  return request.post('/admin/settings/favicon-proxy/test', data)
}
