import apiClient from './apiClient.js'

export const knowledgeApiService = {
  // 获取知识库列表
  async getKnowledgeBases(params = {}) {
    try {
      const response = await apiClient.get('/knowledge/bases', { params })
      return response
    } catch (error) {
      console.error('获取知识库列表失败:', error)
      throw error
    }
  },

  // 创建知识库
  async createKnowledgeBase(data) {
    try {
      const response = await apiClient.post('/knowledge/bases', data)
      return response
    } catch (error) {
      console.error('创建知识库失败:', error)
      throw error
    }
  },

  // 更新知识库
  async updateKnowledgeBase(id, data) {
    try {
      const response = await apiClient.put(`/knowledge/bases/${id}`, data)
      return response
    } catch (error) {
      console.error('更新知识库失败:', error)
      throw error
    }
  },

  // 删除知识库
  async deleteKnowledgeBase(id) {
    try {
      const response = await apiClient.delete(`/knowledge/bases/${id}`)
      return response
    } catch (error) {
      console.error('删除知识库失败:', error)
      throw error
    }
  },

  // 获取知识库详情
  async getKnowledgeBase(id) {
    try {
      const response = await apiClient.get(`/knowledge/bases/${id}`)
      return response
    } catch (error) {
      console.error('获取知识库详情失败:', error)
      throw error
    }
  },

  // 获取知识库文档列表
  async getDocuments(knowledgeBaseId, params = {}) {
    try {
      const response = await apiClient.get(`/knowledge/bases/${knowledgeBaseId}/documents`, { params })
      return response
    } catch (error) {
      console.error('获取文档列表失败:', error)
      throw error
    }
  },

  // 上传文档
  async uploadDocument(formData, onProgress) {
    try {
      const response = await apiClient.post('/knowledge/documents/upload', formData, {
        // 注意：使用FormData时不要手动设置Content-Type，让浏览器自动设置
        onUploadProgress: (progressEvent) => {
          if (onProgress && progressEvent.total) {
            const progress = progressEvent.loaded / progressEvent.total
            onProgress(progress)
          }
        }
      })
      return response
    } catch (error) {
      console.error('上传文档失败:', error)
      throw error
    }
  },

  // 批量上传文档
  async batchUploadDocuments(files, knowledgeBaseId, options = {}) {
    try {
      const uploadPromises = files.map(async (file) => {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('knowledgeBaseId', knowledgeBaseId)
        
        // 添加可选参数
        if (options.parseMode) {
          formData.append('parseMode', options.parseMode)
        }
        if (options.enableVectorization !== undefined) {
          formData.append('enableVectorization', options.enableVectorization)
        }
        
        return this.uploadDocument(formData, options.onProgress)
      })
      
      const results = await Promise.allSettled(uploadPromises)
      return results
    } catch (error) {
      console.error('批量上传文档失败:', error)
      throw error
    }
  },

  // 删除文档
  async deleteDocument(id) {
    try {
      const response = await apiClient.delete(`/knowledge/documents/${id}`)
      return response
    } catch (error) {
      console.error('删除文档失败:', error)
      throw error
    }
  },

  // 获取文档详情
  async getDocument(id) {
    try {
      const response = await apiClient.get(`/knowledge/documents/${id}`)
      return response
    } catch (error) {
      console.error('获取文档详情失败:', error)
      throw error
    }
  },

  // 重新解析文档
  async reparseDocument(id, options = {}) {
    try {
      const response = await apiClient.post(`/knowledge/documents/${id}/reparse`, options)
      return response
    } catch (error) {
      console.error('重新解析文档失败:', error)
      throw error
    }
  },

  // 重新向量化文档
  async revectorizeDocument(id) {
    try {
      const response = await apiClient.post(`/knowledge/documents/${id}/revectorize`)
      return response
    } catch (error) {
      console.error('重新向量化文档失败:', error)
      throw error
    }
  },

  // 搜索知识库
  async searchKnowledge(searchData) {
    try {
      const response = await apiClient.post('/knowledge/search', searchData)
      return response
    } catch (error) {
      console.error('搜索知识库失败:', error)
      throw error
    }
  },

  // RAG对话
  async ragChat(chatData) {
    try {
      const response = await apiClient.post('/knowledge/chat', chatData)
      return response
    } catch (error) {
      console.error('RAG对话失败:', error)
      throw error
    }
  },

  // RAG流式对话
  async ragChatStream(chatData, onMessage, onComplete, onError) {
    try {
      const response = await fetch(`${apiClient.defaults.baseURL}/knowledge/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...apiClient.defaults.headers.common
        },
        body: JSON.stringify(chatData)
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()

      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (data === '[DONE]') {
              onComplete && onComplete()
              return
            }
            try {
              const parsed = JSON.parse(data)
              if (parsed.content) {
                onMessage && onMessage(parsed.content)
              }
            } catch (e) {
              console.warn('解析SSE数据失败:', e)
            }
          }
        }
      }
      
      onComplete && onComplete()
    } catch (error) {
      console.error('RAG流式对话失败:', error)
      onError && onError(error)
    }
  },

  // 获取文档解析状态
  async getDocumentStatus(id) {
    try {
      const response = await apiClient.get(`/knowledge/documents/${id}/status`)
      return response
    } catch (error) {
      console.error('获取文档状态失败:', error)
      throw error
    }
  },

  // 获取知识库统计信息
  async getKnowledgeBaseStats(id) {
    try {
      const response = await apiClient.get(`/knowledge/bases/${id}/stats`)
      return response
    } catch (error) {
      console.error('获取知识库统计失败:', error)
      throw error
    }
  }
}

export default knowledgeApiService