import axios from 'axios'

import router from '@/router'
import { useAuthStore } from '@/stores/modules/auth'
import { showErrorMessage } from '@/utils/message'
import { REFRESHED_TOKEN_HEADER } from './types'
import { extractMessage, unwrapResponse } from './normalize'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()

  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }

  return config
})

http.interceptors.response.use(
  (response) => {
    const authStore = useAuthStore()
    const refreshedToken = response.headers[REFRESHED_TOKEN_HEADER]

    if (refreshedToken) {
      authStore.setToken(refreshedToken)
    }

    return unwrapResponse(response.data)
  },
  async (error) => {
    const authStore = useAuthStore()
    const status = error?.response?.status
    const payload = error?.response?.data
    const message = extractMessage(payload || error.message)

    if (status === 401) {
      authStore.clearAuth()

      if (router.currentRoute.value.path !== '/login') {
        await router.push({
          path: '/login',
          query: {
            redirect: router.currentRoute.value.fullPath,
          },
        })
      }

      showErrorMessage(message || '登录状态已失效')
      return Promise.reject(error)
    }

    if (status === 403) {
      if (router.currentRoute.value.path !== '/403') {
        await router.push('/403')
      }

      showErrorMessage(message || '没有访问权限')
      return Promise.reject(error)
    }

    showErrorMessage(message)
    return Promise.reject(error)
  },
)

export default http
