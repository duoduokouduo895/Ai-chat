import apiClient from './apiClient';

const API_BASE_URL = apiClient.defaults.baseURL;

export const aiApiService = {
    async chatStream(chatRequest, onData, onComplete, onError, onMetadata) {
        const token = localStorage.getItem('token');
        try {
            // 确保chatRequest包含所有必要字段
            const requestData = {
                prompt: chatRequest.message || chatRequest.prompt,
                model: chatRequest.model || chatRequest.modelId,
                conversationId: chatRequest.conversationId,
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
            
            console.log('📤 [前端发送] 知识库模式:', !!requestData.knowledgeBaseIds, '知识库IDs:', requestData.knowledgeBaseIds);
            
            const response = await fetch(`${API_BASE_URL}/ai/chat`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: "Failed to parse error response." }));
                const error = new Error(`HTTP error! status: ${response.status}`);
                error.response = { data: errorData, status: response.status };
                throw error;
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let isComplete = false;

            // 优化的流式响应处理
            while (true && !isComplete) {
                const { done, value } = await reader.read();
                if (done) {
                    break;
                }

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || ''; // 保留最后一行作为缓冲

                for (const line of lines) {
                    if (!line.trim() || !line.startsWith('data:')) continue;

                    const data = line.substring(5).trim();
                    
                    if (data === '[DONE]') {
                        isComplete = true;
                        break;
                    }
                    
                    if (data.startsWith('[METADATA]')) {
                        const metadataJson = data.substring(10);
                        try {
                            if (onMetadata) onMetadata(JSON.parse(metadataJson));
                        } catch (e) {
                            console.warn('元数据解析失败:', e);
                        }
                    } else if (data.startsWith('[ERROR]')) {
                        const errorJson = data.substring(7);
                        try {
                            const errorPayload = JSON.parse(errorJson);
                            const error = new Error(errorPayload.message);
                            error.response = { data: errorPayload };
                            if (onError) onError(error);
                            return; // 遇到错误立即返回
                        } catch (e) {
                            console.warn('错误数据解析失败:', e);
                        }
                    } else if (data && data.length > 0) {
                        // 只处理非空数据
                        if (onData) onData(data);
                    }
                }
            }
            
            // 流式响应正常完成后调用 onComplete
            if (onComplete) onComplete();

        } catch (error) {
            // This catches network errors and errors thrown from response.ok check
            if (onError) onError(error);
            console.error('Error during chat stream fetch:', error);
        }
    }
};