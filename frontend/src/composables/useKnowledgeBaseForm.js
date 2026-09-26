import { computed, reactive, ref, watch } from 'vue';
import { notify } from '@/services/feedback.mjs';
import { getApiErrorMessage } from '@/services/apiErrorService.js';

const defaultFormData = () => ({
  name: '',
  description: '',
  isPublic: false,
  tags: [],
  status: 'ACTIVE',
  autoUpdate: true,
  enableRAG: true
});

const toFormData = (knowledgeBase) => ({
  name: knowledgeBase?.name || '',
  description: knowledgeBase?.description || '',
  isPublic: knowledgeBase?.isPublic || false,
  tags: Array.isArray(knowledgeBase?.tags) ? [...knowledgeBase.tags] : [],
  status: knowledgeBase?.status || 'ACTIVE',
  autoUpdate: knowledgeBase?.autoUpdate !== false,
  enableRAG: knowledgeBase?.enableRAG !== false
});

export function useKnowledgeBaseForm(props, emit) {
  const isSubmitting = ref(false);
  const newTag = ref('');
  const formData = reactive(defaultFormData());

  const isFormValid = computed(() => (
    formData.name.trim().length > 0 && formData.name.trim().length <= 100
  ));

  watch(() => props.knowledgeBase, (newKB) => {
    if (newKB && props.isEdit) {
      Object.assign(formData, toFormData(newKB));
      return;
    }

    if (!props.isEdit) {
      Object.assign(formData, defaultFormData());
    }
  }, { immediate: true });

  const addTag = () => {
    const tag = newTag.value.trim();
    if (tag && !formData.tags.includes(tag) && formData.tags.length < 10) {
      formData.tags.push(tag);
      newTag.value = '';
    }
  };

  const removeTag = (index) => {
    formData.tags.splice(index, 1);
  };

  const handleSubmit = async () => {
    if (!isFormValid.value || isSubmitting.value) {
      return;
    }

    try {
      isSubmitting.value = true;
      emit('save', {
        name: formData.name.trim(),
        description: formData.description.trim() || null,
        isPublic: formData.isPublic,
        tags: formData.tags,
        status: formData.status,
        autoUpdate: formData.autoUpdate,
        enableRAG: formData.enableRAG
      });
    } catch (error) {
      notify.error(getApiErrorMessage(error, '表单提交失败，请稍后重试'));
    } finally {
      isSubmitting.value = false;
    }
  };

  return {
    addTag,
    formData,
    handleSubmit,
    isFormValid,
    isSubmitting,
    newTag,
    removeTag
  };
}
