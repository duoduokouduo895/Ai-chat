<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <section
      ref="dialogRef"
      class="modal active document-viewer"
      role="dialog"
      aria-modal="true"
      aria-labelledby="document-viewer-title"
      tabindex="-1"
    >
      <header class="modal-header">
        <div>
          <span class="section-kicker">Document preview</span>
          <h2 id="document-viewer-title" class="modal-title">{{ document.fileName }}</h2>
        </div>
        <button ref="closeButtonRef" class="modal-close" type="button" aria-label="关闭文档预览" @click="$emit('close')">×</button>
      </header>

      <div class="modal-body">
        <div class="preview-meta">
          <span>{{ formatFileSize(document.fileSize) }}</span>
          <span class="status-badge" :class="getParseStatus(document.parseStatus).className">
            {{ getParseStatus(document.parseStatus).label }}
          </span>
          <button type="button" class="text-action" @click="$emit('download', document)">下载原文件</button>
        </div>
        <StateBlock v-if="loading" type="loading" title="正在加载文档" message="请稍候，正在读取文档内容。" />
        <pre v-else class="document-preview-content">{{ content }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, toRef } from 'vue';
import { useModalDialog } from '@/composables/useModalDialog.js';
import { formatFileSize, getParseStatus } from '@/utils/knowledgeDisplay.js';
import StateBlock from '../common/StateBlock.vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  document: { type: Object, required: true },
  content: { type: String, default: '' },
  loading: { type: Boolean, default: false }
});

const emit = defineEmits(['close', 'download']);
const dialogRef = ref(null);
const closeButtonRef = ref(null);

useModalDialog({
  isOpen: toRef(props, 'visible'),
  dialogRef,
  initialFocusRef: closeButtonRef,
  onClose: () => emit('close')
});
</script>
