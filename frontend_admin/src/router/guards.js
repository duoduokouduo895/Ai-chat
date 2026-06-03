import { useAuthStore } from '@/stores/modules/auth'

const WHITE_LIST = ['/login', '/403', '/404']

export function setupRouterGuards(router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()
    const hasToken = authStore.isAuthenticated

    if (to.path === '/login') {
      if (hasToken) {
        return to.query.redirect || '/dashboard'
      }

      return true
    }

    if (WHITE_LIST.includes(to.path)) {
      return true
    }

    if (!to.meta.requiresAuth) {
      return true
    }

    if (!hasToken) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }

    if (authStore.userLoaded) {
      return true
    }

    try {
      await authStore.fetchCurrentUser()
      return true
    } catch {
      authStore.clearAuth()
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  })

  router.afterEach((to) => {
    const title = to.meta?.title ? `${to.meta.title} - Dduo Admin Portal` : 'Dduo Admin Portal'
    document.title = title
  })
}
