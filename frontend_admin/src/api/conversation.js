import http from '@/service/http'

export function getRecentConversations() {
  return http.get('/conversations/recent')
}

export function getAdminConversations(params) {
  return http.get('/conversations/admin', { params })
}

export function getAdminConversationPage(params) {
  return http.get('/conversations/admin/page', { params })
}

export function getConversationMessages(conversationId) {
  return http.get(`/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId) {
  return http.delete(`/conversations/${conversationId}`)
}

export function deleteConversationAsAdmin(conversationId) {
  return http.delete(`/conversations/admin/${conversationId}`)
}
