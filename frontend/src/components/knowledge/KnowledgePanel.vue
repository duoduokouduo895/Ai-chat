<template>
  <div class="knowledge-panel">
    <!-- 头部 -->
    <header class="knowledge-header">
      <h1 class="panel-title">📚 知识库管理</h1>
      <div class="header-actions">
        <button 
          class="btn-base btn-success btn-sm"
          @click="showCreateModal = true"
        >
          + 创建知识库
        </button>
        <button 
          class="btn-base btn-outline btn-sm"
          @click="$emit('close')"
        >
          返回聊天
        </button>
      </div>
    </header>

    <!-- 知识库列表 -->
    <div class="knowledge-content">
      <div class="knowledge-list custom-scroll">
        <div v-if="loading" class="loading-container">
          <div class="loading"></div>
          <span>加载知识库中...</span>
        </div>
        
        <div v-else-if="knowledgeBases.length === 0" class="empty-state">
          <div class="empty-icon">📚</div>
          <h3>暂无知识库</h3>
          <p>创建您的第一个知识库，开始构建AI知识体系</p>
          <button 
            class="btn-base btn-primary"
            @click="showCreateModal = true"
          >
            创建知识库
          </button>
        </div>
        
        <div v-else class="kb-grid">
          <KnowledgeBaseCard
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :knowledgeBase="kb"
            @select="selectKnowledgeBase"
            @edit="editKnowledgeBase"
            @delete="deleteKnowledgeBase"
            @uploadDocument="openUploadModal"
          />
        </div>
      </div>

      <!-- 知识库详情面板 -->
      <div v-if="selectedKB" class="knowledge-detail">
        <KnowledgeBaseDetail
          :knowledgeBase="selectedKB"
          :documents="documents"
          :documentsLoading="documentsLoading"
          @uploadDocument="openUploadModal"
          @deleteDocument="deleteDocument"
          @refreshDocuments="loadDocuments"
        />
      </div>
    </div>

    <!-- 创建/编辑知识库模态框 -->
    <KnowledgeBaseModal
      v-if="showCreateModal || showEditModal"
      :visible="showCreateModal || showEditModal"
      :knowledgeBase="editingKB"
      :isEdit="!!editingKB"
      @close="closeModals"
      @save="handleSaveKnowledgeBase"
    />

    <!-- 文档上传模态框 -->
    <DocumentUploadModal
      v-if="showUploadModal"
      :visible="showUploadModal"
      :knowledgeBaseId="uploadTargetKBId"
      @close="showUploadModal = false"
      @uploaded="handleDocumentUploaded"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import KnowledgeBaseCard from './KnowledgeBaseCard.vue'
import KnowledgeBaseDetail from './KnowledgeBaseDetail.vue'
import KnowledgeBaseModal from './KnowledgeBaseModal.vue'
import DocumentUploadModal from '../modal/DocumentUploadModal.vue'
import { knowledgeApiService } from '@/services/knowledgeApi.js'

// Emits
const emit = defineEmits(['close'])

// 状态管理
const loading = ref(true)
const knowledgeBases = ref([])
const selectedKB = ref(null)
const documents = ref([])
const documentsLoading = ref(false)

// 模态框状态
const showCreateModal = ref(false)
const showEditModal = ref(false)
const showUploadModal = ref(false)
const editingKB = ref(null)
const uploadTargetKBId = ref(null)

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    loading.value = true
    const response = await knowledgeApiService.getKnowledgeBases()
    knowledgeBases.value = response.data || []
  } catch (error) {
    const errorMsg = error.response?.data?.message || error.message || '文档删除失败，请稍后重试'
    console.error('加载知识库失败:', error)
    // 这里可以添加错误提示
  } finally {
    loading.value = false
  }
}

// 选择知识库
const selectKnowledgeBase = (kb) => {
  selectedKB.value = kb
  loadDocuments(kb.id)
}

// 加载文档列表
const loadDocuments = async (kbId) => {
  try {
    documentsLoading.value = true
    const response = await knowledgeApiService.getDocuments(kbId)
    documents.value = response.data || []
  } catch (error) {
    console.error('加载文档失败:', error)
  } finally {
    documentsLoading.value = false
  }
}

// 编辑知识库
const editKnowledgeBase = (kb) => {
  editingKB.value = { ...kb }
  showEditModal.value = true
}

// 删除知识库
const deleteKnowledgeBase = async (kb) => {
  if (!confirm(`确定要删除知识库"${kb.name}"吗？此操作不可撤销。`)) {
    return
  }
  
  try {
    await knowledgeApiService.deleteKnowledgeBase(kb.id)
    await loadKnowledgeBases()
    if (selectedKB.value?.id === kb.id) {
      selectedKB.value = null
      documents.value = []
    }
  } catch (error) {
    console.error('删除知识库失败:', error)
  }
}

// 保存知识库
const handleSaveKnowledgeBase = async (kbData) => {
  try {
    if (editingKB.value) {
      await knowledgeApiService.updateKnowledgeBase(editingKB.value.id, kbData)
    } else {
      await knowledgeApiService.createKnowledgeBase(kbData)
    }
    await loadKnowledgeBases()
    closeModals()
  } catch (error) {
    console.error('保存知识库失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '保存失败，请重试'
    alert(errorMsg)
  }
}

// 打开上传模态框
const openUploadModal = (kbId) => {
  uploadTargetKBId.value = kbId
  showUploadModal.value = true
}

// 文档上传完成处理
const handleDocumentUploaded = () => {
  showUploadModal.value = false
  if (selectedKB.value) {
    loadDocuments(selectedKB.value.id)
  }
}

// 删除文档
const deleteDocument = async (doc) => {
  if (!confirm(`确定要删除文档"${doc.fileName}"吗？`)) {
    return
  }
  
  try {
    await knowledgeApiService.deleteDocument(doc.id)
    documents.value = documents.value.filter((item) => item.id !== doc.id)
    alert('文档删除成功')
    if (selectedKB.value) {
      loadDocuments(selectedKB.value.id)
    }
  } catch (error) {
    console.error('删除文档失败:', error)
  }
}

// 关闭模态框
const closeModals = () => {
  showCreateModal.value = false
  showEditModal.value = false
  editingKB.value = null
}

// 组件挂载时加载数据
onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped>
.knowledge-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-primary);
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-xl);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.panel-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: var(--spacing-md);
}

.knowledge-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.knowledge-list {
  width: 400px;
  padding: var(--spacing-xl);
  border-right: 1px solid var(--border-light);
  overflow-y: auto;
}

.knowledge-detail {
  flex: 1;
  padding: var(--spacing-xl);
  overflow-y: auto;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl);
  color: var(--text-secondary);
}

.empty-state {
  text-align: center;
  padding: var(--spacing-2xl);
  color: var(--text-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-lg);
}

.empty-state h3 {
  margin: 0 0 var(--spacing-md) 0;
  color: var(--text-primary);
}

.empty-state p {
  margin: 0 0 var(--spacing-xl) 0;
  line-height: 1.5;
}

.kb-grid {
  display: grid;
  gap: var(--spacing-lg);
  grid-template-columns: 1fr;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .knowledge-content {
    flex-direction: column;
  }
  
  .knowledge-list {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border-light);
    max-height: 50vh;
  }
  
  .knowledge-detail {
    padding: var(--spacing-md);
  }
  
  .knowledge-header {
    padding: var(--spacing-md);
  }
  
  .header-actions {
    flex-direction: column;
    gap: var(--spacing-sm);
  }
}
</style>
