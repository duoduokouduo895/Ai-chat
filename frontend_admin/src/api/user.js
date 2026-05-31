import http from '@/service/http'

export function getAdminUsers(params) {
  return http.get('/admin/users', { params })
}

export function deleteAdminUser(userId) {
  return http.delete(`/admin/users/${userId}`)
}

export function getAdminUserConversations(userId, params) {
  return http.get(`/admin/users/${userId}/conversations`, { params })
}

export function getAdminUserDocuments(userId, params) {
  return http.get(`/admin/users/${userId}/documents`, { params })
}

export function getAdminUserKnowledgeBases(userId, params) {
  return http.get(`/admin/users/${userId}/knowledge-bases`, { params })
}
