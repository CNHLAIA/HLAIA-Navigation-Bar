<!--
  StagingList.vue — 暂存区条目列表组件

  设计风格：
  - Warm Minimal Light 主题
  - 网格布局（4/3/2 列响应式）
  - 每张卡片展示 Favicon、标题、URL、倒计时、操作按钮
  - 即将过期的卡片有琥珀色/红色警示色调
  - 加载骨架屏动画

  交互：
  - 点击标题在新标签页打开 URL
  - "Move to Folder" 按钮打开文件夹选择器对话框
  - "Set Expiry" 按钮打开过期时间设置对话框
  - "Delete" 按钮删除条目（带确认）
-->
<template>
  <div class="staging-list-wrapper">
    <!-- 加载骨架屏 -->
    <div v-if="loading" class="staging-skeleton-grid">
      <div
        v-for="i in 6"
        :key="i"
        class="skeleton-card"
      >
        <div class="skeleton-icon shimmer"></div>
        <div class="skeleton-lines">
          <div class="skeleton-line shimmer" style="width: 70%"></div>
          <div class="skeleton-line shimmer" style="width: 90%"></div>
          <div class="skeleton-line shimmer" style="width: 40%"></div>
        </div>
      </div>
    </div>

    <!-- 暂存条目网格 -->
    <div v-else-if="items.length > 0" class="staging-grid">
      <div
        v-for="item in items"
        :key="item.id"
        class="staging-card"
        :class="{
          'is-expiring-soon': getTimeRemaining(item.expireAt) === 'soon',
          'is-expiring-urgent': getTimeRemaining(item.expireAt) === 'urgent'
        }"
      >
        <!-- Favicon / 首字母占位 -->
        <div class="card-favicon">
          <img
            v-if="item.icon && !faviconErrors[item.id]"
            :src="getFaviconUrl(item)"
            :alt="item.title"
            class="favicon-img"
            @error="() => handleFaviconError(item.id)"
          />
          <span v-else class="favicon-letter">{{ getFirstLetter(item.title) }}</span>
        </div>

        <!-- 文本信息 -->
        <div class="card-info">
          <a
            :href="item.url"
            target="_blank"
            rel="noopener noreferrer"
            class="card-title"
            :title="item.title"
          >{{ item.title }}</a>
          <div class="card-url" :title="item.url">{{ truncateUrl(item.url) }}</div>
        </div>

        <!-- 倒计时徽章 -->
        <div class="card-timer" :class="getTimerClass(item.expireAt)">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="4.5" stroke="currentColor" stroke-width="1"/>
            <path d="M6 3.5V6L7.5 7.5" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
          </svg>
          <span>{{ formatCountdown(item.expireAt) }}</span>
        </div>

        <!-- 操作按钮 -->
        <div class="card-actions">
          <button
            class="action-btn action-move"
            :title="t('staging.tooltips.moveToFolder')"
            @click="$emit('move-to-folder', item)"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M1.75 5.25h6.125a2.625 2.625 0 0 1 0 5.25H5.833M1.75 5.25l2.333-2.333M1.75 5.25l2.333 2.333" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button
            class="action-btn action-expiry"
            :title="t('staging.tooltips.setExpiry')"
            @click="$emit('set-expiry', item)"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="7" cy="7" r="5.25" stroke="currentColor" stroke-width="1.2"/>
              <path d="M7 4.375V7l1.75 1.75" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
            </svg>
          </button>
          <button
            class="action-btn action-delete"
            :title="t('staging.tooltips.delete')"
            @click="$emit('delete', item)"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M1.75 3.5h10.5M5.25 3.5V2.333a1.167 1.167 0 0 1 1.167-1.166h1.167a1.167 1.167 0 0 1 1.166 1.166V3.5m1.75 0v8.167a1.167 1.167 0 0 1-1.166 1.166H4.667a1.167 1.167 0 0 1-1.167-1.166V3.5h7.583z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="staging-empty">
      <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
        <rect x="8" y="8" width="40" height="40" rx="8" stroke="currentColor" stroke-width="1.5" opacity="0.3"/>
        <path d="M20 24h16M20 28h12M20 32h14" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" opacity="0.2"/>
        <circle cx="40" cy="14" r="8" fill="currentColor" opacity="0.08"/>
        <path d="M40 10v8M36 14h8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" opacity="0.3"/>
      </svg>
      <p class="empty-title">{{ t('staging.empty.title') }}</p>
      <p class="empty-desc">{{ t('staging.empty.description') }}</p>
    </div>
  </div>
