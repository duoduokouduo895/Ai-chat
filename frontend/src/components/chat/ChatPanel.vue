<template>
  <div class="chat-panel">
    <!-- 聊天头部 -->
    <header class="chat-header">
      <h1 class="chat-title">✨ Dduo AI-Chat</h1>
    </header>
    
    <!-- 对话导航栏 -->
    <nav class="conversation-nav toolbar">
      <div class="nav-left">
        <span class="nav-title">{{ conversationTitle }}</span>
      </div>
      <div class="nav-right">
        <button 
          class="new-conversation-btn"
          @click="$emit('newConversation')" 
          title="开始新对话"
        >
          <span class="btn-icon">+</span>
          <span class="btn-text">新建</span>
        </button>
      </div>
    </nav>
    
    <!-- 消息列表 -->
    <div class="chat-messages custom-scroll" ref="messageContainer">
      <div 
        v-for="message in messages" 
        :key="message.id" 
        class="message-wrapper"
      >
        <SystemMessage 
          v-if="message.type === 'system'" 
          :message="message.text"
        />
        <ChatMessage 
          v-else
          :message="message"
        />
      </div>
    </div>
    
    <!-- 输入区域 -->
    <div class="chat-input-area">
      <ChatInput
        v-model:message="inputMessage"
        v-model:selectedModel="selectedModel"
        v-model:selectedKnowledgeBaseIds="selectedKnowledgeBaseIds"
        :availableModels="availableModels"
        :isLoading="isLoading"
        @send="handleSendMessage"
        @clear="$emit('clearContext')"
        @mode-change="handleModeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import ChatMessage from './ChatMessage.vue'
import SystemMessage from './SystemMessage.vue'
import ChatInput from './ChatInput.vue'

// Props
const props = defineProps({
  messages: {
    type: Array,
    required: true
  },
  availableModels: {
    type: Array,
    default: () => []
  },
  conversationId: {
    type: [String, Number],
    default: null
  },
  isLoading: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits([
  'sendMessage',
  'newConversation', 
  'clearContext'
])

// 本地状态
const messageContainer = ref(null)
const inputMessage = ref('')
const selectedModel = ref('zai-org/GLM-4.5-Air')
const selectedKnowledgeBaseIds = ref([])
const chatMode = ref('normal') // 'normal' | 'rag'

// 计算属性
const conversationTitle = computed(() => {
  const baseTitle = '新的对话'  // 固定显示"新的对话"
  const modeLabel = chatMode.value === 'rag' ? ' (知识库问答)' : ''
  return baseTitle + modeLabel
})

// 发送消息处理
const handleSendMessage = (message, model, knowledgeBaseIds) => {
  const chatRequest = {
    message,
    model,
    conversationId: props.conversationId
  }
  
  // 如果选择了知识库，添加RAG相关参数
  if (knowledgeBaseIds && knowledgeBaseIds.length > 0) {
    chatRequest.knowledgeBaseIds = knowledgeBaseIds
    chatRequest.maxRetrievedDocs = 5
    chatRequest.minSimilarity = 0.3  // 与后端配置保持一致
  }
  
  emit('sendMessage', chatRequest)
  inputMessage.value = ''
}

// 处理聊天模式切换
const handleModeChange = (mode) => {
  chatMode.value = mode
  console.log(`聊天模式切换为: ${mode}`)
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

// 监听消息变化，自动滚动
watch(() => props.messages, scrollToBottom, { deep: true })
</script>

<style scoped>
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  height: 100vh;
  overflow: hidden;
}

.chat-header {
  background: #fffbeb;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-bottom: 1px solid var(--border-light);
  text-align: center;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-title {
  font-weight: 600;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  margin: 0;
  opacity: 0.65;
}

.conversation-nav {
  justify-content: space-between;
}

.nav-title {
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--text-secondary);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-xl);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.chat-input-area {
  padding: var(--spacing-xl);
  background-color: var(--bg-primary);
  border-top: 1px solid var(--border-light);
}

/* 新对话按钮样式 */
.new-conversation-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
  border: none;
  border-radius: var(--border-radius-sm);  /* 改为小圆角，更方形的设计 */
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px 0 rgba(16, 185, 129, 0.25);
  min-height: 32px;
  user-select: none;
}

.new-conversation-btn:hover {
  background: linear-gradient(135deg, #059669, #047857);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px 0 rgba(16, 185, 129, 0.35);
}

.new-conversation-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px 0 rgba(16, 185, 129, 0.25);
}

.new-conversation-btn .btn-icon {
  width: 16px;
  height: 16px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: var(--border-radius-sm);  /* 图标也改为小圆角方形 */
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.new-conversation-btn .btn-text {
  font-size: var(--font-size-sm);
  font-weight: 500;
  letter-spacing: 0.2px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .nav-title {
    font-size: var(--font-size-sm);
  }
  
  .chat-messages {
    padding: var(--spacing-md);
  }
  
  .chat-input-area {
    padding: var(--spacing-md);
  }
}
</style>