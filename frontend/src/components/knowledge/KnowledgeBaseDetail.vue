<template>
  <div class="knowledge-base-detail">
    <!-- 知识库信息头部 -->
    <div class="detail-header">
      <div class="kb-info">
        <h2 class="kb-title">{{ knowledgeBase.name }}</h2>
        <p class="kb-description">{{ knowledgeBase.description || '暂无描述' }}</p>
        <div class="kb-meta">
          <span class="meta-item">
            <span class="meta-label">文档数量:</span>
            <span class="meta-value">{{ documents.length }} 个</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">创建时间:</span>
            <span class="meta-value">{{ formatDate(knowledgeBase.createdAt) }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">状态:</span>
            <span class="status-badge" :class="getStatusClass(knowledgeBase.status)">
              {{ getStatusText(knowledgeBase.status) }}
            </span>
          </span>
        </div>
      </div>
      <div class="header-actions">
        <button 
          class="btn-base btn-primary btn-sm"
          @click="$emit('uploadDocument', knowledgeBase.id)"
        >
          📄 上传文档
        </button>
        <button 
          class="btn-base btn-outline btn-sm"
          @click="$emit('refreshDocuments', knowledgeBase.id)"
          :disabled="documentsLoading"
        >
          🔄 刷新
        </button>
      </div>
    </div>

    <!-- 文档列表 -->
    <div class="documents-section">
      <div class="section-header">
        <h3 class="section-title">📑 文档列表</h3>
        <div class="document-stats">
          <span v-if="!documentsLoading">共 {{ documents.length }} 个文档</span>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="documentsLoading" class="loading-container">
        <div class="loading"></div>
        <span>加载文档中...</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="documents.length === 0" class="empty-documents">
        <div class="empty-icon">📄</div>
        <h4>暂无文档</h4>
        <p>上传文档开始构建知识库内容</p>
        <button 
          class="btn-base btn-primary"
          @click="$emit('uploadDocument', knowledgeBase.id)"
        >
          上传第一个文档
        </button>
      </div>

      <!-- 文档列表 -->
      <div v-else class="documents-list custom-scroll">
        <div 
          v-for="doc in documents" 
          :key="doc.id"
          class="document-item"
        >
          <div class="doc-icon">
            <span>{{ getFileIcon(doc.fileType) }}</span>
          </div>
          <div class="doc-info">
            <h4 class="doc-title">{{ doc.title || doc.fileName }}</h4>
            <p class="doc-filename">{{ doc.fileName }}</p>
            <div class="doc-meta">
              <span class="meta-tag">{{ doc.fileType.toUpperCase() }}</span>
              <span class="meta-size">{{ formatFileSize(doc.fileSize) }}</span>
              <span class="meta-date">{{ formatDate(doc.createdAt) }}</span>
            </div>
            <div class="doc-status">
              <span class="status-badge" :class="getParseStatusClass(doc.parseStatus)">
                {{ getParseStatusText(doc.parseStatus) }}
              </span>
              <span v-if="doc.isEmbedded" class="status-badge status-success">
                已向量化
              </span>
              <span v-else class="status-badge status-warning">
                未向量化
              </span>
            </div>
          </div>
          <div class="doc-actions">
            <button 
              class="btn-base btn-sm btn-outline"
              @click="viewDocument(doc)"
              title="查看文档"
            >
              👁️
            </button>
            <button 
              class="btn-base btn-sm btn-danger"
              @click="$emit('deleteDocument', doc)"
              title="删除文档"
            >
              🗑️
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'

// Props
const props = defineProps({
  knowledgeBase: {
    type: Object,
    required: true
  },
  documents: {
    type: Array,
    default: () => []
  },
  documentsLoading: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['uploadDocument', 'deleteDocument', 'refreshDocuments'])

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '未知'
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 获取文件图标
const getFileIcon = (fileType) => {
  const icons = {
    'pdf': '📕',
    'doc': '📄',
    'docx': '📄',
    'txt': '📝',
    'md': '📋',
    'ppt': '📊',
    'pptx': '📊',
    'xls': '📊',
    'xlsx': '📊'
  }
  return icons[fileType?.toLowerCase()] || '📄'
}

// 获取状态样式类
const getStatusClass = (status) => {
  const classes = {
    'ACTIVE': 'status-success',
    'INACTIVE': 'status-warning',
    'DELETED': 'status-danger'
  }
  return classes[status] || 'status-info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'ACTIVE': '活跃',
    'INACTIVE': '未激活',
    'DELETED': '已删除'
  }
  return texts[status] || status || '未知'
}

// 获取解析状态样式类
const getParseStatusClass = (status) => {
  const classes = {
    'PENDING': 'status-warning',
    'PARSING': 'status-info',
    'PARSED': 'status-success',
    'FAILED': 'status-danger'
  }
  return classes[status] || 'status-info'
}

// 获取解析状态文本
const getParseStatusText = (status) => {
  const texts = {
    'PENDING': '待解析',
    'PARSING': '解析中',
    'PARSED': '已解析',
    'FAILED': '解析失败'
  }
  return texts[status] || status || '未知'
}

// 查看文档
const viewDocument = (doc) => {
  // TODO: 实现文档查看功能
  console.log('查看文档:', doc)
  alert(`查看文档功能待实现\n文档: ${doc.fileName}`)
}
</script>

<style scoped>
.knowledge-base-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-lg);
  padding: var(--spacing-xl);
  background: var(--bg-secondary);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--border-light);
}

.kb-info {
  flex: 1;
}

.kb-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--spacing-sm) 0;
}

.kb-description {
  color: var(--text-secondary);
  margin: 0 0 var(--spacing-md) 0;
  line-height: 1.5;
}

.kb-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
}

.meta-label {
  color: var(--text-secondary);
}

.meta-value {
  color: var(--text-primary);
  font-weight: 500;
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.documents-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.document-stats {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl);
  color: var(--text-secondary);
}

.empty-documents {
  text-align: center;
  padding: var(--spacing-2xl);
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-lg);
}

.empty-documents h4 {
  margin: 0 0 var(--spacing-md) 0;
  color: var(--text-primary);
}

.empty-documents p {
  margin: 0 0 var(--spacing-xl) 0;
  line-height: 1.5;
}

.documents-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.document-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-md);
  transition: all var(--transition-fast);
}

.document-item:hover {
  border-color: var(--border-focus);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.doc-icon {
  font-size: 24px;
  margin-top: var(--spacing-xs);
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--spacing-xs) 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-filename {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0 0 var(--spacing-sm) 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.meta-tag {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 500;
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  text-transform: uppercase;
}

.meta-size,
.meta-date {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.doc-status {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}

.status-badge {
  font-size: var(--font-size-xs);
  font-weight: 500;
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
  text-transform: uppercase;
}

.status-success {
  background: var(--success-bg);
  color: var(--success-text);
}

.status-warning {
  background: var(--warning-bg);
  color: var(--warning-text);
}

.status-danger {
  background: var(--danger-bg);
  color: var(--danger-text);
}

.status-info {
  background: var(--info-bg);
  color: var(--info-text);
}

.doc-actions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.doc-actions .btn-base {
  padding: var(--spacing-xs);
  min-width: 32px;
  height: 32px;
  border-radius: var(--border-radius-sm);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .header-actions {
    flex-direction: row;
    justify-content: flex-start;
  }
  
  .kb-meta {
    flex-direction: column;
    gap: var(--spacing-xs);
  }
  
  .document-item {
    flex-direction: column;
    align-items: stretch;
  }
  
  .doc-actions {
    flex-direction: row;
    justify-content: flex-end;
  }
}
</style>