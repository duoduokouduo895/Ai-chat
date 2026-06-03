import http from '@/service/http'

export function getSearchHistory() {
  return http.get('/search/history')
}

export function clearSearchHistory() {
  return http.delete('/search/history')
}

export function getSearchIndexStatus() {
  return http.get('/search/index-status')
}

export function getAdminSearchOverview() {
  return http.get('/admin/search/overview')
}

export function getAdminSearchHistory(params) {
  return http.get('/admin/search/history', { params })
}

export function rebuildSearchIndexes() {
  return http.post('/admin/search/rebuild-indexes')
}

export function indexAllConversations() {
  return http.post('/admin/search/index-all-conversations')
}
