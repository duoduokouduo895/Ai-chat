import { createRouter, createWebHistory } from 'vue-router';
import { userApiService } from '../services/userApi.js';
import guestSessionService from '../services/guestSessionService.js';

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
    const isGuest = localStorage.getItem('isGuest') === 'true';

    if (isLoggedIn && !isGuest) {
      next('/');
      return;
    }

    if (isGuest) {
      localStorage.removeItem('token');
      localStorage.removeItem('isGuest');
      localStorage.removeItem('guestMessageCount');
      guestSessionService.clearGuestSession();
    }

    next();
    return;
  }

  next();
});

export default router;
