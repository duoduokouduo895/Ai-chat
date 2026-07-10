import { aiApiService } from '@/services/aiApi.js';

export function useChatStreamController({
  abortController,
  chatMessages,
  conversationId,
  isLoading,
  loadRecentConversations,
  streamIdleTimeoutMs = 45000
}) {
  const stopGeneration = () => {
    if (abortController.value && isLoading.value) {
      abortController.value.abort();
      abortController.value = null;
      isLoading.value = false;
    }
  };

  const handleSendMessage = async (data) => {
    if (isLoading.value) return;

    isLoading.value = true;

    const userMessageContent = data.message;
    const userMessage = chatMessages.createUserMessage(userMessageContent);
    const aiMessage = chatMessages.createAiPlaceholder(data.knowledgeBaseIds?.length > 0);

    chatMessages.addUserMessage(userMessage);
    chatMessages.ensureAiMessageVisible(aiMessage);

    const chatRequest = {
      prompt: userMessageContent,
      model: data.model,
      conversationId: conversationId.value,
      clientMessageId: userMessage.id
    };

    if (data.knowledgeBaseIds && data.knowledgeBaseIds.length > 0) {
      chatRequest.knowledgeBaseIds = data.knowledgeBaseIds;
      chatRequest.maxRetrievedDocs = data.maxRetrievedDocs || 5;
      chatRequest.minSimilarity = data.minSimilarity || 0.3;
    }

    let aiResponseText = '';
    let updateFrame = 0;
    let responseCompleted = false;
    let idleTimer = 0;
    abortController.value = new AbortController();

    const clearIdleTimer = () => {
      if (idleTimer) {
        clearTimeout(idleTimer);
        idleTimer = 0;
      }
    };

    const finishRequest = () => {
      clearIdleTimer();
      isLoading.value = false;
      abortController.value = null;
    };

    const failByTimeout = () => {
      if (responseCompleted) return;
      responseCompleted = true;
      if (updateFrame) {
        cancelAnimationFrame(updateFrame);
        updateFrame = 0;
      }
      const timeoutError = new Error('模型响应超时，请稍后重试或切换模型。');
      chatMessages.failAiMessage(aiMessage, timeoutError);
      const currentController = abortController.value;
      finishRequest();
      currentController?.abort();
    };

    const resetIdleTimer = () => {
      clearIdleTimer();
      if (streamIdleTimeoutMs > 0) {
        idleTimer = setTimeout(failByTimeout, streamIdleTimeoutMs);
      }
    };

    const flushAiMessage = () => {
      chatMessages.updateAiText(aiMessage, aiResponseText);
      updateFrame = 0;
    };

    resetIdleTimer();

    await aiApiService.chatStream(
      chatRequest,
      (chunk) => {
        if (responseCompleted) return;
        resetIdleTimer();
        aiResponseText += chunk;
        if (!updateFrame) {
          updateFrame = requestAnimationFrame(flushAiMessage);
        }
        const currentAiMessage = chatMessages.ensureAiMessageVisible(aiMessage);
        if (currentAiMessage) {
          currentAiMessage.status = '';
        }
      },
      (aborted) => {
        if (responseCompleted) return;
        responseCompleted = true;
        if (updateFrame) {
          cancelAnimationFrame(updateFrame);
          updateFrame = 0;
        }
        chatMessages.completeAiMessage(aiMessage, aiResponseText, aborted);
        finishRequest();
        if (aiResponseText.trim()) {
          loadRecentConversations();
        }
      },
      (error) => {
        if (responseCompleted) return;
        responseCompleted = true;
        if (updateFrame) {
          cancelAnimationFrame(updateFrame);
          updateFrame = 0;
        }
        chatMessages.failAiMessage(aiMessage, error);
        finishRequest();
      },
      (metadata) => {
        if (metadata && metadata.conversationId) {
          conversationId.value = metadata.conversationId;
        }
      },
      (status) => {
        resetIdleTimer();
        chatMessages.updateAiStatus(aiMessage, status);
      },
      abortController.value
    );
  };

  return {
    handleSendMessage,
    stopGeneration
  };
}
