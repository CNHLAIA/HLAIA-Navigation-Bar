/**
 * Pinia Bookmark Store — 书签状态管理
 *
 * 职责：
 * - 管理当前文件夹下的书签列表（bookmarks）
 * - 管理多选状态（selectedIds），支持批量操作
 * - 提供书签 CRUD、排序、批量删除、批量复制的 actions
 * - 维护书签缓存（bookmarkCache），回访已访问文件夹时秒出数据
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

  /**
   * 书签缓存：以 folderId 为 key 缓存该文件夹的书签数组
   * 用途：用户回访已访问的文件夹时，先用缓存秒出，再后台静默刷新
   * Map<number, Array> — key 是 folderId，value 是书签数组
   */
  const bookmarkCache = ref(new Map())

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
   *
   * 缓存策略：
   * - 如果 bookmarkCache 中有该文件夹的缓存，立即用缓存填充 bookmarks，
   *   不设 loading（用户无感知），然后后台静默请求最新数据
   * - 如果没有缓存，走正常流程（loading = true，等 API 返回）
   * - 无论哪种路径，API 返回后都会更新缓存和 bookmarks
   *
   * @param {number} folderId
   */
  async function fetchBookmarks(folderId) {
    currentFolderId.value = folderId
    // 切换文件夹时清空选中状态
    selectedIds.value = new Set()

    // 检查缓存：有缓存则立即展示，不触发 loading
    const cached = bookmarkCache.value.get(folderId)
    if (cached) {
      // 立即用缓存数据填充，用户看到秒出效果
      bookmarks.value = [...cached]
      // loading 保持 false，UI 不会显示骨架屏/加载动画

      // 后台静默请求最新数据
      try {
        const res = await getBookmarksApi(folderId)
        const freshData = res.data || []
        bookmarks.value = freshData
        // 同步更新缓存
        bookmarkCache.value.set(folderId, freshData)
      } catch (e) {
        console.error('Failed to refresh bookmarks in background:', e)
      }
    } else {
      // 没有缓存，走正常加载流程：显示 loading，等 API 返回
      loading.value = true
      try {
        const res = await getBookmarksApi(folderId)
        const data = res.data || []
        bookmarks.value = data
        // 首次加载的数据存入缓存，下次回访时秒出
        bookmarkCache.value.set(folderId, data)
      } finally {
        loading.value = false
      }
    }
  }

  /**
   * 创建新书签
   * @param {Object} data - { title, url, folderId, iconUrl }
   */
  async function createBookmark(data) {
    const res = await createBookmarkApi(data)
    // 写操作后失效当前文件夹缓存，确保 fetchBookmarks 从 API 拿到最新数据
    bookmarkCache.value.delete(currentFolderId.value)
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
    bookmarkCache.value.delete(currentFolderId.value)
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
    bookmarkCache.value.delete(currentFolderId.value)
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
    bookmarkCache.value.delete(currentFolderId.value)
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
    bookmarkCache.value.delete(currentFolderId.value)
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
    // 移动操作影响源文件夹和目标文件夹，两个缓存都要失效
    bookmarkCache.value.delete(currentFolderId.value)
    bookmarkCache.value.delete(targetFolderId)
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
    bookmarkCache,
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
