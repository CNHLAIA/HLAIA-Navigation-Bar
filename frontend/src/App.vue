<!--
  App.vue — 应用根组件

  职责：
  - 承载 <router-view />，所有路由页面渲染在这里
  - 路由切换时的淡入淡出过渡动画
  - 全局样式：暖色极简亮色主题、字体引入、Element Plus 亮色覆盖
-->
<template>
  <!--
    用 <Transition> 包裹路由视图，实现页面切换时的淡入淡出效果。
    mode="out-in" 表示旧页面先淡出、新页面再淡入，避免两个页面同时出现。
  -->
  <router-view v-slot="{ Component }">
    <Transition name="route-fade">
      <component :is="Component" />
    </Transition>
  </router-view>
</template>

<script setup>
/**
 * 根组件无需额外逻辑，路由视图由 vue-router 自动渲染
 */
</script>

<style>
/*
 * 全局样式（非 scoped）
 * 这里写的是全局生效的基础样式，如字体、背景色、基础重置
 */

/* ---- 字体引入 ---- */
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,300;0,9..40,400;0,9..40,500;0,9..40,600;0,9..40,700;1,9..40,400&display=swap');

/* ---- CSS 变量（全局设计 Token） ---- */
:root {
  --hlaia-bg: #FAFAF8;
  --hlaia-surface: #FFFFFF;
  --hlaia-surface-light: #F5F4F0;
  --hlaia-border: #E8E4DF;
  --hlaia-primary: #4A7FC7;
  --hlaia-primary-light: #6B9BD2;
  --hlaia-primary-dark: #3A6BAA;
  --hlaia-text: #2C3E50;
  --hlaia-text-muted: #8B9DAF;
  --hlaia-text-light: #B0BEC5;
  --hlaia-danger: #E74C3C;
  --hlaia-warning: #F5A623;
  --hlaia-success: #27AE60;
  --hlaia-accent: #E8927C;
  --hlaia-shadow: 0 2px 8px rgba(44, 62, 80, 0.08);
  --hlaia-shadow-hover: 0 4px 16px rgba(44, 62, 80, 0.12);
  --hlaia-radius: 8px;
  --hlaia-radius-lg: 12px;
}

/* ---- 基础重置 ---- */
*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif;
  background-color: var(--hlaia-bg);
  color: var(--hlaia-text);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ---- Element Plus 亮色主题全局覆盖 ---- */
.el-dialog {
  border-radius: var(--hlaia-radius-lg) !important;
  box-shadow: var(--hlaia-shadow-hover) !important;
}
.el-dialog__header {
  border-bottom: 1px solid var(--hlaia-border);
  padding: 16px 20px !important;
}
.el-dialog__body {
  padding: 20px !important;
}
.el-dialog__footer {
  border-top: 1px solid var(--hlaia-border);
  padding: 12px 20px !important;
}
.el-button--primary {
  background-color: var(--hlaia-primary) !important;
  border-color: var(--hlaia-primary) !important;
}
.el-button--primary:hover {
  background-color: var(--hlaia-primary-light) !important;
  border-color: var(--hlaia-primary-light) !important;
}
.el-input__wrapper {
  border-radius: var(--hlaia-radius) !important;
  box-shadow: 0 0 0 1px var(--hlaia-border) inset !important;
}
.el-input__wrapper:hover {
  box-shadow: 0 0 0 1px var(--hlaia-primary-light) inset !important;
}
.el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px var(--hlaia-primary) inset !important;
}
.el-message-box {
  border-radius: var(--hlaia-radius-lg) !important;
}

/* Mobile dialog responsive */
@media (max-width: 768px) {
  .el-dialog {
    width: calc(100vw - 32px) !important;
    margin: 10vh auto 0 !important;
  }
  .el-overlay-dialog {
    padding: 0 !important;
  }
}

/* 自定义滚动条（Webkit 浏览器） */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #C4BFB8;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #A39E96;
}

/* placeholder 页面的通用样式（临时占位页） */
.main-placeholder {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--hlaia-text-muted);
  font-family: 'DM Sans', sans-serif;
}

.main-placeholder h1 {
  font-size: 24px;
  font-weight: 600;
  color: var(--hlaia-text);
  margin-bottom: 8px;
}

.main-placeholder p {
  font-size: 14px;
}

/*
 * prefers-reduced-motion 无障碍适配
 * 当用户在操作系统中开启了"减少动画"设置时，禁用所有动画和过渡，
 * 避免对前庭功能障碍（vestibular disorders）用户造成不适。
 * animation-iteration-count: 1 确保动画仍能跳到最终帧（不会卡在中间状态）。
 */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>

<!-- 路由切换过渡动画（scoped，仅作用于根组件内的 Transition） -->
<style scoped>
/* 路由切换淡入淡出：同时进行（去掉 mode="out-in"），减少视觉等待 */
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.15s ease;
}

/* 进入起始状态 / 离开结束状态：完全透明 */
.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
}
</style>
