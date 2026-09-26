import { computed, ref } from 'vue';
import { confirmDialog } from '@/services/feedback.mjs';
import { aiApiService } from '@/services/aiApi.js';
import apiClient from '@/services/apiClient.js';
import {
  createChatErrorMessage,
  createSystemMessage,
  toMessageList
} from './useChatMessages.js';

export function useConversationHistory({
  chatMessages,
  conversationId,
  currentView,
  isLoading,
  isLoadingConversation,
  loadError,
  startNewConversation
}) {
  const recentConversations = ref([]);

  const displayedConversations = computed(() => recentConversations.value
    .slice()
    .sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt))
    .slice(0, 5));

  const loadRecentConversations = async () => {
    try {
      const response = await apiClient.get('/conversations/recent', {
        timeout: 8000,
        headers: { 'Content-Type': 'application/json' }
      });
      if (response.data && Array.isArray(response.data)) {
        recentConversations.value = response.data;
      }
    } catch {
      recentConversations.value = [];
    }
  };

  const deleteConversation = async (conversation) => {
    const targetConversationId = conversation?.conversationId || conversation?.id;
    if (!targetConversationId) {
      loadError.value = '无法获取要删除的对话ID。';
      return;
    }
    const confirmed = await confirmDialog({
      title: '删除对话',
      message: '确定要删除这个对话记录及其所有消息吗？此操作不可恢复。',
      confirmText: '删除',
      cancelText: '取消',
      tone: 'danger'
    });

    if (!confirmed) {
      return;
    }

    const previousConversations = [...recentConversations.value];
    const deletingCurrentConversation = String(conversationId.value) === String(targetConversationId);
    recentConversations.value = recentConversations.value.filter((item) => {
      const itemId = item?.conversationId || item?.id;
      return String(itemId) !== String(targetConversationId);
    });

    try {
      await aiApiService.deleteConversation(targetConversationId);
      if (deletingCurrentConversation) {
        await startNewConversation();
      }
      loadRecentConversations();
    } catch (error) {
      recentConversations.value = previousConversations;
      loadError.value = createChatErrorMessage(error);
      currentView.value = 'chat';
    }
  };

  const openSearchedConversation = async (conversation) => {
    try {
      isLoading.value = false;
      isLoadingConversation.value = true;
      loadError.value = '';
      currentView.value = 'chat';

      const convId = conversation.conversationId || conversation.id;
      if (!convId) {
        throw new Error('无法获取有效的对话ID');
      }

      const numericConvId = typeof convId === 'string' ? parseInt(convId, 10) : convId;
      conversationId.value = numericConvId;
      chatMessages.setMessages([createSystemMessage('正在加载对话历史...')]);

      const response = await apiClient.get(`/conversations/${numericConvId}/messages`, {
        timeout: 10000,
        headers: { 'Content-Type': 'application/json' }
      });

      if (!response.data || !Array.isArray(response.data)) {
        throw new Error('API响应数据格式错误');
      }

      chatMessages.setMessages(response.data.length === 0
        ? [createSystemMessage('该会话当前没有消息内容。若这不是新建会话，说明消息记录可能已被清理或保存失败。')]
        : toMessageList(response.data));
    } catch (error) {
      const message = createChatErrorMessage(error);
      loadError.value = message;
      chatMessages.setMessages([createSystemMessage(`${message}\n\n请稍后再试或联系管理员。`)]);
    } finally {
      isLoadingConversation.value = false;
    }
  };

  return {
    deleteConversation,
    displayedConversations,
    loadRecentConversations,
    openSearchedConversation,
    recentConversations
  };
}
