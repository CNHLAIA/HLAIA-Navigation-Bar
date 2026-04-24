/**
 * Pinia Bookmark Store — 书签状态管理
 *
 * 职责：
 * - 管理当前文件夹下的书签列表（bookmarks）
 * - 管理多选状态（selectedIds），支持批量操作
 * - 提供书签 CRUD、排序、批量删除、批量复制的 actions
 *
 * 注意：书签数据是"按文件夹加载"的，切换文件夹时需要重新 fetchBookmarks
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getBookmarks as getBookmarksApi,
  createBookmark as createBookmarkApi,
  updateBookmark as updateBookmarkApi,
  deleteBookmark as deleteBookmarkApi,
  sortBookmarks as sortBookmarksApi,
  batchDeleteBookmarks as batchDeleteApi,
  batchCopyLinks as batchCopyApi,
  moveBookmarks as moveBookmarksApi
} from '@/api/bookmark'

import { useFolderStore } from './folder'

export const useBookmarkStore = defineStore('bookmark', () => {
  // ---- State ----

  /** 当前文件夹下的书签列表 */
  const bookmarks = ref([])

  /** 当前加载书签对应的文件夹 ID */
  const currentFolderId = ref(null)

  /** 已选中的书签 ID 集合（用 Set 存储，查找/删除 O(1)） */
  const selectedIds = ref(new Set())

  /** 是否正在加载 */
  const loading = ref(false)

  // ---- Getters ----

  /** 获取已选中的书签对象数组 */
  const selectedBookmarks = computed(() => {
    return bookmarks.value.filter(b => selectedIds.value.has(b.id))
  })

  /** 是否有选中的书签 */
  const hasSelection = computed(() => selectedIds.value.size > 0)

  /** 是否全选 */
  const isAllSelected = computed(() => {
    return bookmarks.value.length > 0 && selectedIds.value.size === bookmarks.value.length
  })

  // ---- Actions ----

  /**
   * 获取指定文件夹下的书签列表
   * @param {number} folderId
   */
  async function fetchBookmarks(folderId) {
    currentFolderId.value = folderId
    loading.value = true
    // 切换文件夹时清空选中状态
    selectedIds.value = new Set()
    try {
      const res = await getBookmarksApi(folderId)
      bookmarks.value = res.data || []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建新书签
   * @param {Object} data - { title, url, folderId, iconUrl }
   */
  async function createBookmark(data) {
    const res = await createBookmarkApi(data)
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
    } catch (e) {
      console.error('Failed to refresh bookmarks after create:', e)
    }
    return res.data
  }

  /**
   * 更新书签
   * @param {number} id
   * @param {Object} data - { title, url, folderId, iconUrl }
   */
  async function updateBookmark(id, data) {
    const res = await updateBookmarkApi(id, data)
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
    } catch (e) {
      console.error('Failed to refresh bookmarks after update:', e)
    }
    return res.data
  }

  /**
   * 删除书签
   * @param {number} id
   */
  async function deleteBookmark(id) {
    const res = await deleteBookmarkApi(id)
    selectedIds.value.delete(id)
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
    } catch (e) {
      console.error('Failed to refresh bookmarks after delete:', e)
    }
    return res
  }

  /**
   * 批量更新书签排序（拖拽后调用）
   * @param {Array} data - [{ id, sortOrder }, ...]
   */
  async function sortBookmarks(data) {
    const res = await sortBookmarksApi(data)
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
    } catch (e) {
      console.error('Failed to refresh bookmarks after sort:', e)
    }
    return res
  }

  /**
   * 批量删除书签
   * @param {Array} ids - 书签 ID 数组
   */
  async function batchDelete(ids) {
    const res = await batchDeleteApi(ids)
    selectedIds.value = new Set()
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
    } catch (e) {
      console.error('Failed to refresh bookmarks after batch delete:', e)
    }
    return res
  }

  /**
   * 批量复制书签链接
   * 后端返回格式化好的文本，直接复制到剪贴板
   * @param {Array} ids - 书签 ID 数组
   * @returns {string} 格式化后的链接文本
   */
  async function batchCopyLinks(ids) {
    const res = await batchCopyApi(ids)
    return res.data
  }

  /**
   * 切换某个书签的选中状态（Ctrl+Click 时调用）
   * @param {number} id - 书签 ID
   */
  function toggleSelect(id) {
    const newSet = new Set(selectedIds.value)
    if (newSet.has(id)) {
      newSet.delete(id)
    } else {
      newSet.add(id)
    }
    selectedIds.value = newSet
  }

  /**
   * 全选当前书签列表
   */
  function selectAll() {
    selectedIds.value = new Set(bookmarks.value.map(b => b.id))
  }

  /**
   * 清空选中状态
   */
  function clearSelection() {
    selectedIds.value = new Set()
  }

  /**
   * 批量移动书签到目标文件夹
   * 移动后刷新当前书签列表和文件夹树（bookmarkCount 变化）
   * @param {Array} bookmarkIds - 书签 ID 数组
   * @param {number} targetFolderId - 目标文件夹 ID
   */
  async function moveBookmarks(bookmarkIds, targetFolderId) {
    await moveBookmarksApi(bookmarkIds, targetFolderId)
    try {
      if (currentFolderId.value) {
        await fetchBookmarks(currentFolderId.value)
      }
      const folderStore = useFolderStore()
      await folderStore.fetchTree()
    } catch (e) {
      console.error('Failed to refresh after move:', e)
    }
  }

  return {
    // state
    bookmarks,
    currentFolderId,
    selectedIds,
    loading,
    // getters
    selectedBookmarks,
    hasSelection,
    isAllSelected,
    // actions
    fetchBookmarks,
    createBookmark,
    updateBookmark,
    deleteBookmark,
    sortBookmarks,
    batchDelete,
    batchCopyLinks,
    toggleSelect,
    selectAll,
    clearSelection,
    moveBookmarks
  }
})
