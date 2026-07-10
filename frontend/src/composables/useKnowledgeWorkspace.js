import { ref } from 'vue';
import { getApiErrorMessage } from '@/services/apiErrorService.js';
import { knowledgeApiService } from '@/services/knowledgeApi.js';

export function useKnowledgeWorkspace() {
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

  return {
    closeModals,
    deleteDocument,
    deleteKnowledgeBase,
    documents,
    documentsError,
    documentsLoading,
    editKnowledgeBase,
    editingKB,
    handleDocumentUploaded,
    handleSaveKnowledgeBase,
    knowledgeBases,
    listError,
    loadDocuments,
    loadKnowledgeBases,
    loading,
    openUploadModal,
    operationError,
    selectedKB,
    selectKnowledgeBase,
    showCreateModal,
    showEditModal,
    showUploadModal,
    uploadTargetKBId
  };
}
