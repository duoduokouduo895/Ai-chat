<template>
  <div class="ai-platform">
    <!-- 导入样式 -->
    <link rel="stylesheet" href="@/assets/styles/variables.css">
    <link rel="stylesheet" href="@/assets/styles/common.css">
    
    <!-- 侧边栏 -->
    <AppSidebar
      :currentView="currentView"
      :conversations="displayedConversations"
      :conversationId="conversationId"
      :isLoggedIn="isLoggedIn"
      @switchView="switchView"
      @newConversation="startNewConversation"
      @openConversation="openSearchedConversation"
      @authAction="handleAuthAction"
    />

    <!-- 主内容区域 -->
    <main class="main-content">
      <!-- 聊天面板 -->
      <ChatPanel
        v-if="currentView === 'chat'"
        :messages="messages"
        :availableModels="availableModels"
        :conversationId="conversationId"
        :isLoading="isLoading"
        @sendMessage="handleSendMessage"
        @newConversation="startNewConversation"
        @clearContext="clearContext"
      />
      
      <!-- 知识库面板 -->
      <KnowledgePanel
        v-else-if="currentView === 'knowledge'"
        @close="switchView('chat')"
      />
      
      <!-- 搜索面板 -->
      <SearchPanel
        v-else-if="currentView === 'search'"
        @close="switchView('chat')"
        @openConversation="openSearchedConversation"
      />
    </main>

    <!-- 游客限制模态框 -->
    <GuestLimitModal 
      :visible="showGuestLimitModal" 
      @close="showGuestLimitModal = false" 
      @go-to-register="navigateToRegister" 
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApiService } from '@/services/userApi.js'
import { aiApiService } from '@/services/aiApi.js'
import apiClient from '@/services/apiClient.js'
import { formatMessage } from '@/utils/messageFormatter.js'
import heartbeatService from '@/services/heartbeatService.js'

// 导入组件
import AppSidebar from './AppSidebar.vue'
import ChatPanel from '../chat/ChatPanel.vue'
import KnowledgePanel from '../knowledge/KnowledgePanel.vue'
import SearchPanel from '../search/SearchPanel.vue'
import GuestLimitModal from '../modal/GuestLimitModal.vue'

const router = useRouter()

// 核心状态管理
const currentView = ref('chat') // 'chat' | 'search' | 'knowledge'
const messages = ref([])
const conversationId = ref(null)
const isLoading = ref(false)
const showGuestLimitModal = ref(false)

// 用户状态
const isLoggedIn = ref(false)
const isGuest = ref(false)

// AI模型相关
const availableModels = ref([])

// 对话历史
const recentConversations = ref([])
const displayedConversations = computed(() => {
  // 按更新时间倒序排序，确保最新的对话在前面
  const sorted = recentConversations.value
    .slice()
    .sort((a, b) => {
      const dateA = new Date(a.updatedAt || a.createdAt)
      const dateB = new Date(b.updatedAt || b.createdAt)
      return dateB - dateA // 倒序排列，最新的在前面
    })
  return sorted.slice(0, 5)
})

// 核心业务方法
const checkUserStatus = () => {
  isLoggedIn.value = userApiService.isLoggedIn()
  isGuest.value = userApiService.isGuest()
}

const handleAuthAction = async () => {
  if (isLoggedIn.value) {
    try {
      // 停止心跳服务
      heartbeatService.stop()
      
      await userApiService.logout()
      checkUserStatus()
      messages.value = []
      conversationId.value = null
      await loadRecentConversations()
    } catch (error) {
      console.error('登出失败:', error)
    }
  } else {
    router.push('/login')
  }
}

const navigateToRegister = () => {
  router.push('/register')
}

