import { ref } from 'vue';

export const createClientId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`;
};

export const createSystemMessage = (text) => ({
  id: createClientId(),
  sender: 'system',
  text,
  timestamp: new Date().toLocaleString(),
  type: 'system',
  completed: true
});

export const createChatErrorMessage = (error) => {
  if (error?.response?.status === 401) {
    return '登录状态已失效，请重新登录后继续对话。';
  }
  if (error?.response?.status === 403) {
    return error.response.data?.message || '你没有权限访问该资源，请检查账号或知识库权限。';
  }
  if (error?.response?.status === 400) {
    return error.response.data?.message || '请求参数不完整，请检查消息内容或模型选择。';
  }
  if (error?.response?.status >= 500) {
    return '服务暂时不可用，请稍后重试。';
  }
  if (error?.name === 'TypeError' || error?.request) {
    return '网络连接异常，请检查网络后重试。';
  }
  return error?.response?.data?.message || error?.message || '对话生成失败，请稍后重试。';
};

export const toMessageList = (items = []) => items.map((msg, index) => ({
  id: msg.id || index,
  sender: msg.sender || (msg.isUser ? 'user' : 'ai'),
  text: msg.content || msg.text,
  retrieval: msg.retrieval || null,
  chatTurnId: msg.chatTurnId || null,
  timestamp: new Date(msg.timestamp || msg.createdAt).toLocaleString(),
  type: 'chat',
  completed: true
}));

export function useChatMessages() {
  const messages = ref([]);

  const resetMessages = () => {
    messages.value = [];
  };

  const setMessages = (nextMessages) => {
    messages.value = nextMessages;
  };

  const setSystemMessage = (text) => {
    messages.value = [createSystemMessage(text)];
  };

  const createUserMessage = (text) => ({
    id: createClientId(),
    sender: 'user',
    text,
    timestamp: new Date().toLocaleString(),
    type: 'chat'
  });

  const createAiPlaceholder = (hasKnowledgeBases) => ({
    id: createClientId(),
    sender: 'ai',
    text: '',
    timestamp: new Date().toLocaleString(),
    type: 'chat',
    completed: false,
    status: hasKnowledgeBases ? '正在检索知识库' : '正在连接模型'
  });

  const addUserMessage = (message) => {
    messages.value.push(message);
  };

  const ensureAiMessageVisible = (aiMessage) => {
    let currentAiMessage = messages.value.find((message) => message.id === aiMessage.id);
    if (!currentAiMessage) {
      messages.value.push(aiMessage);
      currentAiMessage = aiMessage;
    }
    return currentAiMessage;
  };

  const updateAiText = (aiMessage, text) => {
    const currentAiMessage = ensureAiMessageVisible(aiMessage);
    if (currentAiMessage) {
      currentAiMessage.text = text;
    }
  };

  const updateAiStatus = (aiMessage, status) => {
    const currentAiMessage = messages.value.find((message) => message.id === aiMessage.id);
    if (currentAiMessage && !currentAiMessage.text) {
      currentAiMessage.status = status;
    }
  };

  const completeAiMessage = (aiMessage, text, aborted = false) => {
    const currentAiMessage = ensureAiMessageVisible(aiMessage);
    if (!currentAiMessage) return;

    currentAiMessage.text = text.trim() || (aborted ? '已停止生成。' : '本次没有生成内容，请重试。');
    currentAiMessage.completed = true;
    currentAiMessage.status = '';
  };

  const failAiMessage = (aiMessage, error) => {
    const currentAiMessage = ensureAiMessageVisible(aiMessage);
    if (!currentAiMessage) return;

    currentAiMessage.text = createChatErrorMessage(error);
    currentAiMessage.completed = true;
    currentAiMessage.isError = true;
    currentAiMessage.status = '';
  };

  return {
    addUserMessage,
    completeAiMessage,
    createAiPlaceholder,
    createUserMessage,
    ensureAiMessageVisible,
    failAiMessage,
    messages,
    resetMessages,
    setMessages,
    setSystemMessage,
    updateAiStatus,
    updateAiText
  };
}
