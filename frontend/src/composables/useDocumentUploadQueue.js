import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index';
import { knowledgeApiService } from '@/services/knowledgeApi.js';
import { getApiErrorMessage } from '@/services/apiErrorService.js';

const SAFE_DEFAULT_CAPABILITIES = Object.freeze({
  maxFileBytes: 10 * 1024 * 1024,
  maxRequestBytes: 15 * 1024 * 1024,
  maxBatchFiles: 5,
  supportedExtensions: ['pdf', 'docx', 'doc', 'txt', 'md', 'pptx', 'xlsx']
});

const normalizeCapabilities = (value) => {
  const maxFileBytes = Number(value?.maxFileBytes);
  const maxRequestBytes = Number(value?.maxRequestBytes);
  const maxBatchFiles = Number(value?.maxBatchFiles);
  const supportedExtensions = Array.isArray(value?.supportedExtensions)
    ? value.supportedExtensions
      .filter((extension) => typeof extension === 'string' && /^[a-z0-9]{1,20}$/i.test(extension))
      .map((extension) => extension.toLowerCase())
    : [];

  return {
    maxFileBytes: Number.isFinite(maxFileBytes) && maxFileBytes > 0
      ? maxFileBytes
      : SAFE_DEFAULT_CAPABILITIES.maxFileBytes,
    maxRequestBytes: Number.isFinite(maxRequestBytes) && maxRequestBytes > 0
      ? maxRequestBytes
      : SAFE_DEFAULT_CAPABILITIES.maxRequestBytes,
    maxBatchFiles: Number.isInteger(maxBatchFiles) && maxBatchFiles > 0
      ? maxBatchFiles
      : SAFE_DEFAULT_CAPABILITIES.maxBatchFiles,
    supportedExtensions: supportedExtensions.length > 0
      ? [...new Set(supportedExtensions)]
      : SAFE_DEFAULT_CAPABILITIES.supportedExtensions
  };
};

const fileExtension = (fileName) => {
  const ext = fileName.toLowerCase().split('.').pop();
  return ext || '';
};

export const getFileIcon = (fileName) => {
  const ext = fileName.toLowerCase().split('.').pop();
  return ext ? ext.toUpperCase() : 'DOC';
};

export const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

export const getProgressStatusText = (status) => {
  const statusMap = {
    uploading: '上传中',
    processing: '解析中',
    vectorizing: '向量化中',
    success: '成功',
    error: '失败'
  };
  return statusMap[status] || '未知';
};