const loadAvailableModels = async () => {
  try {
    const response = await apiClient.get('/models', { timeout: 8000 })
    if (response.data && Array.isArray(response.data) && response.data.length > 0) {
      availableModels.value = response.data
      console.log('模型列表加载成功:', availableModels.value)
    } else {
      throw new Error("API returned empty model list")
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
    availableModels.value = []
  }
}

const loadRecentConversations = async () => {
  try {
    const response = await apiClient.get('/conversations/recent', {
      timeout: 8000,
      headers: { 'Content-Type': 'application/json' }
    })
    if (response.data && Array.isArray(response.data)) {
      recentConversations.value = response.data
      console.log('对话历史记录加载成功:', recentConversations.value.length, '个对话')
    }
  } catch (error) {
    console.error('获取对话历史记录失败:', error)
    recentConversations.value = []
  }
}

const startNewConversation = async () => {
  try {
    currentView.value = 'chat'
    conversationId.value = null
    
    messages.value = [{
      id: 1,
      sender: 'ai',
      text: '请问有什么需要帮助的吗？',
      timestamp: new Date().toLocaleString(),
      type: 'chat'
    }]
    
    console.log('新对话界面已准备就绪')
  } catch (error) {
    console.error('开始新对话失败:', error)
  }
}

const clearContext = () => {
  messages.value = [{
    id: 1,
    sender: 'system',
    type: 'system',
    text: '欢迎使用AI助手！我可以帮助您解决各种问题。',
    timestamp: new Date().toLocaleTimeString()
  }]
  conversationId.value = null
}

const switchView = (view) => {
  currentView.value = view
}

const handleSendMessage = async (data) => {
  if (isLoading.value) return

  isLoading.value = true
  const userMessageContent = data.message

  const userMessage = {
    id: Date.now(),
    sender: 'user',
    text: userMessageContent,
    timestamp: new Date().toLocaleString(),
    type: 'chat'
  }
  messages.value.push(userMessage)

  const aiMessage = {
    id: Date.now() + 1,
    sender: 'ai',
    text: '',
    timestamp: new Date().toLocaleString(),
    type: 'chat'
  }
  messages.value.push(aiMessage)

  const chatRequest = {
    prompt: userMessageContent,
    model: data.model,
    conversationId: conversationId.value
  }

  let aiResponseText = ''

  await aiApiService.chatStream(
    chatRequest,
    (chunk) => {
      aiResponseText += chunk
      const currentAiMessageIndex = messages.value.findIndex(m => m.id === aiMessage.id)
      if (currentAiMessageIndex !== -1) {
        messages.value[currentAiMessageIndex].text = formatMessage(aiResponseText)
      }
    },
    () => {
      isLoading.value = false
      loadRecentConversations()
    },
    (error) => {
      console.error('聊天流中发生错误:', error)
      if (error.response && error.response.data && error.response.data.code === 'GUEST_LIMIT_EXCEEDED') {
        const aiMessageIndex = messages.value.findIndex(m => m.id === aiMessage.id)
        if (aiMessageIndex !== -1) {
          messages.value.splice(aiMessageIndex, 1)
        }
        showGuestLimitModal.value = true
      } else {
        const currentAiMessageIndex = messages.value.findIndex(m => m.id === aiMessage.id)
        if (currentAiMessageIndex !== -1) {
          messages.value[currentAiMessageIndex].text = '抱歉，AI服务暂时出现问题，请稍后再试。'
        }
      }
      isLoading.value = false
    },
    (metadata) => {
      if (metadata && metadata.conversationId) {
        conversationId.value = metadata.conversationId
        console.log(`上下文已更新，当前会话ID: ${conversationId.value}`)
      }
    }
  )
}

const openSearchedConversation = async (conversation) => {
  try {
    console.log('=== 开始加载对话 ===')
    console.log('传入的conversation对象:', JSON.stringify(conversation, null, 2))
    
    currentView.value = 'chat'
    
    const convId = conversation.conversationId || conversation.id
    console.log('提取的conversationId:', convId)
    
    if (!convId) {
      console.error('无法获取有效的对话ID')
      throw new Error('无法获取有效的对话ID')
    }
    
    const numericConvId = typeof convId === 'string' ? parseInt(convId) : convId
    conversationId.value = numericConvId
    
    const apiUrl = `/conversations/${numericConvId}/messages`
    console.log('API请求URL:', apiUrl)
    
    messages.value = [{
      id: 'loading',
      sender: 'system',
      text: '正在加载对话历史...', 
      timestamp: new Date().toLocaleString(),
      type: 'system'
    }]
    
    const response = await apiClient.get(apiUrl, {
      timeout: 10000,
      headers: { 'Content-Type': 'application/json' }
    })
    
    console.log('API响应状态:', response.status)
    console.log('API响应数据:', JSON.stringify(response.data, null, 2))
    
    if (response.data && Array.isArray(response.data)) {
      if (response.data.length === 0) {
        messages.value = [{
          id: Date.now(),
          sender: 'ai',
          text: '欢迎使用AI助手！请问有什么需要帮助的吗？',
          timestamp: new Date().toLocaleString(),
          type: 'chat'
        }]
      } else {
        messages.value = response.data.map((msg, index) => ({
          id: msg.id || index,
          sender: msg.sender || (msg.isUser ? 'user' : 'ai'),
          text: msg.content || msg.text,
          timestamp: new Date(msg.timestamp || msg.createdAt).toLocaleString(),
          type: 'chat'
        }))
      }
    } else {
      throw new Error('API响应数据格式错误')
    }
    
    console.log('=== 对话加载完成 ===')
  } catch (error) {
    console.error('=== 加载对话失败 ===', error)
    
    let errorMessage = '无法加载对话内容。'
    if (error.response) {
      errorMessage = `服务器错误: ${error.response.status} - ${error.response.data?.message || error.response.statusText}`
    } else if (error.request) {
      errorMessage = '网络请求失败，请检查网络连接。'
    } else if (error.message) {
      errorMessage = error.message
    }
    
    messages.value = [{
      id: Date.now(),
      sender: 'ai',
      text: errorMessage + '\n\n请稍后再试或联系管理员。',
      timestamp: new Date().toLocaleString(),
      type: 'chat'
    }]
  }
}

// 组件挂载
onMounted(async () => {
  try {
    checkUserStatus()
    await loadAvailableModels()
    await loadRecentConversations()
    
    // 如果用户已登录，启动心跳服务
    if (isLoggedIn.value) {
      console.log('用户已登录，启动JWT心跳服务')
      heartbeatService.start()
    }
    
    messages.value = [{
      id: 1,
      sender: 'ai',
      text: '请问有什么需要帮助的吗？',
      timestamp: new Date().toLocaleString(),
      type: 'chat'
    }]
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

// 组件卸载
onUnmounted(() => {
  // 停止心跳服务
  heartbeatService.stop()
})
</script>

<style scoped>
/* 导入公共样式 */
@import '@/assets/styles/variables.css';
@import '@/assets/styles/common.css';

.ai-platform {
  display: flex;
  height: 100vh;
  font-family: var(--font-family);
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  overflow: hidden;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .ai-platform {
    flex-direction: column;
  }
}
</style>