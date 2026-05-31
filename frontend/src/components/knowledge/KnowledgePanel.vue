<template>
  <section class="knowledge-panel">
    <WorkspaceHeader
      kicker="Knowledge"
      title="知识库"
      description="管理可用于 RAG 问答的文档集合和解析状态。"
    >
      <button class="btn-base btn-primary btn-sm" type="button" @click="showCreateModal = true">创建知识库</button>
      <button class="btn-base btn-outline btn-sm" type="button" @click="$emit('close')">返回聊天</button>
    </WorkspaceHeader>

    <div class="knowledge-content" :class="{ 'show-detail': selectedKB }">
      <aside class="knowledge-list custom-scroll">
        <StateBlock
          v-if="loading"
          type="loading"
          title="正在加载知识库"
          message="请稍候，正在同步你的知识库列表。"
        />
        <StateBlock
          v-else-if="listError"
          type="error"
          title="知识库加载失败"
          :message="listError"
          action-text="重新加载"
          @action="loadKnowledgeBases"
        />
        <StateBlock
          v-else-if="knowledgeBases.length === 0"
          type="empty"
          title="暂无知识库"
          message="创建你的第一个知识库，开始构建 AI 知识体系。"
          action-text="创建知识库"
          @action="showCreateModal = true"
        />
        <template v-else>
          <div class="knowledge-list-heading">
            <span>知识库列表</span>
            <small>{{ knowledgeBases.length }} 个</small>
          </div>
          <div class="knowledge-rows">
            <KnowledgeBaseCard
              v-for="knowledgeBase in knowledgeBases"
              :key="knowledgeBase.id"
              :knowledge-base="knowledgeBase"
              :selected="selectedKB?.id === knowledgeBase.id"
              @select="selectKnowledgeBase"
              @edit="editKnowledgeBase"
              @delete="deleteKnowledgeBase"
              @upload-document="openUploadModal"
            />
          </div>
        </template>
      </aside>

      <main class="knowledge-detail custom-scroll">
        <button v-if="selectedKB" class="mobile-back" type="button" @click="selectedKB = null">← 返回知识库列表</button>
        <StateBlock v-if="operationError" type="error" title="操作失败" :message="operationError" />
        <KnowledgeBaseDetail
          v-if="selectedKB"
          :knowledge-base="selectedKB"
          :documents="documents"
          :documents-loading="documentsLoading"
          :documents-error="documentsError"
          @upload-document="openUploadModal"
          @delete-document="deleteDocument"
          @refresh-documents="loadDocuments"
        />
        <div v-else class="knowledge-detail-empty">
          <span class="section-kicker">Knowledge base</span>
          <h2>选择一个知识库</h2>
          <p>查看文档状态、上传资料或处理解析失败的文件。</p>
        </div>
      </main>
    </div>

    <KnowledgeBaseModal
      v-if="showCreateModal || showEditModal"
      :visible="showCreateModal || showEditModal"
      :knowledge-base="editingKB"
      :is-edit="!!editingKB"
      @close="closeModals"
      @save="handleSaveKnowledgeBase"
    />

    <DocumentUploadModal
      v-if="showUploadModal"
      :visible="showUploadModal"
      :knowledge-base-id="uploadTargetKBId"
      @close="showUploadModal = false"
      @uploaded="handleDocumentUploaded"
    />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getApiErrorMessage } from '@/services/apiErrorService.js';
import { knowledgeApiService } from '@/services/knowledgeApi.js';
import WorkspaceHeader from '../common/WorkspaceHeader.vue';
import StateBlock from '../common/StateBlock.vue';
import DocumentUploadModal from '../modal/DocumentUploadModal.vue';
import KnowledgeBaseCard from './KnowledgeBaseCard.vue';
import KnowledgeBaseDetail from './KnowledgeBaseDetail.vue';
import KnowledgeBaseModal from './KnowledgeBaseModal.vue';

defineEmits(['close']);

const loading = ref(true);
const knowledgeBases = ref([]);
const selectedKB = ref(null);
const documents = ref([]);
const documentsLoading = ref(false);
const listError = ref('');
const documentsError = ref('');
const operationError = ref('');
const showCreateModal = ref(false);
const showEditModal = ref(false);
const showUploadModal = ref(false);
const editingKB = ref(null);
const uploadTargetKBId = ref(null);

const loadKnowledgeBases = async () => {
  loading.value = true;
  listError.value = '';
  try {
    const response = await knowledgeApiService.getKnowledgeBases();
    knowledgeBases.value = response.data || [];
  } catch (error) {
    listError.value = getApiErrorMessage(error, '知识库加载失败，请稍后重试');
    knowledgeBases.value = [];
  } finally {
    loading.value = false;
  }
};

const loadDocuments = async (knowledgeBaseId) => {
  documentsLoading.value = true;
  documentsError.value = '';
  try {
    const response = await knowledgeApiService.getDocuments(knowledgeBaseId);
    documents.value = response.data || [];
  } catch (error) {
    documentsError.value = getApiErrorMessage(error, '文档加载失败，请稍后重试');
    documents.value = [];
  } finally {
    documentsLoading.value = false;
  }
};

const selectKnowledgeBase = (knowledgeBase) => {
  selectedKB.value = knowledgeBase;
  loadDocuments(knowledgeBase.id);
};

const editKnowledgeBase = (knowledgeBase) => {
  editingKB.value = { ...knowledgeBase };
  showEditModal.value = true;
};

const deleteKnowledgeBase = async (knowledgeBase) => {
  if (!window.confirm(`确定要删除知识库"${knowledgeBase.name}"吗？此操作不可撤销。`)) return;
  operationError.value = '';
  try {
    await knowledgeApiService.deleteKnowledgeBase(knowledgeBase.id);
    await loadKnowledgeBases();
    if (selectedKB.value?.id === knowledgeBase.id) {
      selectedKB.value = null;
      documents.value = [];
    }
  } catch (error) {
    operationError.value = getApiErrorMessage(error, '知识库删除失败，请稍后重试');
  }
};

const handleSaveKnowledgeBase = async (data) => {
  operationError.value = '';
  try {
    if (editingKB.value) await knowledgeApiService.updateKnowledgeBase(editingKB.value.id, data);
    else await knowledgeApiService.createKnowledgeBase(data);
    await loadKnowledgeBases();
    closeModals();
  } catch (error) {
    operationError.value = getApiErrorMessage(error, '保存失败，请重试');
  }
};

const openUploadModal = (knowledgeBaseId) => {
  uploadTargetKBId.value = knowledgeBaseId;
  showUploadModal.value = true;
};

const handleDocumentUploaded = () => {
  showUploadModal.value = false;
  if (selectedKB.value) loadDocuments(selectedKB.value.id);
};

const deleteDocument = async (document) => {
  if (!window.confirm(`确定要删除文档"${document.fileName}"吗？`)) return;
  try {
    await knowledgeApiService.deleteDocument(document.id);
    if (selectedKB.value) await loadDocuments(selectedKB.value.id);
  } catch (error) {
    documentsError.value = getApiErrorMessage(error, '文档删除失败，请稍后重试');
  }
};

const closeModals = () => {
  showCreateModal.value = false;
  showEditModal.value = false;
  editingKB.value = null;
};

onMounted(loadKnowledgeBases);
</script>

<style src="../../assets/styles/knowledge.css"></style>