export function useDocumentUploadQueue(props, emit) {
  const fileInput = ref(null);
  const selectedFiles = ref([]);
  const isDragOver = ref(false);
  const isUploading = ref(false);
  const parseMode = ref('auto');
  const enableVectorization = ref(true);
  const uploadProgress = ref([]);
  const uploadCapabilities = ref(SAFE_DEFAULT_CAPABILITIES);
  const isCapabilitiesLoading = ref(false);

  const acceptAttribute = computed(() => uploadCapabilities.value.supportedExtensions
    .map((extension) => `.${extension}`)
    .join(','));
  const supportedExtensionsLabel = computed(() => uploadCapabilities.value.supportedExtensions
    .map((extension) => extension.toUpperCase())
    .join(', '));
  const fileLimitLabel = computed(() => (
    `最大支持 ${formatFileSize(uploadCapabilities.value.maxFileBytes)}，单次最多上传 ${uploadCapabilities.value.maxBatchFiles} 个文件`
  ));

  const isAllowedFile = (file, capabilities = uploadCapabilities.value) => {
    return capabilities.supportedExtensions.includes(fileExtension(file.name));
  };

  const reconcileSelectedFiles = () => {
    const capabilities = uploadCapabilities.value;
    const retained = selectedFiles.value
      .filter((file) => isAllowedFile(file, capabilities) && file.size <= capabilities.maxFileBytes)
      .slice(0, capabilities.maxBatchFiles);
    if (retained.length !== selectedFiles.value.length) {
      selectedFiles.value = retained;
      ElMessage.warning('上传限制已更新，不符合新限制的文件已移除');
    }
  };

  const refreshUploadCapabilities = async () => {
    isCapabilitiesLoading.value = true;
    try {
      uploadCapabilities.value = normalizeCapabilities(
        await knowledgeApiService.getDocumentUploadCapabilities({ force: true })
      );
      reconcileSelectedFiles();
    } catch (error) {
      uploadCapabilities.value = normalizeCapabilities(uploadCapabilities.value);
      ElMessage.warning('无法读取当前上传限制，已按安全默认值处理');
    } finally {
      isCapabilitiesLoading.value = false;
    }
  };

  watch(() => props.visible, (visible) => {
    if (visible) {
      void refreshUploadCapabilities();
    }
  }, { immediate: true });

  const addFiles = (files) => {
    if (isCapabilitiesLoading.value) {
      ElMessage.info('正在读取上传限制，请稍候');
      return;
    }

    const capabilities = uploadCapabilities.value;
    const validFiles = files.filter((file) => {
      if (!isAllowedFile(file, capabilities)) {
        ElMessage.warning(`文件 "${file.name}" 格式不支持`);
        return false;
      }

      if (file.size > capabilities.maxFileBytes) {
        ElMessage.warning(`文件 "${file.name}" 超过 ${formatFileSize(capabilities.maxFileBytes)} 大小限制`);
        return false;
      }

      if (selectedFiles.value.some((item) => item.name === file.name && item.size === file.size)) {
        ElMessage.warning(`文件 "${file.name}" 已经选择`);
        return false;
      }

      return true;
    });

    const remainingSlots = Math.max(0, capabilities.maxBatchFiles - selectedFiles.value.length);
    if (validFiles.length > remainingSlots) {
      ElMessage.warning(`最多只能选择 ${capabilities.maxBatchFiles} 个文件，当前剩余 ${Math.max(0, remainingSlots)} 个位置`);
      validFiles.splice(remainingSlots);
    }

    selectedFiles.value.push(...validFiles);
  };

  const handleDrop = (event) => {
    isDragOver.value = false;
    addFiles(Array.from(event.dataTransfer.files));
  };

  const handleFileSelect = (event) => {
    addFiles(Array.from(event.target.files));
    event.target.value = '';
  };

  const removeFile = (index) => {
    selectedFiles.value.splice(index, 1);
  };

  const triggerFileInput = () => {
    fileInput.value?.click();
  };

  const handleUpload = async () => {
    if (selectedFiles.value.length === 0 || isCapabilitiesLoading.value) return;

    reconcileSelectedFiles();
    if (selectedFiles.value.length === 0) return;

    isUploading.value = true;
    uploadProgress.value = selectedFiles.value.map((file) => ({
      fileName: file.name,
      percent: 0,
      status: 'uploading'
    }));

    try {
      const uploadPromises = selectedFiles.value.map(async (file, index) => {
        try {
          const formData = new FormData();
          formData.append('file', file);
          formData.append('knowledgeBaseId', props.knowledgeBaseId);
          formData.append('parseMode', parseMode.value);
          formData.append('enableVectorization', enableVectorization.value);

          await knowledgeApiService.uploadDocument(formData, (progress) => {
            uploadProgress.value[index].percent = Math.round(progress * 100);
          });

          uploadProgress.value[index].status = 'success';
          uploadProgress.value[index].percent = 100;
        } catch (error) {
          uploadProgress.value[index].status = 'error';
          uploadProgress.value[index].percent = 0;
          ElMessage.error(`${file.name} 上传失败：${getApiErrorMessage(error, '请稍后重试')}`);
        }
      });

      await Promise.all(uploadPromises);

      const successCount = uploadProgress.value.filter((progress) => progress.status === 'success').length;
      if (successCount > 0) {
        setTimeout(() => {
          emit('uploaded');
        }, 1000);
      }
    } catch (error) {
      ElMessage.error(getApiErrorMessage(error, '批量上传失败，请稍后重试'));
    } finally {
      isUploading.value = false;
    }
  };

  return {
    enableVectorization,
    acceptAttribute,
    fileInput,
    fileLimitLabel,
    formatFileSize,
    getFileIcon,
    getProgressStatusText,
    handleDrop,
    handleFileSelect,
    handleUpload,
    isDragOver,
    isCapabilitiesLoading,
    isUploading,
    parseMode,
    removeFile,
    refreshUploadCapabilities,
    selectedFiles,
    supportedExtensionsLabel,
    triggerFileInput,
    uploadProgress
  };
}
