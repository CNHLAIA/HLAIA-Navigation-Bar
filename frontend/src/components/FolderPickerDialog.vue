<!--
  FolderPickerDialog.vue — 文件夹选择器弹窗
  展示用户文件夹树，支持选中目标文件夹，可排除指定文件夹。
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="400px"
    :append-to-body="true"
    class="bookmark-dialog"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('update:visible', false)"
  >
    <p class="move-dialog-hint">{{ t('bookmarks.moveDialog.description') }}</p>
    <div v-if="flatFolders.length > 0" class="folder-picker">
      <div
        v-for="node in flatFolders"
        :key="node.id"
        class="folder-picker-item"
        :class="{ 'is-selected': selectedId === node.id }"
        :style="{ paddingLeft: `${12 + node.depth * 20}px` }"
        @click="selectedId = node.id"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M1.75 4.083V11.083a1.167 1.167 0 0 0 1.167 1.167H11.083a1.167 1.167 0 0 0 1.167-1.167V5.833a1.167 1.167 0 0 0-1.167-1.167H7L5.833 3.25H2.917A1.167 1.167 0 0 0 1.75 4.417" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>{{ node.name }}</span>
      </div>
    </div>
    <div v-else class="folder-picker-empty">
      <p>{{ t('bookmarks.moveDialog.empty') }}</p>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :disabled="!selectedId"
        @click="handleConfirm"
      >
        {{ t('bookmarks.moveDialog.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'

const { t } = useI18n()
const folderStore = useFolderStore()

const props = defineProps({
  visible: Boolean,
  title: {
    type: String,
    default: ''
  },
  excludeFolderId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const selectedId = ref(null)

const flatFolders = computed(() => {
  return flattenTree(folderStore.folderTree || []).filter(n => n.id !== props.excludeFolderId)
})

watch(() => props.visible, (val) => {
  if (val) selectedId.value = null
})

function handleConfirm() {
  if (selectedId.value) {
    emit('confirm', selectedId.value)
  }
}

function flattenTree(nodes, depth = 0) {
  const result = []
  for (const node of nodes) {
    result.push({ id: node.id, name: node.name, depth })
    if (node.children && node.children.length > 0) {
      result.push(...flattenTree(node.children, depth + 1))
    }
  }
  return result
}
</script>

<style scoped>
.move-dialog-hint {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
  margin: 0 0 12px;
}

.folder-picker {
  max-height: 300px;
  overflow-y: auto;
  border-radius: var(--hlaia-radius);
  border: 1px solid var(--hlaia-border);
  background: var(--hlaia-surface);
}

.folder-picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--hlaia-text-muted);
  border-bottom: 1px solid var(--hlaia-border);
}

.folder-picker-item:last-child {
  border-bottom: none;
}

.folder-picker-item:hover {
  background: var(--hlaia-surface-light);
  color: var(--hlaia-text);
}

.folder-picker-item.is-selected {
  background: rgba(74, 127, 199, 0.08);
  color: var(--hlaia-primary);
}

.folder-picker-item span {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.folder-picker-empty {
  padding: 24px;
  text-align: center;
}

.folder-picker-empty p {
  font-family: 'DM Sans', sans-serif;
  font-size: 13px;
  color: var(--hlaia-text-muted);
}
</style>
