import http from '@/service/http'

export function login(data) {
  return http.post('/admin/auth/login', data)
}

export function guestLogin() {
  return http.post('/users/guest')
}

export function getCurrentUser() {
  return http.get('/admin/auth/me')
}

export function logout() {
  return http.post('/admin/auth/logout')
}
