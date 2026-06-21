<!--
  BatchToolbar.vue -- 批量操作工具栏

  设计说明：Warm Minimal Light 主题
  - 当有书签被选中时，从内容区顶部滑入
  - 显示选中数量、批量操作按钮（删除、复制链接）、全选/取消全选
  - 白色背景 + 顶部阴影

  动画：使用 CSS transition + transform 实现滑入/滑出效果
-->
<template>
  <transition name="toolbar-slide">
    <div v-if="visible" class="batch-toolbar">
      <!-- 左侧：选中计数 -->
      <div class="toolbar-left">
        <div class="selection-count">
          <span class="count-number">{{ count }}</span>
          <span class="count-label">{{ t('batchToolbar.selected') }}</span>
        </div>
      </div>

      <!-- 中间：操作按钮 -->
      <div class="toolbar-actions">
        <button class="toolbar-btn btn-delete" @click="$emit('delete')">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M2 4h12M5.333 4V2.667a1.333 1.333 0 0 1 1.334-1.334h2.666a1.333 1.333 0 0 1 1.334 1.334V4m2 0v9.333a1.333 1.333 0 0 1-1.334 1.334H4.667a1.333 1.333 0 0 1-1.334-1.334V4h9.334Z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ t('batchToolbar.delete') }}
        </button>

        <button class="toolbar-btn btn-copy" @click="$emit('copy')">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <rect x="5.5" y="5.5" width="7" height="7" rx="1" stroke="currentColor" stroke-width="1.2"/>
            <path d="M3.5 10.5V3.5a1 1 0 0 1 1-1h7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
          {{ t('batchToolbar.copyLinks') }}
        </button>

        <button class="toolbar-btn btn-select-all" @click="$emit('toggle-select-all')">
          {{ isAllSelected ? t('batchToolbar.deselectAll') : t('batchToolbar.selectAll') }}
        </button>
      </div>

      <!-- 右侧：关闭按钮 -->
      <button class="toolbar-dismiss" @click="$emit('dismiss')">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M10.5 3.5L3.5 10.5M3.5 3.5l7 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
  </transition>
</template>

<script setup>
/**
 * BatchToolbar 组件
 * 纯展示+事件派发组件，不包含业务逻辑
 *
 * Props:
 * - visible: 是否显示工具栏
 * - count: 选中的书签数量
 * - isAllSelected: 是否已全选
 *
 * Events:
 * - delete: 点击删除按钮
 * - copy: 点击复制链接按钮
 * - toggle-select-all: 点击全选/取消全选
 * - dismiss: 点击关闭按钮
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  count: {
    type: Number,
    default: 0
  },
  isAllSelected: {
    type: Boolean,
    default: false
  }
})

defineEmits(['delete', 'copy', 'toggle-select-all', 'dismiss'])
</script>

<style scoped>
/* ---- 工具栏容器 ---- */
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  border-radius: var(--hlaia-radius-lg);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  box-shadow: var(--hlaia-shadow);
  margin-bottom: 16px;
}

/* ---- 滑入/滑出动画 ---- */
.toolbar-slide-enter-active,
.toolbar-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.toolbar-slide-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.toolbar-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---- 左侧选中计数 ---- */
.toolbar-left {
  display: flex;
  align-items: center;
}

.selection-count {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.count-number {
  font-family: 'DM Sans', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--hlaia-primary);
}

.count-label {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}

/* ---- 操作按钮组 ---- */
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: center;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--hlaia-radius);
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--hlaia-text);
  background: var(--hlaia-surface-light);
}

.toolbar-btn:hover {
  background: var(--hlaia-border);
}

.toolbar-btn svg {
  flex-shrink: 0;
}

/* 删除按钮：红色警告色调 */
.btn-delete {
  border-color: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
}

.btn-delete:hover {
  background: rgba(231, 76, 60, 0.08);
  border-color: rgba(231, 76, 60, 0.4);
}

/* 复制按钮 */
.btn-copy {
  border-color: var(--hlaia-border);
}

.btn-copy:hover {
  border-color: var(--hlaia-primary-light);
  color: var(--hlaia-primary);
}

/* 全选按钮 */
.btn-select-all {
  border-color: rgba(74, 127, 199, 0.2);
  color: var(--hlaia-primary);
}

.btn-select-all:hover {
  background: rgba(74, 127, 199, 0.08);
  border-color: var(--hlaia-primary-light);
}

/* ---- 关闭按钮 ---- */
.toolbar-dismiss {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.toolbar-dismiss:hover {
  background: var(--hlaia-border);
  color: var(--hlaia-text);
}
</style>
