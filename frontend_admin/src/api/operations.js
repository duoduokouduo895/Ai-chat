import http from '@/service/http'

export function getOperationsSummary(params) {
  return http.get('/admin/operations/summary', { params })
}

export function getOperationsIncidents(params) {
  return http.get('/admin/operations/incidents', { params })
}

export function getOperationsIncident(incidentId) {
  return http.get(`/admin/operations/incidents/${incidentId}`)
}

export function getOperationsReport(incidentId) {
  return http.get(`/admin/operations/incidents/${incidentId}/report`)
}
