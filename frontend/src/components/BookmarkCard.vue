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
  - 悬浮 ≥1 秒且备注非空：弹出只读 Markdown 备注弹窗（teleport 到 body，
    因卡片自身 overflow:hidden 会裁剪内部弹层）
-->
<template>
  <div
    ref="cardRef"
    class="bookmark-card"
    :class="{ 'is-selected': selected, 'is-selecting': isSelecting }"
    @click="handleClick"
    @contextmenu.prevent="handleContextMenu"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
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

  <!-- 悬浮备注弹窗：卡片自身 overflow:hidden，必须 teleport 到 body 渲染 -->
  <teleport to="body">
    <div
      v-if="notePopover.visible"
      ref="notePopoverRef"
      class="note-popover"
      :style="{ left: notePopover.x + 'px', top: notePopover.y + 'px' }"
      v-html="noteHtml"
    ></div>
  </teleport>
</template>

<script setup>
/**
 * Props 定义
 * - bookmark: 书签数据对象 { id, title, url, iconUrl, folderId, sortOrder, description }
 * - selected: 是否被选中（多选模式）
 */
import { computed, reactive, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/utils/markdown'

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

/** 卡片根元素引用：悬浮定时器触发时量取 getBoundingClientRect 定位弹窗 */
const cardRef = ref(null)

/** 悬浮备注弹窗 DOM 引用：渲染后量取实际宽高做视口钳制 */
const notePopoverRef = ref(null)

/** Favicon 加载失败时切换到首字母显示 */
const faviconError = ref(false)

/** 当 bookmark 数据变化时重置错误状态，允许重新加载图标 */
watch(() => props.bookmark.iconUrl, () => {
  faviconError.value = false
})

/**
 * Favicon URL 处理（多级回退策略）：
 *   1. iconUrl 为完整 http URL → 直接使用
 *   2. iconUrl 为 null → 通过后端代理 API 获取 favicon（避免触发目标服务器的认证弹窗）
 *   3. 以上都失败（onFaviconError）→ 显示首字母占位
 */
const faviconUrl = computed(() => {
  const iconUrl = props.bookmark.iconUrl
  if (iconUrl && iconUrl.startsWith('http')) return iconUrl
  // iconUrl 为空或非 http → 通过后端代理 API 获取 favicon，避免浏览器直接请求目标服务器
  try {
    const url = new URL(props.bookmark.url)
    return `/api/favicon?url=${encodeURIComponent(url.origin)}`
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

// ---- 悬浮备注弹窗 ----

/** 悬浮延迟：≥1 秒才弹出，避免鼠标扫过卡片时误弹 */
const NOTE_HOVER_DELAY = 1000

/** 弹窗与卡片/视口边缘的留白 */
const POPOVER_GAP = 8

/** 悬浮定时器 ID：纯内部控制流，不需要响应式 */
let noteTimer = null

/** 弹窗状态：visible 控制显隐，x/y 为 fixed 定位坐标 */
const notePopover = reactive({ visible: false, x: 0, y: 0 })

/** 弹窗内容：渲染后的 Markdown（description 为空串时 renderMarkdown 返回 ''） */
const noteHtml = computed(() => renderMarkdown(props.bookmark.description || ''))

/**
 * 鼠标移入卡片：备注非空白时启动延迟定时器
 * 纯空白备注（null / '' / 全空白字符）不弹，与"清空备注后不再弹框"的语义一致
 */
function handleMouseEnter() {
  clearNoteTimer()
  const desc = props.bookmark.description
  if (!desc || !desc.trim()) return
  noteTimer = setTimeout(showNotePopover, NOTE_HOVER_DELAY)
}

/**
 * 鼠标移开：清定时器 + 关弹窗
 * 弹窗本体 pointer-events:none，鼠标"穿过"弹窗不会抑制卡片的 mouseleave
 */
function handleMouseLeave() {
  clearNoteTimer()
  notePopover.visible = false
}

/** 清除悬浮定时器 */
function clearNoteTimer() {
  if (noteTimer) {
    clearTimeout(noteTimer)
    noteTimer = null
  }
}

/**
 * 显示悬浮备注弹窗：两阶段定位
 *   1. 先按"卡片正下方"provisional 坐标渲染（此时用户还看不到）
 *   2. nextTick 量取弹窗实际宽高后做视口钳制与上下翻转
 * nextTick 是微任务，浏览器绘制前完成修正，不产生可见的位置跳动
 */
function showNotePopover() {
  const card = cardRef.value
  if (!card) return
  const rect = card.getBoundingClientRect()

  notePopover.x = rect.left
  notePopover.y = rect.bottom + POPOVER_GAP
  notePopover.visible = true

  nextTick(() => {
    const pop = notePopoverRef.value
    if (!pop) return
    const w = pop.offsetWidth
    const h = pop.offsetHeight
    const vw = window.innerWidth
    const vh = window.innerHeight

    // 垂直：优先卡片下方；下方放不下且上方更宽裕时翻转到卡片上方
    let y = rect.bottom + POPOVER_GAP
    if (y + h > vh - POPOVER_GAP && rect.top - POPOVER_GAP - h >= POPOVER_GAP) {
      y = rect.top - POPOVER_GAP - h
    }
    // 极端小视口（翻转后仍放不下）：贴边显示，弹窗内部滚动
    y = Math.max(POPOVER_GAP, Math.min(y, vh - POPOVER_GAP - h))

    // 水平：默认与卡片左对齐，越界时钳制在视口内
    let x = rect.left
    if (x + w > vw - POPOVER_GAP) x = vw - POPOVER_GAP - w
    if (x < POPOVER_GAP) x = POPOVER_GAP

    notePopover.x = x
    notePopover.y = y
  })
}

// 组件卸载（拖拽排序、数据刷新都可能移除卡片）时清理定时器，防止悬挂回调
onBeforeUnmount(() => {
  clearNoteTimer()
})
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

<!-- 全局样式：悬浮备注弹窗 teleport 到 body，scoped 无法命中 -->
<style>
/* ---- 悬浮备注弹窗（视觉语言与右键菜单一致：圆角/描边/悬浮阴影） ---- */
.note-popover {
  position: fixed;
  /* 低于右键菜单(9999)：菜单打开时菜单层级更高 */
  z-index: 9990;
  max-width: 360px;
  max-height: 40vh;
  overflow: auto;
  /* 只读预览：鼠标可穿过弹窗，卡片 mouseleave 即消失，避免弹窗挡住相邻卡片 */
  pointer-events: none;
  padding: 12px 16px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow-hover);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  line-height: 1.7;
  color: var(--hlaia-text);
  animation: notePopoverIn 0.15s ease;
}

@keyframes notePopoverIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* ---- Markdown 渲染基础排版（与 NoteDialog 预览区同款元素样式） ---- */
.note-popover :first-child { margin-top: 0; }
.note-popover :last-child { margin-bottom: 0; }

.note-popover h1,
.note-popover h2,
.note-popover h3,
.note-popover h4 {
  margin: 0.6em 0 0.3em;
  font-weight: 600;
  line-height: 1.4;
  color: var(--hlaia-text);
}

.note-popover h1 { font-size: 17px; }
.note-popover h2 { font-size: 15.5px; }
.note-popover h3 { font-size: 14.5px; }
.note-popover h4 { font-size: 13.5px; }

.note-popover p {
  margin: 0.4em 0;
  /* 备注是流式文本，保留换行空格的折行行为，长串字符也能折行不撑破弹窗 */
  overflow-wrap: break-word;
}

.note-popover ul,
.note-popover ol {
  padding-left: 1.4em;
  margin: 0.4em 0;
}

.note-popover li {
  margin: 0.15em 0;
}

/* 链接不可点（弹窗 pointer-events:none），按主题色提示其为链接即可 */
.note-popover a {
  color: var(--hlaia-primary);
  text-decoration: none;
}

.note-popover code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.note-popover pre {
  padding: 10px 12px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  overflow-x: auto;
}

.note-popover pre code {
  padding: 0;
  border: none;
  background: transparent;
}

.note-popover blockquote {
  margin: 0.5em 0;
  padding: 2px 12px;
  border-left: 3px solid var(--hlaia-primary-light);
  color: var(--hlaia-text-muted);
}

.note-popover img {
  max-width: 100%;
}

.note-popover hr {
  margin: 0.8em 0;
  border: none;
  border-top: 1px solid var(--hlaia-border);
}

.note-popover strong {
  font-weight: 600;
}
</style>
