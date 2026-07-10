import apiClient from './apiClient.js'

const KNOWLEDGE_BASE_CACHE_TTL_MS = 30_000
let knowledgeBaseCache = null

const normalizeParams = (params = {}) => {
  const normalized = { ...params }
  delete normalized._t
  delete normalized.noCache
  return normalized
}

const cacheKeyFor = (params = {}) => JSON.stringify(normalizeParams(params))

const isKnowledgeBaseCacheFresh = (key) => (
  knowledgeBaseCache &&
  knowledgeBaseCache.key === key &&
  Date.now() - knowledgeBaseCache.createdAt < KNOWLEDGE_BASE_CACHE_TTL_MS
)

export const knowledgeApiService = {
  // 获取知识库列表
  async getKnowledgeBases(params = {}) {
    try {
      const cacheKey = cacheKeyFor(params)
      if (!params.noCache && isKnowledgeBaseCacheFresh(cacheKey)) {
        return knowledgeBaseCache.response
      }
      const response = await apiClient.get('/knowledge/bases', { params })
      if (!params.noCache) {
        knowledgeBaseCache = {
          key: cacheKey,
          response,
          createdAt: Date.now()
        }
      }
      return response
    } catch (error) {
      throw error
    }
  },

  invalidateKnowledgeBaseCache() {
    knowledgeBaseCache = null
  },

  // 创建知识库
  async createKnowledgeBase(data) {
    try {
      const response = await apiClient.post('/knowledge/bases', data)
      this.invalidateKnowledgeBaseCache()
      return response
    } catch (error) {
      throw error
    }
  },

  // 更新知识库
  async updateKnowledgeBase(id, data) {
    try {
      const response = await apiClient.put(`/knowledge/bases/${id}`, data)
      this.invalidateKnowledgeBaseCache()
      return response
    } catch (error) {
      throw error
    }
  },

  // 删除知识库
  async deleteKnowledgeBase(id) {
    try {
      const response = await apiClient.delete(`/knowledge/bases/${id}`)
      this.invalidateKnowledgeBaseCache()
      return response
    } catch (error) {
      throw error
    }
  },

  // 获取知识库详情
  async getKnowledgeBase(id) {
    try {
      const response = await apiClient.get(`/knowledge/bases/${id}`)
      return response
    } catch (error) {
      throw error
    }
  },

  // 获取知识库文档列表
  async getDocuments(knowledgeBaseId, params = {}) {
    try {
      const response = await apiClient.get(`/knowledge/bases/${knowledgeBaseId}/documents`, { params })
      return response
    } catch (error) {
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
      throw error
    }
  },

  // 删除文档
  async deleteDocument(id) {
    try {
      const response = await apiClient.delete(`/knowledge/documents/${id}`)
      return response
    } catch (error) {
      throw error
    }
  },

  // 获取文档详情
  async getDocument(id) {
    try {
      const response = await apiClient.get(`/knowledge/documents/${id}`)
      return response
    } catch (error) {
      throw error
    }
  },

  // 下载文档
  async downloadDocument(id) {
    try {
      const response = await apiClient.get(`/knowledge/documents/${id}/download`, {
        responseType: 'blob'
      })
      return response
    } catch (error) {
      throw error
    }
  },

  // 重新解析文档
  async reparseDocument(id, options = {}) {
    try {
      const response = await apiClient.post(`/knowledge/documents/${id}/reparse`, options)
      return response
    } catch (error) {
      throw error
    }
  },

  // 重新向量化文档
  async reEmbedDocument(id) {
    try {
      const response = await apiClient.post(`/knowledge/documents/${id}/re-embed`)
      return response
    } catch (error) {
      throw error
    }
  },

  async revectorizeDocument(id) {
    return this.reEmbedDocument(id)
  },

  // 搜索知识库
  async searchKnowledge(searchData) {
    try {
      const response = await apiClient.post('/knowledge/search', searchData)
      return response
    } catch (error) {
      throw error
    }
  },

  // 获取文档向量化状态
  async getDocumentEmbeddingStatus(id) {
    try {
      const response = await apiClient.get(`/knowledge/documents/${id}/embedding-status`)
      return response
    } catch (error) {
      throw error
    }
  },

  async getDocumentStatus(id) {
    return this.getDocumentEmbeddingStatus(id)
  },

  // 获取知识库统计信息
  async getKnowledgeBaseStats(id) {
    try {
      return await apiClient.get(`/knowledge/bases/${id}/stats`)
    } catch (error) {
      throw error
    }
  }
}

export default knowledgeApiService