</template>

<script setup>
/**
 * StagingList — 暂存区条目网格列表
 *
 * Props:
 * - items: 暂存条目数组
 * - loading: 是否正在加载
 *
 * Events:
 * - move-to-folder: 点击"移动到文件夹"
 * - set-expiry: 点击"设置过期时间"
 * - delete: 点击"删除"
 *
 * 倒计时逻辑：
 * - expireAt 是后端返回的 ISO 时间字符串
 * - 通过 Date.parse 转为时间戳，与当前时间计算差值
 * - 分类：normal（>30min）、soon（<30min）、urgent（<10min）
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  items: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['move-to-folder', 'set-expiry', 'delete'])

/** 记录 Favicon 加载失败的条目 ID */
const faviconErrors = ref({})

/**
 * Favicon 加载失败回调
 * 切换到首字母显示模式
 */
function handleFaviconError(id) {
  faviconErrors.value[id] = true
}

/**
 * 构建 Favicon URL
 * 优先使用条目自带的 icon URL，否则通过 Google Favicon 服务获取
 */
function getFaviconUrl(item) {
  if (!item.icon) return ''
  if (item.icon.startsWith('http')) return item.icon
  try {
    const url = new URL(item.url)
    return `https://www.google.com/s2/favicons?domain=${url.hostname}&sz=32`
  } catch {
    return ''
  }
}

/** 取标题首字母 */
function getFirstLetter(title) {
  return (title || '?').charAt(0).toUpperCase()
}

/** 截断 URL 显示 */
function truncateUrl(url) {
  if (!url) return ''
  return url.length > 45 ? url.substring(0, 45) + '...' : url
}

/**
 * 格式化倒计时文本
 * 将 expireAt (ISO string) 转为可读的剩余时间
 * 例如："2h 15m"、"45m"、"3m"
 */
