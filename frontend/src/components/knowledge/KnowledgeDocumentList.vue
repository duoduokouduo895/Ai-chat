<template>
  <section class="documents-section">
    <header class="section-header">
      <div>
        <span class="section-kicker">Documents</span>
        <h3>文档列表</h3>
      </div>
      <span v-if="!loading" class="document-count">{{ documents.length }} 个文档</span>
    </header>

    <StateBlock
      v-if="error"
      type="error"
      title="文档加载失败"
      :message="error"
      action-text="重新加载"
      @action="$emit('refresh')"
    />
    <StateBlock
      v-else-if="loading"
      type="loading"
      title="正在加载文档"
      message="请稍候，正在同步当前知识库的文档列表。"
    />
    <StateBlock
      v-else-if="documents.length === 0"
      type="empty"
      title="暂无文档"
      message="上传文档开始构建知识库内容。"
      action-text="上传第一个文档"
      @action="$emit('upload')"
    />

    <div v-else class="documents-list custom-scroll">
      <article v-for="doc in documents" :key="doc.id" class="document-row">
        <div class="file-type">{{ getFileIcon(doc.fileType) }}</div>
        <div class="document-info">
          <h4>{{ doc.title || doc.fileName }}</h4>
          <p>{{ doc.fileName }}</p>
          <div class="document-meta">
            <span>{{ formatFileSize(doc.fileSize) }}</span>
            <span>{{ formatDateTime(doc.createdAt) }}</span>
            <span class="status-badge" :class="getParseStatus(doc.parseStatus).className">
              {{ getParseStatus(doc.parseStatus).label }}
            </span>
            <span class="status-badge" :class="doc.isEmbedded ? 'status-success' : 'status-warning'">
              {{ doc.isEmbedded ? '已向量化' : '未向量化' }}
            </span>
          </div>
        </div>
        <div class="document-actions">
          <button type="button" aria-label="查看文档" @click="$emit('view', doc)">查看</button>
          <button type="button" aria-label="下载文档" @click="$emit('download', doc)">下载</button>
          <button type="button" class="danger" aria-label="删除文档" @click="$emit('delete', doc)">删除</button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import StateBlock from '../common/StateBlock.vue';
import { formatDateTime, formatFileSize, getFileIcon, getParseStatus } from '@/utils/knowledgeDisplay.js';

defineProps({
  documents: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
});

defineEmits(['upload', 'refresh', 'view', 'download', 'delete']);
</script>
