import http from '@/service/http'

export function getKnowledgeBaseList(params) {
  return http.get('/knowledge/bases', { params })
}

export function getAdminKnowledgeBaseList(params) {
  return http.get('/knowledge/admin/bases', { params })
}

export function getAdminKnowledgeBasePage(params) {
  return http.get('/knowledge/admin/bases/page', { params })
}

export function getKnowledgeBaseDetail(id) {
  return http.get(`/knowledge/bases/${id}`)
}

export function createKnowledgeBase(data, creatorId) {
  return http.post('/knowledge/admin/bases', data, {
    params: {
      creatorId,
    },
  })
}

export function updateKnowledgeBase(id, data) {
  return http.put(`/knowledge/bases/${id}`, data)
}

export function deleteKnowledgeBase(id) {
  return http.delete(`/knowledge/bases/${id}`)
}

export function uploadDocument(data) {
  return http.post('/knowledge/admin/documents/upload', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function getKnowledgeDocumentList(knowledgeBaseId, params) {
  return http.get(`/knowledge/bases/${knowledgeBaseId}/documents`, { params })
}

export function deleteDocument(documentId) {
  return http.delete(`/knowledge/documents/${documentId}`)
}

export function getAdminDocumentList(params) {
  return http.get('/knowledge/admin/documents', { params })
}

export function getAdminDocumentPage(params) {
  return http.get('/knowledge/admin/documents/page', { params })
}

export function getFailedCleanupOperations() {
  return http.get('/knowledge/cleanup-operations')
}

export function retryCleanupOperation(id) {
  return http.post(`/knowledge/cleanup-operations/${id}/retry`)
}
