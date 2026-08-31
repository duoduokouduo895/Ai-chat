import apiClient from './apiClient';
import authSessionService from './authSessionService.js';
import { readSseEventStream } from './sseEventStream.mjs';

const API_BASE_URL = apiClient.defaults.baseURL;

export const aiApiService = {
    async chatStream(chatRequest, onData, onComplete, onError, onMetadata, onStatus, abortController = null) {
        try {

            // 确保chatRequest包含所有必要字段
            const requestData = {
                prompt: chatRequest.message || chatRequest.prompt,
                model: chatRequest.model || chatRequest.modelId,
                conversationId: chatRequest.conversationId,
                clientMessageId: chatRequest.clientMessageId,
                // 支持知识库相关参数
                knowledgeBaseIds: chatRequest.knowledgeBaseIds || null,
                maxRetrievedDocs: chatRequest.maxRetrievedDocs || 5,
                minSimilarity: chatRequest.minSimilarity || 0.3,  // 与后端配置保持一致
                // 其他可选参数
                saveMessage: chatRequest.saveMessage !== false,
                streaming: chatRequest.streaming !== false
            };

            // 移除null或undefined的字段
            Object.keys(requestData).forEach(key => {
                if (requestData[key] === null || requestData[key] === undefined) {
                    delete requestData[key];
                }
            });

            const response = await fetch(`${API_BASE_URL}/ai/chat`, {
                method: 'POST',
                headers: authSessionService.buildAuthHeaders({
                    'Accept': 'text/event-stream; charset=utf-8',
                    'Content-Type': 'application/json'
                }),
                body: JSON.stringify(requestData),
                signal: abortController ? abortController.signal : undefined
            });

            authSessionService.applyRefreshedToken(response.headers);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: "Failed to parse error response." }));
                const error = new Error(`HTTP error! status: ${response.status}`);
                error.response = { data: errorData, status: response.status };
                if (response.status === 401) {
                    await authSessionService.handleUnauthorized();
                }
                throw error;
            }

            let streamErrored = false;
            await readSseEventStream(response, {
                onMessage: onData,
                onMetadata,
                onStatus,
                onError: (error) => {
                    streamErrored = true;
                    if (onError) onError(error);
                }
            });

            // 流式响应正常完成后调用 onComplete
            if (!streamErrored && onComplete) onComplete();

        } catch (error) {
            // Handle AbortError specifically to not treat it as a scary error
            if (error.name === 'AbortError') {
                if (onComplete) onComplete(true); // Call complete but indicate it was aborted if helpful
                return;
            }
            // This catches network errors and errors thrown from response.ok check
            if (onError) onError(error);
        }
    },

    // 新增：删除整个对话及消息
    async deleteConversation(conversationId) {
        await apiClient.delete(`/conversations/${conversationId}`);
        // 返回无内容响应状态
        return true;
    }
};
