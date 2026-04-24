<!--
  BookmarkCard.vue -- 单个书签卡片组件

  设计风格：Warm Minimal Light 主题
  - 白色卡片背景 + 柔和阴影
  - 悬停时微妙的上移 + 阴影增强
  - 选中状态有主题蓝色边框
  - Favicon 显示：有图标 URL 则显示图片，否则取标题首字母作为彩色占位

  交互：
  - 单击：在新标签页打开 URL
  - Ctrl+单击：切换选中状态（多选模式）
  - 右键：上下文菜单（编辑、删除）
-->
<template>
  <div
    class="bookmark-card"
    :class="{ 'is-selected': selected, 'is-selecting': isSelecting }"
    @click="handleClick"
    @contextmenu.prevent="handleContextMenu"
  >
    <!-- Favicon / 首字母占位 -->
    <div class="card-favicon">
      <img
        v-if="hasFavicon"
        :src="faviconUrl"
        :alt="bookmark.title"
        class="favicon-img"
        @error="onFaviconError"
      />
      <span v-else class="favicon-letter">{{ firstLetter }}</span>
    </div>

    <!-- 标题和 URL -->
    <div class="card-info">
      <div class="card-title" :title="bookmark.title">{{ bookmark.title }}</div>
      <div class="card-url" :title="bookmark.url">{{ truncatedUrl }}</div>
    </div>

    <!-- 选中指示器 -->
    <div v-if="selected" class="select-indicator">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path d="M11.6666 3.5L5.24992 9.91667L2.33325 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
/**
 * Props 定义
 * - bookmark: 书签数据对象 { id, title, url, iconUrl, folderId, sortOrder }
 * - selected: 是否被选中（多选模式）
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

useI18n()

const props = defineProps({
  bookmark: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  },
  isSelecting: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click', 'contextmenu'])

/** Favicon 加载失败时切换到首字母显示 */
const faviconError = ref(false)

/** 当 bookmark 数据变化时重置错误状态，允许重新加载图标 */
watch(() => props.bookmark.iconUrl, () => {
  faviconError.value = false
})

/**
 * Favicon URL 处理（多级回退策略）：
 *   1. iconUrl 为完整 http URL → 直接使用
 *   2. iconUrl 为 null → 从书签 URL 提取域名，尝试 /favicon.ico
 *   3. 以上都失败（onFaviconError）→ 显示首字母占位
 */
const faviconUrl = computed(() => {
  const iconUrl = props.bookmark.iconUrl
  if (iconUrl && iconUrl.startsWith('http')) return iconUrl
  // iconUrl 为空或非 http → 从书签 URL 构造域名 favicon
  try {
    const url = new URL(props.bookmark.url)
    return `${url.origin}/favicon.ico`
  } catch {
    return ''
  }
})

/** 是否有可用的 favicon URL（用于控制 img 标签显示） */
const hasFavicon = computed(() => {
  return faviconUrl.value !== '' && !faviconError.value
})

/** 取标题首字母作为占位图标 */
const firstLetter = computed(() => {
  const title = props.bookmark.title || ''
  return title.charAt(0).toUpperCase() || '?'
})

/** URL 截断显示（超过一定长度加省略号） */
const truncatedUrl = computed(() => {
  const url = props.bookmark.url || ''
  if (url.length > 40) return url.substring(0, 40) + '...'
  return url
})

/** Favicon 图片加载失败时的回调 */
function onFaviconError() {
  faviconError.value = true
}

/**
 * 处理卡片点击
 * - 普通点击：emit click 事件（由父组件决定行为）
 * - Ctrl/Cmd + 点击：切换选中
 */
function handleClick(e) {
  emit('click', {
    bookmark: props.bookmark,
    ctrlKey: e.ctrlKey || e.metaKey,
    shiftKey: e.shiftKey
  })
}

/**
 * 处理右键菜单
 * 阻止默认右键菜单，emit 自定义事件让父组件显示自定义菜单
 */
function handleContextMenu(e) {
  emit('contextmenu', {
    bookmark: props.bookmark,
    x: e.clientX,
    y: e.clientY
  })
}
</script>

<style scoped>
/* ---- 书签卡片 ---- */
.bookmark-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  overflow: hidden;
}

/* 悬停效果：上移 + 阴影增强 */
.bookmark-card:hover {
  border-color: rgba(74, 127, 199, 0.15);
  transform: translateY(-2px);
  box-shadow: var(--hlaia-shadow-hover);
}

/* 选中状态：主题蓝色边框 */
.bookmark-card.is-selected {
  border-color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.04);
  box-shadow: 0 0 0 1px var(--hlaia-primary);
}

.bookmark-card.is-selected:hover {
  background: rgba(74, 127, 199, 0.06);
}

/* 多选模式下的悬停样式提示用户可选中 */
.bookmark-card.is-selecting:not(.is-selected):hover {
  border-color: var(--hlaia-primary-light);
  background: rgba(74, 127, 199, 0.02);
}

/* ---- Favicon 区域 ---- */
.card-favicon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: var(--hlaia-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hlaia-surface-light);
  overflow: hidden;
}

.favicon-img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

/* 首字母占位：使用主题蓝色 */
.favicon-letter {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--hlaia-primary);
}

/* ---- 文本信息 ---- */
.card-info {
  flex: 1;
  min-width: 0; /* 允许 flex 子元素截断文本 */
}

.card-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.card-url {
  font-family: 'DM Sans', sans-serif;
  font-size: 11.5px;
  color: var(--hlaia-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

/* ---- 选中指示器（右上角勾选图标） ---- */
.select-indicator {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--hlaia-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  animation: popIn 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes popIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