function formatCountdown(expireAt) {
  if (!expireAt) return t('staging.timer.noExpiry')
  const diff = new Date(expireAt).getTime() - Date.now()
  if (diff <= 0) return t('staging.timer.expired')

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60

  if (hours > 0) {
    return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`
  }
  return `${minutes}m`
}

/**
 * 获取时间分类：用于卡片样式判断
 * - 'normal': 剩余 >30 分钟
 * - 'soon': 剩余 10-30 分钟
 * - 'urgent': 剩余 <10 分钟
 */
function getTimeRemaining(expireAt) {
  if (!expireAt) return 'normal'
  const diff = new Date(expireAt).getTime() - Date.now()
  const minutes = Math.floor(diff / 60000)
  if (minutes <= 0) return 'urgent'
  if (minutes < 10) return 'urgent'
  if (minutes < 30) return 'soon'
  return 'normal'
}

/**
 * 获取倒计时徽章的 CSS class
 */
function getTimerClass(expireAt) {
  const status = getTimeRemaining(expireAt)
  return {
    'timer-normal': status === 'normal',
    'timer-soon': status === 'soon',
    'timer-urgent': status === 'urgent'
  }
}
</script>

<style scoped>
/* ---- 容器 ---- */
.staging-list-wrapper {
  width: 100%;
}

/* ---- 骨架屏 ---- */
.staging-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

@media (max-width: 1200px) {
  .staging-skeleton-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .staging-skeleton-grid { grid-template-columns: repeat(2, 1fr); }
}

.skeleton-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
}

.skeleton-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  flex-shrink: 0;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 2px;
}

.skeleton-line {
  height: 10px;
  border-radius: 5px;
}

/* 微光扫描动画：从左到右的渐变扫光效果（浅色主题） */
.shimmer {
  background: var(--hlaia-surface-light);
  background-image: linear-gradient(
    90deg,
    var(--hlaia-surface-light) 0%,
    #EDECEA 50%,
    var(--hlaia-surface-light) 100%
  );
  background-size: 200% 100%;
  animation: shimmerMove 1.8s ease-in-out infinite;
}

@keyframes shimmerMove {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ---- 暂存条目网格 ---- */
.staging-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

@media (max-width: 1200px) {
  .staging-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .staging-grid { grid-template-columns: repeat(2, 1fr); }
}

/* ---- 单个暂存卡片（Warm Minimal Light） ---- */
.staging-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
  cursor: default;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.staging-card:hover {
  border-color: var(--hlaia-primary-light);
  transform: translateY(-2px);
  box-shadow: var(--hlaia-shadow-hover);
}

/* ---- 即将过期状态：琥珀色 ---- */
.staging-card.is-expiring-soon {
  border-color: var(--hlaia-warning);
  background: rgba(245, 166, 35, 0.03);
}

.staging-card.is-expiring-soon:hover {
  border-color: var(--hlaia-warning);
  box-shadow: var(--hlaia-shadow-hover), 0 0 12px rgba(245, 166, 35, 0.08);
}

/* ---- 紧急过期状态：红色 ---- */
.staging-card.is-expiring-urgent {
  border-color: var(--hlaia-danger);
  background: rgba(231, 76, 60, 0.02);
}

.staging-card.is-expiring-urgent:hover {
  border-color: var(--hlaia-danger);
  box-shadow: var(--hlaia-shadow-hover), 0 0 12px rgba(231, 76, 60, 0.08);
}

/* ---- Favicon 区域 ---- */
.card-favicon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(74, 127, 199, 0.08);
  overflow: hidden;
}

.favicon-img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.favicon-letter {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--hlaia-primary);
}

/* ---- 文本信息 ---- */
.card-info {
  flex: 1;
  min-width: 0;
}

.card-title {
  display: block;
  font-family: 'DM Sans', sans-serif;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--hlaia-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-decoration: none;
  line-height: 1.4;
  transition: color 0.2s ease;
}

.card-title:hover {
  color: var(--hlaia-primary);
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

/* ---- 倒计时徽章 ---- */
.card-timer {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  padding: 3px 8px;
  border-radius: 6px;
  font-family: 'DM Sans', sans-serif;
  font-size: 11px;
  font-weight: 500;
}

.timer-normal {
  background: rgba(39, 174, 96, 0.08);
  color: var(--hlaia-success);
}

.timer-soon {
  background: rgba(245, 166, 35, 0.1);
  color: var(--hlaia-warning);
}

.timer-urgent {
  background: rgba(231, 76, 60, 0.1);
  color: var(--hlaia-danger);
  animation: urgentPulse 2s ease-in-out infinite;
}

@keyframes urgentPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* ---- 操作按钮组 ---- */
.card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.staging-card:hover .card-actions {
  opacity: 1;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  border: none;
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: var(--hlaia-border);
  color: var(--hlaia-text);
}

.action-move:hover {
  color: var(--hlaia-primary);
  background: rgba(74, 127, 199, 0.08);
}

.action-expiry:hover {
  color: var(--hlaia-warning);
  background: rgba(245, 166, 35, 0.08);
}

.action-delete:hover {
  color: var(--hlaia-danger);
  background: rgba(231, 76, 60, 0.08);
}

/* ---- 空状态 ---- */
.staging-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--hlaia-text-muted);
  gap: 8px;
}

.empty-title {
  font-family: 'DM Sans', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--hlaia-text-muted);
  margin: 12px 0 0;
}

.empty-desc {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  opacity: 0.7;
  margin: 0;
}

/* ---- 移动端：操作按钮始终可见 ---- */
@media (max-width: 768px) {
  .card-actions {
    opacity: 1;
  }
}
</style>
