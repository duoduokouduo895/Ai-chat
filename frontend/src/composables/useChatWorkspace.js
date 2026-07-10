import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { userApiService } from '@/services/userApi.js';
import apiClient from '@/services/apiClient.js';
import { isGuest as hasGuestSession } from '@/services/sessionStore.mjs';
import { useChatMessages } from './useChatMessages.js';
import { useChatStreamController } from './useChatStreamController.js';
import { useConversationHistory } from './useConversationHistory.js';

export function useChatWorkspace() {
  const router = useRouter();
  const currentView = ref('chat');
  const conversationId = ref(null);
  const isLoading = ref(false);
  const isLoadingConversation = ref(false);
  const loadError = ref('');
  const abortController = ref(null);
  const isLoggedIn = ref(false);
  const isGuest = ref(false);
  const availableModels = ref([]);

  const chatMessages = useChatMessages();

  const checkUserStatus = () => {
    isLoggedIn.value = userApiService.isLoggedIn();
    isGuest.value = hasGuestSession();
  };

  const loadAvailableModels = async () => {
    try {
      const response = await apiClient.get('/ai/models', { timeout: 8000 });
      if (response.data && Array.isArray(response.data) && response.data.length > 0) {
        availableModels.value = response.data;
        return;
      }
      availableModels.value = [];
    } catch {
      availableModels.value = [];
    }
  };

  const startNewConversation = async () => {
    currentView.value = 'chat';
    conversationId.value = null;
    loadError.value = '';
    chatMessages.resetMessages();
  };

  const clearContext = () => {
    conversationId.value = null;
    loadError.value = '';
    chatMessages.resetMessages();
  };

  const switchView = (view) => {
    currentView.value = view;
  };

  const conversationHistory = useConversationHistory({
    chatMessages,
    conversationId,
    currentView,
    isLoading,
    isLoadingConversation,
    loadError,
    startNewConversation
  });

  const chatStreamController = useChatStreamController({
    abortController,
    chatMessages,
    conversationId,
    isLoading,
    loadRecentConversations: conversationHistory.loadRecentConversations
  });

  const handleAuthAction = async () => {
    if (isGuest.value) {
      await router.push('/login');
      return;
    }
    if (isLoggedIn.value) {
      await userApiService.logout();
      checkUserStatus();
      conversationId.value = null;
      chatMessages.resetMessages();
      await conversationHistory.loadRecentConversations();
      return;
    }
    router.push('/login');
  };

  const handleSendMessage = async (data) => {
    loadError.value = '';
    await chatStreamController.handleSendMessage(data);
  };

  const initWorkspace = async () => {
    checkUserStatus();
    await Promise.all([
      loadAvailableModels(),
      conversationHistory.loadRecentConversations()
    ]);
    chatMessages.resetMessages();
  };

  return {
    availableModels,
    checkUserStatus,
    clearContext,
    conversationId,
    currentView,
    deleteConversation: conversationHistory.deleteConversation,
    displayedConversations: conversationHistory.displayedConversations,
    handleAuthAction,
    handleSendMessage,
    initWorkspace,
    isLoading,
    isLoadingConversation,
    isGuest,
    isLoggedIn,
    loadError,
    messages: chatMessages.messages,
    openSearchedConversation: conversationHistory.openSearchedConversation,
    startNewConversation,
    stopGeneration: chatStreamController.stopGeneration,
    switchView
  };
}
