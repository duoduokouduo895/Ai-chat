import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { userApiService } from '@/services/userApi.js'
import { aiApiService } from '@/services/aiApi.js'
import apiClient from '@/services/apiClient.js'

const createClientId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const createSystemMessage = (text) => ({
  id: createClientId(),
  sender: 'system',
  text,
  timestamp: new Date().toLocaleString(),
  type: 'system',
  completed: true
})

const createChatErrorMessage = (error) => {
  if (error?.response?.status === 401) {
    return '登录状态已失效，请重新登录后继续对话。'
  }
  if (error?.response?.status === 403) {
    return error.response.data?.message || '你没有权限访问该资源，请检查账号或知识库权限。'
  }
  if (error?.response?.status === 400) {
    return error.response.data?.message || '请求参数不完整，请检查消息内容或模型选择。'
  }
  if (error?.response?.status >= 500) {
    return '服务暂时不可用，请稍后重试。'
  }
  if (error?.name === 'TypeError' || error?.request) {
    return '网络连接异常，请检查网络后重试。'
  }
  return error?.response?.data?.message || error?.message || '对话生成失败，请稍后重试。'
}

export function useChatWorkspace() {
  const router = useRouter()
  const currentView = ref('chat')
  const messages = ref([])
  const conversationId = ref(null)
  const isLoading = ref(false)
  const isLoadingConversation = ref(false)
  const loadError = ref('')
  const abortController = ref(null)
  const isLoggedIn = ref(false)
  const isGuest = ref(false)
  const availableModels = ref([])
  const recentConversations = ref([])

  const displayedConversations = computed(() => recentConversations.value
    .slice()
    .sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt))
    .slice(0, 5))

  const checkUserStatus = () => {
    isLoggedIn.value = userApiService.isLoggedIn()
    isGuest.value = localStorage.getItem('isGuest') === 'true'
  }

  const loadAvailableModels = async () => {
    try {
      const response = await apiClient.get('/ai/models', { timeout: 8000 })
      if (response.data && Array.isArray(response.data) && response.data.length > 0) {
        availableModels.value = response.data
        return
      }
      availableModels.value = []
    } catch (error) {
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
      }
    } catch (error) {
      recentConversations.value = []
    }
  }

  const startNewConversation = async () => {
    currentView.value = 'chat'
    conversationId.value = null
    loadError.value = ''
    messages.value = []
  }

  const clearContext = () => {
    conversationId.value = null
    loadError.value = ''
    messages.value = []
  }

  const switchView = (view) => {
    currentView.value = view
  }

  const stopGeneration = () => {
    if (abortController.value && isLoading.value) {
      abortController.value.abort()
      abortController.value = null
      isLoading.value = false
    }
  }

  const handleAuthAction = async () => {
    if (isGuest.value) {
      await router.push('/login')
      return
    }
    if (isLoggedIn.value) {
      await userApiService.logout()
      checkUserStatus()
      conversationId.value = null
      messages.value = []
      await loadRecentConversations()
      return
    }
    router.push('/login')
  }

  const deleteConversation = async (conversation) => {
    const targetConversationId = conversation?.conversationId || conversation?.id
    if (!targetConversationId) {
      loadError.value = '无法获取要删除的对话ID。'
      return
    }
    const confirmed = await ElMessageBox.confirm(
      '确定要删除这个对话记录及其所有消息吗？此操作不可恢复。',
      '删除对话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => true).catch(() => false)

    if (!confirmed) {
      return
    }

    const previousConversations = [...recentConversations.value]
    const deletingCurrentConversation = String(conversationId.value) === String(targetConversationId)
    recentConversations.value = recentConversations.value.filter((item) => {
      const itemId = item?.conversationId || item?.id
      return String(itemId) !== String(targetConversationId)
    })

    try {
      await aiApiService.deleteConversation(targetConversationId)
      if (deletingCurrentConversation) {
        await startNewConversation()
      }
      loadRecentConversations()
    } catch (error) {
      recentConversations.value = previousConversations
      loadError.value = createChatErrorMessage(error)
      currentView.value = 'chat'
    }
  }

  const handleSendMessage = async (data) => {
    if (isLoading.value) return

    isLoading.value = true
    loadError.value = ''

    const userMessageContent = data.message
    const userMessage = {
      id: createClientId(),
      sender: 'user',
      text: userMessageContent,
      timestamp: new Date().toLocaleString(),
      type: 'chat'
    }
    const aiMessage = {
      id: createClientId(),
      sender: 'ai',
      text: '',
      timestamp: new Date().toLocaleString(),
      type: 'chat',
      completed: false
    }

    messages.value.push(userMessage, aiMessage)

    const chatRequest = {
      prompt: userMessageContent,
      model: data.model,
      conversationId: conversationId.value,
      clientMessageId: userMessage.id
    }

    if (data.knowledgeBaseIds && data.knowledgeBaseIds.length > 0) {
      chatRequest.knowledgeBaseIds = data.knowledgeBaseIds
      chatRequest.maxRetrievedDocs = data.maxRetrievedDocs || 5
      chatRequest.minSimilarity = data.minSimilarity || 0.3
    }

    let aiResponseText = ''
    let lastUpdateTime = 0
    let responseCompleted = false
    abortController.value = new AbortController()

    await aiApiService.chatStream(
      chatRequest,
      (chunk) => {
        if (responseCompleted) return
        aiResponseText += chunk
        const now = Date.now()
        if (now - lastUpdateTime > 50) {
          const currentAiMessage = messages.value.find((message) => message.id === aiMessage.id)
          if (currentAiMessage) {
            currentAiMessage.text = aiResponseText
          }
          lastUpdateTime = now
        }
      },
      (aborted) => {
        responseCompleted = true
        const currentAiMessage = messages.value.find((message) => message.id === aiMessage.id)
        if (currentAiMessage) {
          currentAiMessage.text = aiResponseText.trim() || (aborted ? '已停止生成。' : '本次没有生成内容，请重试。')
          currentAiMessage.completed = true
        }
        isLoading.value = false
        abortController.value = null
        if (aiResponseText.trim()) {
          loadRecentConversations()
        }
      },
      (error) => {
        const currentAiMessage = messages.value.find((message) => message.id === aiMessage.id)
        if (currentAiMessage) {
          currentAiMessage.text = createChatErrorMessage(error)
          currentAiMessage.completed = true
          currentAiMessage.isError = true
        }
        isLoading.value = false
        abortController.value = null
      },
      (metadata) => {
        if (metadata && metadata.conversationId) {
          conversationId.value = metadata.conversationId
        }
      },
      abortController.value
    )
  }

  const openSearchedConversation = async (conversation) => {
    try {
      isLoading.value = false
      isLoadingConversation.value = true
      loadError.value = ''
      currentView.value = 'chat'

      const convId = conversation.conversationId || conversation.id
      if (!convId) {
        throw new Error('无法获取有效的对话ID')
      }

      const numericConvId = typeof convId === 'string' ? parseInt(convId, 10) : convId
      conversationId.value = numericConvId
      messages.value = [createSystemMessage('正在加载对话历史...')]

      const response = await apiClient.get(`/conversations/${numericConvId}/messages`, {
        timeout: 10000,
        headers: { 'Content-Type': 'application/json' }
      })

      if (!response.data || !Array.isArray(response.data)) {
        throw new Error('API响应数据格式错误')
      }

      messages.value = response.data.length === 0
        ? [createSystemMessage('该会话当前没有消息内容。若这不是新建会话，说明消息记录可能已被清理或保存失败。')]
        : response.data.map((msg, index) => ({
          id: msg.id || index,
          sender: msg.sender || (msg.isUser ? 'user' : 'ai'),
          text: msg.content || msg.text,
          timestamp: new Date(msg.timestamp || msg.createdAt).toLocaleString(),
          type: 'chat',
          completed: true
        }))
    } catch (error) {
      const message = createChatErrorMessage(error)
      loadError.value = message
      messages.value = [createSystemMessage(`${message}\n\n请稍后再试或联系管理员。`)]
    } finally {
      isLoadingConversation.value = false
    }
  }

  const initWorkspace = async () => {
    checkUserStatus()
    await Promise.all([
      loadAvailableModels(),
      loadRecentConversations()
    ])
    messages.value = []
  }

  return {
    availableModels,
    checkUserStatus,
    clearContext,
    conversationId,
    currentView,
    deleteConversation,
    displayedConversations,
    handleAuthAction,
    handleSendMessage,
    initWorkspace,
    isLoading,
    isLoadingConversation,
    isGuest,
    isLoggedIn,
    loadError,
    messages,
    openSearchedConversation,
    startNewConversation,
    stopGeneration,
    switchView
  }
}
