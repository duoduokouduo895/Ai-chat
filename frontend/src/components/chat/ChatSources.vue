<template>
  <section class="chat-sources" aria-label="本轮参考资料">
    <p v-if="retrieval.status === 'DEGRADED'" class="retrieval-note" role="status">部分检索通道暂不可用，本轮使用已取得的资料。</p>
    <p v-if="retrieval.status === 'EMPTY'" class="retrieval-note">资料不足：本轮未找到可用的知识库内容。</p>
    <details v-if="retrieval.sources?.length">
      <summary>本轮参考资料 · {{ retrieval.sources.length }}</summary>
      <p class="source-caption">这些资料参与了本轮回答，不代表逐句引用已核验。</p>
      <ul>
        <li v-for="source in retrieval.sources" :key="`${source.documentId}:${source.chunkId}`">
          <template v-if="source.available">
            <span>{{ source.documentTitle || '未命名文档' }}<small>{{ source.knowledgeBaseName }}</small></span>
            <button type="button" @click="openDocument(source)">查看当前文档</button>
          </template>
          <span v-else class="source-unavailable">来源不可用<span class="source-caption"> · 已移除或当前无权访问</span></span>
        </li>
      </ul>
    </details>
    <p v-if="error" class="retrieval-note" role="alert">{{ error }}</p>
    <DocumentViewerModal v-if="document" :visible="true" :document="document" :content="content"
      :loading="loading" @close="closeDocument" @download="downloadDocument" />
  </section>
</template>

<script setup>
import { ref } from 'vue';
import DocumentViewerModal from '../knowledge/DocumentViewerModal.vue';
import { knowledgeApiService } from '@/services/knowledgeApi.js';

defineProps({ retrieval: { type: Object, required: true } });
const document = ref(null);
const content = ref('');
const loading = ref(false);
const error = ref('');
let requestSequence = 0;

const closeDocument = () => { requestSequence += 1; document.value = null; loading.value = false; };
const openDocument = async (source) => {
  const sequence = ++requestSequence;
  error.value = '';
  document.value = { id: source.documentId, fileName: source.documentTitle, fileSize: 0 };
  content.value = '';
  loading.value = true;
  try {
    const response = await knowledgeApiService.getDocument(source.documentId);
    if (sequence !== requestSequence) return;
    document.value = response.data;
    content.value = response.data?.content || '文档内容为空或尚未解析完成。';
  } catch {
    if (sequence !== requestSequence) return;
    document.value = null;
    error.value = '来源暂不可用，文档可能已移除或访问权限已变更。';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
};
const downloadDocument = (value) => {
  if (!value.ossUrl) { error.value = '原文件暂不可用。'; return; }
  const link = window.document.createElement('a');
  link.href = value.ossUrl;
  link.download = value.fileName;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.click();
};
</script>

<style scoped>
.chat-sources { border-top: 1px solid var(--border-subtle); margin-top: 12px; padding-top: 8px; font-size: var(--font-size-xs); color: var(--text-secondary); }
summary { cursor: pointer; padding: 4px 0; color: var(--text-primary); }
.source-caption, small { color: var(--text-muted); }
.source-caption, .retrieval-note { margin: 4px 0 8px; }
ul { padding: 0; margin: 8px 0 0; list-style: none; }
li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 6px 0; overflow-wrap: anywhere; }
small { display: block; }
button { background: transparent; border: 1px solid var(--border-subtle); border-radius: 6px; padding: 6px 8px; color: var(--accent-color); cursor: pointer; flex-shrink: 0; font: inherit; min-height: 36px; }
button:hover { background: var(--accent-soft); }
button:focus-visible, summary:focus-visible { outline: 2px solid var(--accent-color); outline-offset: 3px; }
@media (max-width: 480px) { li { align-items: flex-start; flex-direction: column; gap: 4px; } button { min-height: 44px; } }
</style>
