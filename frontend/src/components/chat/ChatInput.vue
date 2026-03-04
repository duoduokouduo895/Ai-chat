<template>
  <div class="chat-input-container">
    <!-- 工具栏 -->
    <div class="input-toolbar">
      <ModelSelector 
        v-model="localSelectedModel"
        :models="availableModels"
      />
      <KnowledgeSelector
        v-model="knowledgeBaseIds"
        @mode-change="handleModeChange"
      />
      <button 
        class="btn-base btn-outline btn-sm"
        @click="$emit('clear')" 
        title="清空对话"
      >
        🗑️ 清空
      </button>
    </div>
    
    <!-- 输入区域 -->
    <div class="input-area">
      <textarea
        v-model="localMessage"
        @keydown.enter.ctrl="handleSend"
        @keydown.enter.exact="handleEnterKey"
        placeholder="请输入消息... (Ctrl+Enter 发送)"
        class="input-base message-input"
        ref="textareaRef"
      ></textarea>
      
      <div class="send-button-container">
        <button 
          v-if="!isLoading"
          class="btn-base btn-primary"
          @click="handleSend"
          :disabled="!localMessage.trim()"
        >
          发送(Ctrl+Enter)
        </button>
        <button 
          v-else
          class="btn-base btn-danger"
          @click="$emit('stop')"
        >
          <span class="loading"></span>
          停止生成
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import ModelSelector from '../common/ModelSelector.vue'
import KnowledgeSelector from '../knowledge/KnowledgeSelector.vue'

// Props
const props = defineProps({
  message: {
    type: String,
    default: ''
  },
  selectedModel: {
    type: String,
    default: 'zai-org/GLM-4.5-Air'
  },
  availableModels: {
    type: Array,
    default: () => []
  },
  isLoading: {
    type: Boolean,
    default: false
  },
  selectedKnowledgeBaseIds: {
    type: Array,
    default: () => []
  }
})

// Emits
const emit = defineEmits(['update:message', 'update:selectedModel', 'update:selectedKnowledgeBaseIds', 'send', 'clear', 'mode-change', 'stop'])

// 本地状态
const textareaRef = ref(null)
const knowledgeBaseIds = ref(props.selectedKnowledgeBaseIds || [])

// 双向绑定
const localMessage = computed({
  get: () => props.message,
  set: (value) => emit('update:message', value)
})

const localSelectedModel = computed({
  get: () => props.selectedModel,
  set: (value) => emit('update:selectedModel', value)
})

// 发送消息
const handleSend = () => {
  if (!localMessage.value.trim() || props.isLoading) return
  
  emit('send', localMessage.value.trim(), localSelectedModel.value, knowledgeBaseIds.value)
}

// 处理知识库模式切换
const handleModeChange = (mode) => {
  emit('mode-change', mode)
}

// 监听知识库选择变化
watch(knowledgeBaseIds, (newVal) => {
  emit('update:selectedKnowledgeBaseIds', newVal)
}, { deep: true })

// 处理Enter键
const handleEnterKey = (event) => {
  if (!event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

// 自动调整高度
const adjustHeight = () => {
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
    textareaRef.value.style.height = textareaRef.value.scrollHeight + 'px'
  }
}

// 监听消息变化调整高度
watch(localMessage, adjustHeight)
</script>

<style scoped>
.chat-input-container {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  color: var(--text-primary);
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.message-input {
  min-height: 80px;
  max-height: 200px;
  resize: vertical;
  font-family: var(--font-family);
  line-height: 1.5;
}

.send-button-container {
  display: flex;
  justify-content: flex-end;
}

.send-button-container .btn-base {
  min-width: 140px;
  gap: var(--spacing-sm);
}

.btn-danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  border: none;
  border-radius: var(--border-radius-base);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 15px rgba(220, 38, 38, 0.2);
}

.btn-danger:hover {
  background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(220, 38, 38, 0.3);
}

.btn-danger:active {
  transform: translateY(1px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .input-toolbar {
    flex-direction: column;
    gap: var(--spacing-md);
    align-items: stretch;
  }
  
  .send-button-container .btn-base {
    width: 100%;
  }
}
</style>