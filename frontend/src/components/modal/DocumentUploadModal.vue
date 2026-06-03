<template>
  <div class="modal-overlay" @click.self="closeDialog">
    <div
      ref="dialogRef"
      class="modal"
      :class="{ active: visible }"
      role="dialog"
      aria-modal="true"
      aria-labelledby="upload-document-modal-title"
      tabindex="-1"
    >
      <header class="modal-header">
        <h2 id="upload-document-modal-title" class="modal-title">上传文档</h2>
        <button
          ref="closeButtonRef"
          type="button"
          class="modal-close"
          aria-label="关闭文档上传"
          @click="closeDialog"
        >
          ✕
        </button>
      </header>
      
      <div class="modal-body">
        <!-- 文件拖拽上传区域 -->
        <div 
          class="upload-area"
          :class="{ 
            'drag-over': isDragOver, 
            'has-files': selectedFiles.length > 0 
          }"
          @drop.prevent="handleDrop"
          @dragover.prevent="isDragOver = true"
          @dragleave.prevent="isDragOver = false"
          @click="triggerFileInput"
          @keydown.enter.prevent="triggerFileInput"
          @keydown.space.prevent="triggerFileInput"
          role="button"
          tabindex="0"
          aria-label="选择要上传的文档，也可以将文件拖放到这里"
        >
          <div v-if="selectedFiles.length === 0" class="upload-placeholder">
            <div class="upload-icon" aria-hidden="true">DOC</div>
            <h3>拖拽文件到这里或点击选择</h3>
            <p>支持 PDF, DOC, DOCX, TXT, MD, PPT, PPTX, XLS, XLSX 格式</p>
            <p class="file-limit">最大支持 50MB，单次最多上传 10 个文件</p>
          </div>
          
          <div v-else class="file-list">
            <div 
              v-for="(file, index) in selectedFiles" 
              :key="index"
              class="file-item"
            >
              <div class="file-info">
                <span class="file-icon">{{ getFileIcon(file.name) }}</span>
                <div class="file-details">
                  <div class="file-name">{{ file.name }}</div>
                  <div class="file-size">{{ formatFileSize(file.size) }}</div>
                </div>
              </div>
              <button 
                type="button"
                class="btn-base btn-sm"
                @click.stop="removeFile(index)"
                title="移除文件"
                :aria-label="`移除文件 ${file.name}`"
              >
                ✕
              </button>
            </div>
          </div>
        </div>
        
        <!-- 隐藏的文件输入 -->
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".pdf,.doc,.docx,.txt,.md,.ppt,.pptx,.xls,.xlsx"
          aria-label="选择要上传的文档"
          @change="handleFileSelect"
          style="display: none;"
        >
        
        <!-- 上传选项 -->
        <div class="upload-options" v-if="selectedFiles.length > 0">
          <div class="form-group">
            <label for="parse-mode" class="form-label">解析模式：</label>
            <select id="parse-mode" v-model="parseMode" class="input-base">
              <option value="auto">智能解析（推荐）</option>
              <option value="basic">基础解析</option>
              <option value="ai_enhanced">AI增强解析</option>
            </select>
          </div>
          
          <div class="form-group">
            <label class="form-label">
              <input 
                type="checkbox" 
                v-model="enableVectorization"
                class="checkbox"
              >
              自动向量化（生成嵌入向量用于检索）
            </label>
          </div>
        </div>
        
        <!-- 上传进度 -->
        <div v-if="uploadProgress.length > 0" class="upload-progress" role="status" aria-live="polite">
          <h4>上传进度</h4>
          <div 
            v-for="(progress, index) in uploadProgress" 
            :key="index"
            class="progress-item"
          >
            <div class="progress-info">
              <span class="progress-name">{{ progress.fileName }}</span>
              <span class="progress-status" :class="progress.status">
                {{ getProgressStatusText(progress.status) }}
              </span>
            </div>
            <div class="progress">
              <div 
                class="progress-bar" 
                :style="{ transform: `scaleX(${progress.percent / 100})` }"
                :class="progress.status"
              ></div>
            </div>
            <div class="progress-text">{{ progress.percent }}%</div>
          </div>
        </div>
      </div>
      
      <footer class="modal-footer">
        <button 
          class="btn-base btn-outline"
          @click="closeDialog"
          :disabled="isUploading"
        >
          取消
        </button>
        <button 
          class="btn-base btn-primary"
          @click="handleUpload"
          :disabled="selectedFiles.length === 0 || isUploading"
        >
          <span v-if="isUploading" class="loading-spinner"></span>
          {{ isUploading ? '上传中...' : `上传 ${selectedFiles.length} 个文件` }}
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, toRef } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { useModalDialog } from '@/composables/useModalDialog.js'
import { knowledgeApiService } from '@/services/knowledgeApi.js'
import { getApiErrorMessage } from '@/services/apiErrorService.js'

// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  knowledgeBaseId: {
    type: [String, Number],
    required: true
  }
})

// Emits
const emit = defineEmits(['close', 'uploaded'])

// 状态管理
const fileInput = ref(null)
const selectedFiles = ref([])
const isDragOver = ref(false)
const isUploading = ref(false)
const parseMode = ref('auto')
const enableVectorization = ref(true)
const uploadProgress = ref([])
const dialogRef = ref(null)
const closeButtonRef = ref(null)

const closeDialog = () => {
  emit('close')
}

useModalDialog({
  isOpen: toRef(props, 'visible'),
  dialogRef,
  initialFocusRef: closeButtonRef,
  onClose: closeDialog
})

// 支持的文件类型
const supportedTypes = {
  'application/pdf': 'pdf',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'text/plain': 'txt',
  'text/markdown': 'md',
  'application/vnd.ms-powerpoint': 'ppt',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx'
}

// 文件拖拽处理
const handleDrop = (event) => {
  isDragOver.value = false
  const files = Array.from(event.dataTransfer.files)
  addFiles(files)
}

// 文件选择处理
const handleFileSelect = (event) => {
  const files = Array.from(event.target.files)
  addFiles(files)
  // 清空input以便重复选择同一文件
  event.target.value = ''
}

// 添加文件
const addFiles = (files) => {
  const validFiles = files.filter(file => {
    // 检查文件类型
    if (!supportedTypes[file.type] && !isValidFileExtension(file.name)) {
      ElMessage.warning(`文件 "${file.name}" 格式不支持`)
      return false
    }
    
    // 检查文件大小 (50MB)
    if (file.size > 50 * 1024 * 1024) {
      ElMessage.warning(`文件 "${file.name}" 超过 50MB 大小限制`)
      return false
    }
    
    // 检查重复文件
    if (selectedFiles.value.some(f => f.name === file.name && f.size === file.size)) {
      ElMessage.warning(`文件 "${file.name}" 已经选择`)
      return false
    }
    
    return true
  })
  
  // 限制最多10个文件
  const remainingSlots = 10 - selectedFiles.value.length
  if (validFiles.length > remainingSlots) {
    ElMessage.warning(`最多只能选择 10 个文件，当前剩余 ${remainingSlots} 个位置`)
    validFiles.splice(remainingSlots)
  }
  
  selectedFiles.value.push(...validFiles)
}

// 检查文件扩展名
const isValidFileExtension = (fileName) => {
  const ext = fileName.toLowerCase().split('.').pop()
  return ['pdf', 'doc', 'docx', 'txt', 'md', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)
}

// 移除文件
const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value?.click()
}

// 获取文件图标
const getFileIcon = (fileName) => {
  const ext = fileName.toLowerCase().split('.').pop()
  return ext ? ext.toUpperCase() : 'DOC'
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 获取进度状态文本
const getProgressStatusText = (status) => {
  const statusMap = {
    'uploading': '上传中',
    'processing': '解析中',
    'vectorizing': '向量化中',
    'success': '成功',
    'error': '失败'
  }
  return statusMap[status] || '未知'
}

// 处理上传
const handleUpload = async () => {
  if (selectedFiles.value.length === 0) return
  
  isUploading.value = true
  uploadProgress.value = selectedFiles.value.map(file => ({
    fileName: file.name,
    percent: 0,
    status: 'uploading'
  }))
  
  try {
    // 批量上传文件
    const uploadPromises = selectedFiles.value.map(async (file, index) => {
      try {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('knowledgeBaseId', props.knowledgeBaseId)
        formData.append('parseMode', parseMode.value)
        formData.append('enableVectorization', enableVectorization.value)
        
        // 上传文件
        await knowledgeApiService.uploadDocument(formData, (progress) => {
          uploadProgress.value[index].percent = Math.round(progress * 100)
        })
        
        uploadProgress.value[index].status = 'success'
        uploadProgress.value[index].percent = 100
        
      } catch (error) {
        uploadProgress.value[index].status = 'error'
        uploadProgress.value[index].percent = 0
        ElMessage.error(`${file.name} 上传失败：${getApiErrorMessage(error, '请稍后重试')}`)
      }
    })
    
    await Promise.all(uploadPromises)
    
    // 检查是否有成功上传的文件
    const successCount = uploadProgress.value.filter(p => p.status === 'success').length
    if (successCount > 0) {
      setTimeout(() => {
        emit('uploaded')
      }, 1000)
    }
    
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '批量上传失败，请稍后重试'))
  } finally {
    isUploading.value = false
  }
}
</script>
