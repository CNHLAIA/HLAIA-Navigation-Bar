<script setup>
/**
 * 导出页实时预览（仅开发期脚手架）
 *
 * 用 iframe srcdoc 渲染 renderExportHtml 的产物 —— 与最终下载的 .html 文件
 * 完全等价（独立文档，自带 <style>/<script>，互不污染）。
 * 改 exportHtml.js 的结构/CSS → Vite HMR → 这里 html 更新 → iframe 自动刷新。
 *
 * 生产构建时该路由会被守卫排除（见 router/index.js 的 import.meta.env.DEV 判断）。
 */
import { computed } from 'vue'
import { renderExportHtml } from '@/utils/exportHtml'
import { exportMockData } from '@/utils/exportMock'

const html = computed(() => renderExportHtml(exportMockData))
</script>

<template>
  <div class="export-preview-wrap">
    <div class="preview-bar">
      <span class="tag">DEV</span>
      <span class="label">导出页预览 · 所见即最终下载效果</span>
      <span class="hint">编辑 <code>utils/exportHtml.js</code> 或 <code>utils/exportMock.js</code> 后自动刷新</span>
    </div>
    <iframe class="preview-frame" :srcdoc="html" title="export preview"></iframe>
  </div>
</template>

<style scoped>
.export-preview-wrap {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #eef1f5;
}
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: #2c3e50;
  color: #e6edf3;
  font-size: 13px;
}
.preview-bar .tag {
  background: #4A7FC7;
  color: #fff;
  font-weight: 700;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
  letter-spacing: .05em;
}
.preview-bar .label { font-weight: 600; }
.preview-bar .hint { margin-left: auto; color: #8a94a6; font-size: 12px; }
.preview-bar code { background: rgba(255,255,255,.12); padding: 1px 5px; border-radius: 3px; }
.preview-frame {
  flex: 1;
  width: 100%;
  border: 0;
  background: #fff;
}
</style>
