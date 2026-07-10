import { ref } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index';
import { knowledgeApiService } from '@/services/knowledgeApi.js';
import { getApiErrorMessage } from '@/services/apiErrorService.js';

const MAX_FILE_SIZE = 50 * 1024 * 1024;
const MAX_FILE_COUNT = 10;
const SUPPORTED_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md', 'ppt', 'pptx', 'xls', 'xlsx'];
const SUPPORTED_TYPES = {
  'application/pdf': 'pdf',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'text/plain': 'txt',
  'text/markdown': 'md',
  'application/vnd.ms-powerpoint': 'ppt',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx'
};

const isValidFileExtension = (fileName) => {
  const ext = fileName.toLowerCase().split('.').pop();
  return SUPPORTED_EXTENSIONS.includes(ext);
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

  const addFiles = (files) => {
    const validFiles = files.filter((file) => {
      if (!SUPPORTED_TYPES[file.type] && !isValidFileExtension(file.name)) {
        ElMessage.warning(`文件 "${file.name}" 格式不支持`);
        return false;
      }

      if (file.size > MAX_FILE_SIZE) {
        ElMessage.warning(`文件 "${file.name}" 超过 50MB 大小限制`);
        return false;
      }

      if (selectedFiles.value.some((item) => item.name === file.name && item.size === file.size)) {
        ElMessage.warning(`文件 "${file.name}" 已经选择`);
        return false;
      }

      return true;
    });

    const remainingSlots = MAX_FILE_COUNT - selectedFiles.value.length;
    if (validFiles.length > remainingSlots) {
      ElMessage.warning(`最多只能选择 10 个文件，当前剩余 ${remainingSlots} 个位置`);
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
    fileInput,
    formatFileSize,
    getFileIcon,
    getProgressStatusText,
    handleDrop,
    handleFileSelect,
    handleUpload,
    isDragOver,
    isUploading,
    parseMode,
    removeFile,
    selectedFiles,
    triggerFileInput,
    uploadProgress
  };
}
