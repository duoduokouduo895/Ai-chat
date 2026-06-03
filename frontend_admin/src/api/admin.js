import http from '@/service/http'

export function getDashboardStats() {
  return http.get('/admin/dashboard/stats')
}
