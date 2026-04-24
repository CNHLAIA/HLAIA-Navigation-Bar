<!--
  FolderBreadcrumb.vue -- 文件夹面包屑导航

  功能：
  - 显示从根目录到当前文件夹的完整路径
  - 每个路径段可点击，点击后导航到对应文件夹
  - 使用 ">" 作为分隔符

  设计：
  - Warm Minimal Light 主题
  - 当前位置用主题蓝色，可点击的段用淡色文字 + 悬停效果
  - 分隔符使用 muted 色
-->
<template>
  <nav v-if="path.length > 0" class="folder-breadcrumb">
    <!-- 根目录项 -->
    <button class="breadcrumb-item breadcrumb-root" @click="$emit('navigate', null)">
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path d="M1.75 5.833L7 1.75l5.25 4.083v5.834a1.167 1.167 0 0 1-1.167 1.166H2.917a1.167 1.167 0 0 1-1.167-1.166V5.833z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>

    <!-- 路径段 -->
    <template v-for="(segment, index) in path" :key="segment.id">
      <!-- 分隔符 -->
      <span class="breadcrumb-separator">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path d="M4.5 2.5L8 6l-3.5 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>

      <!-- 路径段按钮 -->
      <button
        class="breadcrumb-item"
        :class="{ 'is-current': index === path.length - 1 }"
        @click="handleNavigate(segment)"
      >
        {{ segment.name }}
      </button>
    </template>
  </nav>
</template>

<script setup>
/**
 * FolderBreadcrumb -- 面包屑导航组件
 *
 * Props:
 * - path: 路径数组，由 Folder Store 的 folderPath getter 提供
 *   格式：[{ id: 1, name: '开发' }, { id: 5, name: '前端' }]
 *
 * Events:
 * - navigate: 点击某个路径段时触发，参数为文件夹 ID（null 表示根目录）
 */
import { useI18n } from 'vue-i18n'

useI18n()

defineProps({
  path: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['navigate'])

/**
 * 导航到某个路径段
 * @param {Object} segment - { id, name }
 */
function handleNavigate(segment) {
  emit('navigate', segment.id)
}
</script>

<style scoped>
/* ---- 面包屑容器 ---- */
.folder-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 0;
  font-family: 'DM Sans', sans-serif;
  flex-wrap: wrap;
}

/* ---- 单个路径段 ---- */
.breadcrumb-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  border: none;
  background: transparent;
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--hlaia-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.breadcrumb-item:hover {
  color: var(--hlaia-text);
  background: var(--hlaia-surface-light);
}

/* 当前位置：主题蓝色，不可点击的感觉 */
.breadcrumb-item.is-current {
  color: var(--hlaia-primary);
  cursor: default;
  background: none;
  font-weight: 600;
}

.breadcrumb-item.is-current:hover {
  color: var(--hlaia-primary);
  background: none;
}

/* 根目录图标 */
.breadcrumb-root {
  padding: 4px 6px;
}

/* ---- 分隔符 ---- */
.breadcrumb-separator {
  display: flex;
  align-items: center;
  color: var(--hlaia-text-muted);
  opacity: 0.4;
}
</style>
