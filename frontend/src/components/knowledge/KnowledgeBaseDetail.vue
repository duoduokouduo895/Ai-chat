<template>
  <section class="knowledge-base-detail">
    <header class="detail-header">
      <div>
        <span class="section-kicker">Knowledge base</span>
        <h2>{{ knowledgeBase.name }}</h2>
        <p>{{ knowledgeBase.description || '暂无描述' }}</p>
        <div class="knowledge-meta">
          <span>{{ documents.length }} 个文档</span>
          <span>{{ formatDateTime(knowledgeBase.createdAt) }}</span>
          <span class="status-badge" :class="status.className">{{ status.label }}</span>
        </div>
      </div>
      <div class="detail-actions">
        <button class="btn-base btn-primary btn-sm" type="button" @click="$emit('upload-document', knowledgeBase.id)">上传文档</button>
        <button class="btn-base btn-outline btn-sm" type="button" :disabled="documentsLoading" @click="$emit('refresh-documents', knowledgeBase.id)">刷新</button>
      </div>
    </header>

    <StateBlock
      v-if="localDocumentError"
      type="error"
      title="文档操作失败"
      :message="localDocumentError"
      action-text="关闭"
      @action="localDocumentError = ''"
    />

    <KnowledgeDocumentList
      :documents="documents"
      :loading="documentsLoading"
      :error="documentsError"
      @upload="$emit('upload-document', knowledgeBase.id)"
      @refresh="$emit('refresh-documents', knowledgeBase.id)"
      @view="viewDocument"
      @download="downloadDocument"
      @delete="$emit('delete-document', $event)"
    />

    <DocumentViewerModal
      v-if="currentViewDocument"
      :visible="!!currentViewDocument"
      :document="currentViewDocument"
      :content="documentContent"
      :loading="loadingDocumentContent"
      @close="closeDocumentView"
      @download="downloadDocument"
    />
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import knowledgeApiService from '@/services/knowledgeApi';
import { formatDateTime, getKnowledgeStatus } from '@/utils/knowledgeDisplay.js';
import StateBlock from '../common/StateBlock.vue';
import DocumentViewerModal from './DocumentViewerModal.vue';
import KnowledgeDocumentList from './KnowledgeDocumentList.vue';

const props = defineProps({
  knowledgeBase: { type: Object, required: true },
  documents: { type: Array, default: () => [] },
  documentsLoading: { type: Boolean, default: false },
  documentsError: { type: String, default: '' }
});

defineEmits(['upload-document', 'delete-document', 'refresh-documents']);

const status = computed(() => getKnowledgeStatus(props.knowledgeBase.status));
const currentViewDocument = ref(null);
const documentContent = ref('');
const loadingDocumentContent = ref(false);
const localDocumentError = ref('');

const viewDocument = async (document) => {
  currentViewDocument.value = document;
  loadingDocumentContent.value = true;
  documentContent.value = '';
  try {
    const response = await knowledgeApiService.getDocument(document.id);
    documentContent.value = response?.data?.content || '文档内容为空或尚未解析完成';
  } catch {
    documentContent.value = '加载文档内容失败，请稍后重试';
  } finally {
    loadingDocumentContent.value = false;
  }
};

const downloadDocument = (document) => {
  if (!document.ossUrl) {
    localDocumentError.value = '文档下载链接不可用';
    return;
  }
  const link = window.document.createElement('a');
  link.href = document.ossUrl;
  link.download = document.fileName;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.click();
};

const closeDocumentView = () => {
  currentViewDocument.value = null;
  documentContent.value = '';
};
</script>
