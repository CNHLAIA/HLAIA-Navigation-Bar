/**
 * Pinia Folder Store — 文件夹树状态管理
 *
 * 职责：
 * - 管理当前用户的文件夹树形数据（folderTree）
 * - 追踪当前选中的文件夹（currentFolderId）
 * - 提供文件夹 CRUD、排序、移动的 actions
 * - 提供 currentFolder getter 和 folderPath（面包屑路径）getter
 *
 * 设计模式：Setup Store（Composition API 风格）
 * 使用 ref/reactive 定义状态，用普通函数定义 actions，用 computed 定义 getters。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getFolderTree as getFolderTreeApi,
  createFolder as createFolderApi,
  updateFolder as updateFolderApi,
  deleteFolder as deleteFolderApi,
  sortFolders as sortFoldersApi,
  moveFolder as moveFolderApi
} from '@/api/folder'

export const useFolderStore = defineStore('folder', () => {
  // ---- State ----

  /** 完整的文件夹树（嵌套结构，后端已递归构建好 children） */
  const folderTree = ref([])

  /** 当前选中的文件夹 ID，null 表示未选中任何文件夹 */
  const currentFolderId = ref(null)

  /** 是否正在加载 */
  const loading = ref(false)

  // ---- Getters ----

  /**
   * 当前选中的文件夹对象
   * 通过递归查找树形数据来定位当前节点
   */
  const currentFolder = computed(() => {
    if (!currentFolderId.value) return null
    return findNodeById(folderTree.value, currentFolderId.value)
  })

  /**
   * 面包屑路径：从根到当前文件夹的路径数组
   * 例如：[{ id: 1, name: '开发' }, { id: 5, name: '前端' }]
   * 用于在顶部显示导航路径
   */
  const folderPath = computed(() => {
    if (!currentFolderId.value) return []
    return buildPath(folderTree.value, currentFolderId.value)
  })

  // ---- Actions ----

  /**
   * 从后端获取文件夹树
   * 后端返回的是已经构建好的嵌套树形结构
   */
  async function fetchTree() {
    loading.value = true
    try {
      const res = await getFolderTreeApi()
      folderTree.value = res.data || []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建新文件夹
   * @param {Object} data - { name, parentId }
   */
  async function createFolder(data) {
    const res = await createFolderApi(data)
    // 创建成功后重新拉取整棵树（简单策略，避免复杂的本地树操作）
    await fetchTree()
    return res.data
  }

  /**
   * 更新文件夹（重命名）
   * @param {number} id
   * @param {Object} data - { name }
   */
  async function updateFolder(id, data) {
    const res = await updateFolderApi(id, data)
    await fetchTree()
    return res.data
  }

  /**
   * 删除文件夹（会级联删除子文件夹和书签）
   * @param {number} id
   */
  async function deleteFolder(id) {
    const res = await deleteFolderApi(id)
    // 如果删除的是当前选中的文件夹，清空选中状态
    if (currentFolderId.value === id) {
      currentFolderId.value = null
    }
    await fetchTree()
    return res
  }

  /**
   * 批量更新文件夹排序（拖拽后调用）
   * @param {Array} data - [{ id, sortOrder }, ...]
   */
  async function sortFolders(data) {
    const res = await sortFoldersApi(data)
    await fetchTree()
    return res
  }

  /**
   * 移动文件夹到新的父级
   * @param {number} id
   * @param {Object} data - { parentId }
   */
  async function moveFolder(id, data) {
    const res = await moveFolderApi(id, data)
    await fetchTree()
    return res
  }

  /**
   * 设置当前选中的文件夹
   * @param {number|null} id
   */
  function setCurrentFolder(id) {
    currentFolderId.value = id
  }

  // ---- 内部辅助函数 ----

  /**
   * 递归查找树中指定 ID 的节点
   * @param {Array} nodes - 树节点数组
   * @param {number} id - 目标 ID
   * @returns {Object|null}
   */
  function findNodeById(nodes, id) {
    for (const node of nodes) {
      if (node.id === id) return node
      if (node.children && node.children.length > 0) {
        const found = findNodeById(node.children, id)
        if (found) return found
      }
    }
    return null
  }

  /**
   * 构建从根到目标节点的路径数组（用于面包屑）
   * @param {Array} nodes - 树节点数组
   * @param {number} targetId - 目标文件夹 ID
   * @returns {Array} - 路径数组 [{ id, name }, ...]
   */
  function buildPath(nodes, targetId, path = []) {
    for (const node of nodes) {
      // 把当前节点加入路径
      const currentPath = [...path, { id: node.id, name: node.name }]
      if (node.id === targetId) return currentPath
      if (node.children && node.children.length > 0) {
        const result = buildPath(node.children, targetId, currentPath)
        if (result.length > 0) return result
      }
    }
    return []
  }

  return {
    // state
    folderTree,
    currentFolderId,
    loading,
    // getters
    currentFolder,
    folderPath,
    // actions
    fetchTree,
    createFolder,
    updateFolder,
    deleteFolder,
    sortFolders,
    moveFolder,
    setCurrentFolder
  }
})
