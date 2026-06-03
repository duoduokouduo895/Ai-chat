import apiClient from './apiClient';
import authSessionService from './authSessionService.js';

const API_BASE_URL = apiClient.defaults.baseURL;

export const aiApiService = {
    async chatStream(chatRequest, onData, onComplete, onError, onMetadata, abortController = null) {
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

            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let buffer = '';
            let isComplete = false;

            // 优化的流式响应处理 (标准 SSE 解析)
            let sseEvent = 'message';
            let sseData = null; // 用 null 代表还没收到过 data

            while (true && !isComplete) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || ''; // 保留最后一行未完整的缓冲

                for (let line of lines) {
                    // 处理可能带有的 \r 结尾，特别是 Windows 后端发出的响应
                    if (line.endsWith('\r')) {
                        line = line.substring(0, line.length - 1);
                    }

                    if (!line) {
                        // 空行代表一个完整的 SSE event 快结束了
                        if (sseData !== null) {
                            if (sseData === '[DONE]') {
                                isComplete = true;
                                break;
                            }

                            if (sseEvent === 'metadata') {
                                try {
                                    if (onMetadata) onMetadata(JSON.parse(sseData));
                                } catch (e) {
                                    console.warn('元数据解析失败:', e);
                                }
                            } else if (sseEvent === 'error') {
                                try {
                                    const errorPayload = JSON.parse(sseData);
                                    const error = new Error(errorPayload.message);
                                    error.response = { data: errorPayload };
                                    if (onError) onError(error);
                                    return; // 遇到错误立即返回
                                } catch (e) {
                                    console.warn('错误数据解析失败:', e);
                                }
                            } else {
                                // 默认 message
                                if (onData) onData(sseData);
                            }
                        }
                        // 重置状态
                        sseEvent = 'message';
                        sseData = null;
                        continue;
                    }

                    if (line.startsWith('event:')) {
                        sseEvent = line.substring(6).trim();
                    } else if (line.startsWith('data:')) {
                        // 注意不能直接 .trim()！会破坏 Markdown 的 `# ` 标题空格和代码缩进
                        // 根据 SSE 标准规范：如果冒号后面跟着一个空格，则只去除这一个空格
                        let currentData = line.substring(5);
                        if (currentData.startsWith(' ')) {
                            currentData = currentData.substring(1);
                        }

                        if (sseData === null) {
                            sseData = currentData;
                        } else {
                            sseData = sseData + '\n' + currentData;
                        }
                    }
                }
            }

            // 流式响应正常完成后调用 onComplete
            if (onComplete) onComplete();

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
        try {
            await apiClient.delete(`/conversations/${conversationId}`);
            // 返回无内容响应状态
            return true;
        } catch (error) {
            throw error;
        }
    }
};
