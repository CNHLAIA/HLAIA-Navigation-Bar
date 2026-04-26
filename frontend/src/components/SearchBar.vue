<template>
  <div class="search-bar" :class="{ 'is-focused': focused }">
    <svg class="search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
      <circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.3"/>
      <path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
    </svg>
    <input
      ref="inputRef"
      v-model="keyword"
      class="search-input"
      :placeholder="t('search.placeholder')"
      @focus="focused = true"
      @blur="handleBlur"
      @keydown.enter="handleSearch"
      @keydown.escape="closeDropdown"
      @input="handleInput"
    />
    <button v-if="keyword" class="search-clear" @click="clearSearch">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path d="M4 4l6 6M10 4l-6 6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
      </svg>
    </button>

    <!-- 下拉建议列表 -->
    <div v-if="showDropdown && suggestions.length" class="search-dropdown">
      <div
        v-for="item in suggestions"
        :key="`${item.type}-${item.id}`"
        class="suggestion-item"
        @mousedown.prevent="handleSelect(item)"
      >
        <img
          v-if="item.type === 'bookmark' && item.icon"
          :src="item.icon"
          class="suggestion-icon"
          @error="handleIconError"
        />
        <span v-else-if="item.type === 'folder'" class="suggestion-icon-text">📁</span>
        <span v-else class="suggestion-icon-text">🔗</span>
        <div class="suggestion-info">
          <span class="suggestion-title">{{ item.title }}</span>
          <span v-if="item.url" class="suggestion-url">{{ item.url }}</span>
        </div>
        <span class="suggestion-type">{{ item.type === 'folder' ? t('search.folder') : t('search.bookmark') }}</span>
      </div>
    </div>
  </div>

  <!-- 搜索结果弹窗 -->
  <el-dialog
    v-model="showResults"
    :title="t('search.resultsTitle', { keyword: searchKeyword })"
    width="640px"
    top="10vh"
    class="search-results-dialog"
    @close="showResults = false"
  >
    <div v-if="results.length" class="search-results">
      <div
        v-for="item in results"
        :key="`${item.type}-${item.id}`"
        class="result-item"
        @click="handleResultClick(item)"
      >
        <img
          v-if="item.type === 'bookmark' && item.icon"
          :src="item.icon"
          class="result-icon"
          @error="handleIconError"
        />
        <span v-else-if="item.type === 'folder'" class="result-icon-text">📁</span>
        <span v-else class="result-icon-text">🔗</span>
        <div class="result-info">
          <span class="result-title">{{ item.title }}</span>
          <span v-if="item.url" class="result-url">{{ item.url }}</span>
        </div>
        <span class="result-type-badge">{{ item.type === 'folder' ? t('search.folder') : t('search.bookmark') }}</span>
      </div>
    </div>
    <div v-else class="search-empty">
      <p>{{ t('search.noResults') }}</p>
    </div>
    <div v-if="total > size" class="search-pagination">
      <span class="pagination-info">{{ t('search.totalResults', { total }) }}</span>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { searchSuggest, searchAll } from '@/api/search'
import { useFolderStore } from '@/stores/folder'

const { t } = useI18n()
const router = useRouter()
const folderStore = useFolderStore()

const inputRef = ref(null)
const keyword = ref('')
const focused = ref(false)
const suggestions = ref([])
const showDropdown = ref(false)
const showResults = ref(false)
const results = ref([])
const total = ref(0)
const searchKeyword = ref('')

let debounceTimer = null

function handleInput() {
  clearTimeout(debounceTimer)
  if (!keyword.value.trim()) {
    suggestions.value = []
    showDropdown.value = false
    return
  }
  debounceTimer = setTimeout(async () => {
    try {
      const res = await searchSuggest(keyword.value.trim())
      suggestions.value = res.data || []
      showDropdown.value = suggestions.value.length > 0
    } catch {
      suggestions.value = []
    }
  }, 300)
}

