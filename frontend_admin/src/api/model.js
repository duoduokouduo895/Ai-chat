import http from '@/service/http'

export function getModelList() {
  return http.get('/ai/models')
}
