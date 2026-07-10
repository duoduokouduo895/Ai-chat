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
            <p>支持 {{ supportedExtensionsLabel }} 格式</p>
            <p class="file-limit">{{ isCapabilitiesLoading ? '正在读取上传限制…' : fileLimitLabel }}</p>
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
          :accept="acceptAttribute"
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
          :disabled="selectedFiles.length === 0 || isUploading || isCapabilitiesLoading"
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
import { useDocumentUploadQueue } from '@/composables/useDocumentUploadQueue.js'
import { useModalDialog } from '@/composables/useModalDialog.js'

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

const dialogRef = ref(null)
const closeButtonRef = ref(null)
const {
  acceptAttribute,
  enableVectorization,
  fileInput,
  fileLimitLabel,
  formatFileSize,
  getFileIcon,
  getProgressStatusText,
  handleDrop,
  handleFileSelect,
  handleUpload,
  isCapabilitiesLoading,
  isDragOver,
  isUploading,
  parseMode,
  removeFile,
  selectedFiles,
  supportedExtensionsLabel,
  triggerFileInput,
  uploadProgress
} = useDocumentUploadQueue(props, emit)

const closeDialog = () => {
  emit('close')
}

useModalDialog({
  isOpen: toRef(props, 'visible'),
  dialogRef,
  initialFocusRef: closeButtonRef,
  onClose: closeDialog
})
</script>
