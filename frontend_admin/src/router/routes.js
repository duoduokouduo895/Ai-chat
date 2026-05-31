import AuthLayout from '@/layouts/AuthLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const routes = [
  {
    path: '/login',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'login',
        component: () => import('@/views/auth/LoginView.vue'),
        meta: {
          title: '登录',
        },
      },
    ],
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    meta: {
      requiresAuth: true,
    },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: {
          title: '首页',
          menuKey: 'dashboard',
        },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UserManagementView.vue'),
        meta: {
          title: '用户管理',
          menuKey: 'users',
        },
      },
      {
        path: 'sessions',
        name: 'sessions',
        component: () => import('@/views/sessions/SessionManagementView.vue'),
        meta: {
          title: '会话管理',
          menuKey: 'sessions',
        },
      },
      {
        path: 'knowledge/bases',
        name: 'knowledge-bases',
        component: () => import('@/views/knowledge/KnowledgeBaseView.vue'),
        meta: {
          title: '知识库管理',
          menuKey: 'knowledge',
        },
      },
      {
        path: 'knowledge/documents',
        name: 'knowledge-documents',
        component: () => import('@/views/knowledge/KnowledgeDocumentView.vue'),
        meta: {
          title: '文档管理',
          menuKey: 'knowledge',
        },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/views/search/SearchManagementView.vue'),
        meta: {
          title: '搜索管理',
          menuKey: 'search',
        },
      },
      {
        path: 'models',
        name: 'models',
        component: () => import('@/views/model/ModelManagementView.vue'),
        meta: {
          title: '模型管理',
          menuKey: 'models',
        },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: {
          title: '个人中心',
          menuKey: 'profile',
        },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: {
      title: '无权限',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: {
      title: '页面不存在',
    },
  },
]

export default routes
