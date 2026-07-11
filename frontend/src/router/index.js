import { createRouter, createWebHistory } from 'vue-router';
import { userApiService } from '../services/userApi.js';
import guestSessionService from '../services/guestSessionService.js';
import {
  clearGuestState,
  clearToken,
  isGuest
} from '../services/sessionStore.mjs';

const MarketingPlatform = () => import('../components/layout/MarketingPlatform.vue');
const Login = () => import('../components/login/Login.vue');
const Register = () => import('../components/login/Register.vue');

const routes = [
  {
    path: '/',
    name: 'Home',
    component: MarketingPlatform,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const isLoggedIn = userApiService.isLoggedIn();

  console.log(`Route guard: from=${from.path} to=${to.path} isLoggedIn=${isLoggedIn}`);

  if (to.meta.requiresAuth) {
    if (isLoggedIn) {
      next();
      return;
    }

    if (to.path === '/') {
      userApiService.loginGuest().then(result => {
        if (result.success) {
          next();
        } else {
          console.error('Guest login failed:', result.message);
          next('/login');
        }
      });
      return;
    }

    next('/login');
    return;
  }

  if (to.name === 'Login' || to.name === 'Register') {
    const hasGuestSession = isGuest();

    if (isLoggedIn && !hasGuestSession) {
      next('/');
      return;
    }

    if (hasGuestSession && to.name === 'Register') {
      // 注册页会使用当前游客 Token 升级同一账户，不能在路由阶段清理它。
      next();
      return;
    }

    if (hasGuestSession) {
      clearToken();
      clearGuestState();
      guestSessionService.clearGuestSession();
    }

    next();
    return;
  }

  next();
});

export default router;
