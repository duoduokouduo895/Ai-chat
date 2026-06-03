import { defineStore } from 'pinia'

import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, getToken, setToken as persistToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    username: '',
    userLoaded: false,
    loginLoading: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
  },
  actions: {
    setToken(token) {
      this.token = token || ''

      if (token) {
        persistToken(token)
        return
      }

      clearToken()
    },
    async login(payload) {
      this.loginLoading = true

      try {
        const response = await loginApi(payload)
        const token = response?.token

        if (!token) {
          throw new Error('登录成功但未返回 Token')
        }

        this.setToken(token)
        await this.fetchCurrentUser()
        return response
      } finally {
        this.loginLoading = false
      }
    },
    async fetchCurrentUser() {
      if (!this.token) {
        throw new Error('当前没有可用的 Token')
      }

      const response = await getCurrentUser()
      this.username = response?.username || ''
      this.userLoaded = true
      return response
    },
    async logout() {
      try {
        if (this.token) {
          await logoutApi()
        }
      } finally {
        this.clearAuth()
      }
    },
    clearAuth() {
      this.token = ''
      this.username = ''
      this.userLoaded = false
      this.loginLoading = false
      clearToken()
    },
  },
})
