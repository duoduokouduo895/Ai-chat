import apiClient from './apiClient.js'

const KNOWLEDGE_BASE_CACHE_TTL_MS = 30_000
let knowledgeBaseCache = null
let uploadCapabilitiesCache = null

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
  },

  invalidateKnowledgeBaseCache() {
    knowledgeBaseCache = null
  },

  async getDocumentUploadCapabilities({ force = false } = {}) {
    if (!force && uploadCapabilitiesCache && Date.now() - uploadCapabilitiesCache.createdAt < KNOWLEDGE_BASE_CACHE_TTL_MS) {
      return uploadCapabilitiesCache.value
    }

    const response = await apiClient.get('/knowledge/upload-capabilities')
    uploadCapabilitiesCache = {
      value: response.data,
      createdAt: Date.now()
    }
    return uploadCapabilitiesCache.value
  },

  // 创建知识库
  async createKnowledgeBase(data) {
    const response = await apiClient.post('/knowledge/bases', data)
    this.invalidateKnowledgeBaseCache()
    return response
  },

  // 更新知识库
  async updateKnowledgeBase(id, data) {
    const response = await apiClient.put(`/knowledge/bases/${id}`, data)
    this.invalidateKnowledgeBaseCache()
    return response
  },

  // 删除知识库
  async deleteKnowledgeBase(id) {
    const response = await apiClient.delete(`/knowledge/bases/${id}`)
    this.invalidateKnowledgeBaseCache()
    return response
  },

  // 获取知识库详情
  async getKnowledgeBase(id) {
    return apiClient.get(`/knowledge/bases/${id}`)
  },

  // 获取知识库文档列表
  async getDocuments(knowledgeBaseId, params = {}) {
    return apiClient.get(`/knowledge/bases/${knowledgeBaseId}/documents`, { params })
  },

  // 上传文档
  async uploadDocument(formData, onProgress) {
    return apiClient.post('/knowledge/documents/upload', formData, {
      // 注意：使用FormData时不要手动设置Content-Type，让浏览器自动设置
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = progressEvent.loaded / progressEvent.total
          onProgress(progress)
        }
      }
    })
  },

  // 批量上传文档
  async batchUploadDocuments(files, knowledgeBaseId, options = {}) {
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

    return Promise.allSettled(uploadPromises)
  },

  // 删除文档
  async deleteDocument(id) {
    return apiClient.delete(`/knowledge/documents/${id}`)
  },

  // 获取文档详情
  async getDocument(id) {
    return apiClient.get(`/knowledge/documents/${id}`)
  },

  // 下载文档
  async downloadDocument(id) {
    return apiClient.get(`/knowledge/documents/${id}/download`, { responseType: 'blob' })
  },

  // 重新解析文档
  async reparseDocument(id, options = {}) {
    return apiClient.post(`/knowledge/documents/${id}/reparse`, options)
  },

  // 重新向量化文档
  async reEmbedDocument(id) {
    return apiClient.post(`/knowledge/documents/${id}/re-embed`)
  },

  async revectorizeDocument(id) {
    return this.reEmbedDocument(id)
  },

  // 搜索知识库
  async searchKnowledge(searchData) {
    return apiClient.post('/knowledge/search', searchData)
  },

  // 获取文档向量化状态
  async getDocumentEmbeddingStatus(id) {
    return apiClient.get(`/knowledge/documents/${id}/embedding-status`)
  },

  async getDocumentStatus(id) {
    return this.getDocumentEmbeddingStatus(id)
  },

  // 获取知识库统计信息
  async getKnowledgeBaseStats(id) {
    return apiClient.get(`/knowledge/bases/${id}/stats`)
  }
}

export default knowledgeApiService