async function handleSearch() {
  if (!keyword.value.trim()) return
  showDropdown.value = false
  searchKeyword.value = keyword.value.trim()
  try {
    const res = await searchAll(searchKeyword.value)
    results.value = res.data?.items || []
    total.value = res.data?.total || 0
    showResults.value = true
  } catch {
    // 静默处理
  }
}

function handleSelect(item) {
  showDropdown.value = false
  if (item.type === 'folder') {
    folderStore.setCurrentFolder(item.id)
    router.push('/')
  } else {
    folderStore.setCurrentFolder(item.folderId)
    router.push('/')
  }
}

function handleResultClick(item) {
  showResults.value = false
  if (item.type === 'folder') {
    folderStore.setCurrentFolder(item.id)
  } else {
    folderStore.setCurrentFolder(item.folderId)
  }
  router.push('/')
}

function handleBlur() {
  focused.value = false
  setTimeout(() => { showDropdown.value = false }, 150)
}

function closeDropdown() {
  showDropdown.value = false
  inputRef.value?.blur()
}

function clearSearch() {
  keyword.value = ''
  suggestions.value = []
  showDropdown.value = false
}

function handleIconError(e) {
  e.target.style.display = 'none'
}
</script>

<style scoped>
.search-bar {
  position: relative;
  display: flex;
  align-items: center;
  width: 240px;
  height: 34px;
  padding: 0 10px;
  border-radius: 6px;
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
  transition: all 0.2s ease;
}

.search-bar.is-focused {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.12);
}

.search-icon {
  flex-shrink: 0;
  color: var(--hlaia-text-muted);
}

.search-bar.is-focused .search-icon {
  color: var(--hlaia-primary);
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text);
  padding: 0 6px;
  height: 100%;
}

.search-input::placeholder {
  color: var(--hlaia-text-muted);
}

.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--hlaia-text-muted);
  cursor: pointer;
  padding: 2px;
  border-radius: 4px;
}

.search-clear:hover {
  color: var(--hlaia-text);
  background: var(--hlaia-surface-light);
}

/* 下拉建议 */
.search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
  box-shadow: var(--hlaia-shadow-hover);
  z-index: 100;
  max-height: 320px;
  overflow-y: auto;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.suggestion-item:hover {
  background: var(--hlaia-surface-light);
}

.suggestion-item:first-child {
  border-radius: var(--hlaia-radius-lg) var(--hlaia-radius-lg) 0 0;
}

.suggestion-item:last-child {
  border-radius: 0 0 var(--hlaia-radius-lg) var(--hlaia-radius-lg);
}

.suggestion-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  flex-shrink: 0;
}

.suggestion-icon-text {
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
}

.suggestion-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.suggestion-title {
  font-size: 13px;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.suggestion-url {
  font-size: 11px;
  color: var(--hlaia-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.suggestion-type {
  font-size: 10px;
  color: var(--hlaia-text-muted);
  background: var(--hlaia-surface-light);
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

/* 搜索结果弹窗 */
.search-results {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.result-item:hover {
  background: var(--hlaia-surface-light);
}

.result-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  flex-shrink: 0;
}

.result-icon-text {
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
}

.result-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--hlaia-text);
}

.result-url {
  font-size: 12px;
  color: var(--hlaia-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-highlight {
  font-size: 12px;
  color: var(--hlaia-text-muted);
}

.result-highlight :deep(em) {
  color: var(--hlaia-primary);
  font-style: normal;
  font-weight: 600;
}

.result-type-badge {
  font-size: 10px;
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.search-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--hlaia-text-muted);
  font-size: 14px;
}

.search-pagination {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}

.pagination-info {
  font-size: 12px;
  color: var(--hlaia-text-muted);
}
</style>

<style>
/* 全局样式：搜索结果弹窗 */
.search-results-dialog .el-dialog__header {
  border-bottom: 1px solid var(--hlaia-border);
  padding-bottom: 16px;
}
.search-results-dialog .el-dialog__body {
  padding: 16px;
  max-height: 60vh;
  overflow-y: auto;
}
</style>
