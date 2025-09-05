import { createRouter, createWebHistory } from 'vue-router';
import MarketingPlatform from '../components/layout/MarketingPlatform.vue';
import Login from '../components/login/Login.vue';
import Register from '../components/login/Register.vue';
import { userApiService } from '../services/userApi.js';

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

// 异步路由守卫
router.beforeEach(async (to, from, next) => {
  const isLoggedIn = userApiService.isLoggedIn();

  // 目标路由需要认证
  if (to.meta.requiresAuth) {
    if (isLoggedIn) {
      // 如果已登录，直接放行
      next();
    } else {
      // 如果未登录，尝试获取游客身份
      try {
        console.log("路由守卫：未登录，尝试获取游客身份...");
        const result = await userApiService.createGuest();
        if (result.success) {
          console.log("路由守卫：游客身份获取成功，放行当前导航。");
          // 成功获取游客token后，直接放行当前导航
          next();
        } else {
          // 获取游客身份失败，跳转到登录页
          console.error("路由守卫：游客身份获取失败，跳转到登录页。");
          next('/login');
        }
      } catch (error) {
        console.error("路由守卫：调用游客接口异常，跳转到登录页。", error);
        next('/login');
      }
    }
  } 
  // 目标路由是登录/注册页
  else if (to.name === 'Login' || to.name === 'Register') {
    if (userApiService.isRegisteredUser()) {
      // 如果是注册用户，则不应访问登录/注册页，跳转到首页
      next('/');
    } else {
      // 如果是游客或未登录，正常访问登录/注册页
      next();
    }
  } 
  // 其他不需要认证的页面，直接放行
  else {
    next();
  }
});

export default router;