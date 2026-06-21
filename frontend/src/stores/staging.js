/**
 * Pinia Staging Store — 暂存区状态管理
 *
 * 职责：
 * - 管理暂存区条目列表（items）
 * - 提供暂存区 CRUD、设置过期时间、移动到文件夹的 actions
 * - 暂存区类似"稍后阅读"，条目有过期时间，到期后后端自动清理
 *
 * 设计模式：Setup Store（Composition API 风格）
 * 与 bookmark.js / folder.js 保持一致的状态管理模式
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getStagingItems as getStagingItemsApi,
  addStagingItem as addStagingItemApi,
  updateStagingExpiry as updateStagingExpiryApi,
  deleteStagingItem as deleteStagingItemApi,
  moveToFolder as moveToFolderApi
} from '@/api/staging'

export const useStagingStore = defineStore('staging', () => {
  // ---- State ----

  /** 暂存区条目列表 */
  const items = ref([])

  /** 是否正在加载 */
  const loading = ref(false)

  // ---- Actions ----

  /**
   * 获取暂存区条目列表
   * 后端会自动过滤已过期的条目，前端无需额外筛选
   */
  async function fetchItems() {
    loading.value = true
    try {
      const res = await getStagingItemsApi()
      items.value = res.data || []
    } finally {
      loading.value = false
    }
  }

  /**
   * 添加条目到暂存区
   * @param {Object} data - { title, url, icon, expireMinutes }
   */
  async function addItem(data) {
    const res = await addStagingItemApi(data)
    // 添加成功后刷新列表
    await fetchItems()
    return res.data
  }

  /**
   * 更新暂存条目的过期时间
   * @param {number} id - 暂存条目 ID
   * @param {number} expireMinutes - 新的过期时间（分钟）
   */
  async function updateExpiry(id, expireMinutes) {
    const res = await updateStagingExpiryApi(id, expireMinutes)
    await fetchItems()
    return res
  }

  /**
   * 删除暂存条目
   * @param {number} id - 暂存条目 ID
   */
  async function deleteItem(id) {
    const res = await deleteStagingItemApi(id)
    await fetchItems()
    return res
  }

  /**
   * 将暂存条目移动到文件夹（变成正式书签）
   * 移动后条目从暂存区消失
   * @param {number} id - 暂存条目 ID
   * @param {number} folderId - 目标文件夹 ID
   */
  async function moveToFolder(id, folderId) {
    const res = await moveToFolderApi(id, folderId)
    await fetchItems()
    return res
  }

  return {
    // state
    items,
    loading,
    // actions
    fetchItems,
    addItem,
    updateExpiry,
    deleteItem,
    moveToFolder
  }
})
