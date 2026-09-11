<!--
  NoteDialog.vue — 书签备注编辑对话框（Markdown）

  纯 UI 受控组件：编辑（textarea）/ 预览（Markdown 渲染）切换 + 保存事件派发。
  不调 API、不碰 store，保存组合逻辑归父组件 BookmarkGrid（单一职责约定）。
  受控模式参照 FolderPickerDialog：visible prop + update:visible 事件。
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="t('bookmarks.noteDialog.title')"
    width="520px"
    :append-to-body="true"
    class="note-dialog"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('update:visible', false)"
  >
    <!-- 编辑/预览模式切换 -->
    <div class="note-toolbar">
      <el-radio-group v-model="noteMode" size="small">
        <el-radio-button value="edit">{{ t('bookmarks.noteDialog.edit') }}</el-radio-button>
        <el-radio-button value="preview">{{ t('bookmarks.noteDialog.preview') }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 编辑态：纯 textarea，无工具栏（用户熟悉 Markdown 语法）；
         maxlength 与后端 @Size(max=10000) 对齐，前端先兜一道 -->
    <el-input
      v-if="noteMode === 'edit'"
      v-model="formText"
      type="textarea"
      :rows="10"
      :placeholder="t('bookmarks.noteDialog.placeholder')"
      maxlength="10000"
      class="note-textarea"
    />
    <!-- 预览态：v-html 安全性由 markdown.js 的 html:false 转义保证 -->
    <div v-else class="note-preview" v-html="previewHtml"></div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * NoteDialog 组件 — 书签备注编辑面板
 *
 * 职责：
 * - 编辑/预览切换：编辑态 textarea 直写 Markdown 原文，预览态实时渲染
 * - 受控对话框：显隐完全由父组件 visible prop 决定
 * - 保存按钮 loading 由 saving prop 驱动（父组件的保存请求在途时置 true）
 */
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { renderMarkdown } from '@/utils/markdown'

const { t } = useI18n()

const props = defineProps({
  /** 对话框可见性（受控） */
  visible: Boolean,
  /** 目标书签对象，打开时取其 description 作为编辑初值 */
  bookmark: {
    type: Object,
    default: null
  },
  /** 保存按钮 loading 态（由父组件的保存请求驱动） */
  saving: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'save'])

/** 当前模式：'edit' 编辑 | 'preview' 预览 */
const noteMode = ref('edit')

/** 备注编辑文本（对话框打开时从 bookmark.description 初始化） */
const formText = ref('')

/** 预览内容：基于当前编辑文本实时渲染，切换到预览态即所见即所得 */
const previewHtml = computed(() => renderMarkdown(formText.value))

// 对话框每次打开时初始化：取书签现有备注（null → ''），并回到编辑模式。
// 用 ?? 而非 ||：description 为空串（已清空备注）时保持空串而非误判
watch(
  () => props.visible,
  (val) => {
    if (val) {
      formText.value = props.bookmark?.description ?? ''
      noteMode.value = 'edit'
    }
  }
)

/** 保存：把当前文本交给父组件提交（空串=清空备注，走后端部分更新语义） */
function handleSave() {
  emit('save', formText.value)
}
</script>

<!-- 全局样式：append-to-body 的对话框渲染在 body 层级，scoped 无法命中 -->
<style>
/* ---- 备注对话框（模式沿用 .bookmark-dialog 的 Element Plus 覆盖） ---- */
.note-dialog .el-dialog {
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius-lg);
}

.note-dialog .el-dialog__title {
  font-family: 'DM Sans', sans-serif;
  font-weight: 600;
  color: var(--hlaia-text);
}

.note-dialog .note-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

/* 编辑态 textarea：与 bookmark-dialog 输入框同款底色 */
.note-dialog .note-textarea .el-textarea__inner {
  background: var(--hlaia-surface-light);
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  box-shadow: none;
  font-family: 'DM Sans', Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--hlaia-text);
}

.note-dialog .note-textarea .el-textarea__inner:focus {
  border-color: var(--hlaia-primary);
  box-shadow: 0 0 0 2px rgba(74, 127, 199, 0.12);
}

/* ---- 预览态：Markdown 基础排版 ---- */
.note-dialog .note-preview {
  min-height: 232px; /* 与 rows=10 的编辑态高度接近，切换模式不跳版 */
  max-height: 50vh;
  overflow-y: auto;
  padding: 12px 16px;
  border: 1px solid var(--hlaia-border);
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface-light);
  font-family: 'DM Sans', sans-serif;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--hlaia-text);
}

.note-dialog .note-preview :first-child { margin-top: 0; }
.note-dialog .note-preview :last-child { margin-bottom: 0; }

.note-dialog .note-preview h1,
.note-dialog .note-preview h2,
.note-dialog .note-preview h3,
.note-dialog .note-preview h4 {
  margin: 0.6em 0 0.3em;
  font-weight: 600;
  line-height: 1.4;
  color: var(--hlaia-text);
}

.note-dialog .note-preview h1 { font-size: 18px; }
.note-dialog .note-preview h2 { font-size: 16px; }
.note-dialog .note-preview h3 { font-size: 15px; }
.note-dialog .note-preview h4 { font-size: 14px; }

.note-dialog .note-preview p {
  margin: 0.4em 0;
}

.note-dialog .note-preview ul,
.note-dialog .note-preview ol {
  padding-left: 1.4em;
  margin: 0.4em 0;
}

.note-dialog .note-preview li {
  margin: 0.15em 0;
}

.note-dialog .note-preview a {
  color: var(--hlaia-primary);
  text-decoration: none;
}

.note-dialog .note-preview a:hover {
  text-decoration: underline;
}

.note-dialog .note-preview code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
}

.note-dialog .note-preview pre {
  padding: 10px 12px;
  border-radius: var(--hlaia-radius);
  background: var(--hlaia-surface);
  border: 1px solid var(--hlaia-border);
  overflow-x: auto;
}

.note-dialog .note-preview pre code {
  padding: 0;
  border: none;
  background: transparent;
}

.note-dialog .note-preview blockquote {
  margin: 0.5em 0;
  padding: 2px 12px;
  border-left: 3px solid var(--hlaia-primary-light);
  color: var(--hlaia-text-muted);
}

.note-dialog .note-preview img {
  max-width: 100%;
}

.note-dialog .note-preview hr {
  margin: 0.8em 0;
  border: none;
  border-top: 1px solid var(--hlaia-border);
}

.note-dialog .note-preview strong {
  font-weight: 600;
}

/* 移动端：对话框占满视口宽度（与 bookmark-dialog 同款适配） */
@media (max-width: 768px) {
  .note-dialog .el-dialog {
    width: calc(100vw - 32px) !important;
    margin: 10vh auto 0 !important;
  }
}
</style>
