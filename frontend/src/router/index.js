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

// 路由守卫
router.beforeEach((to, from, next) => {
  const isLoggedIn = userApiService.isLoggedIn();

  console.log(`路由守卫: from=${from.path} to=${to.path} isLoggedIn=${isLoggedIn}`);

  // 目标路由需要认证
  if (to.meta.requiresAuth) {
    if (isLoggedIn) {
      // 如果已登录，直接放行
      console.log('用户已登录，允许访问认证页面');
      next();
    } else {
      // 如果未登录，且访问首页，尝试自动作为游客登录
      if (to.path === '/') {
        console.log('访问首页且未登录，尝试进行游客登录');
        userApiService.loginGuest().then(result => {
          if (result.success) {
            console.log('游客自动登录成功，放行到首页');
            next();
          } else {
            console.error('游客登录失败:', result.message);
            next('/login');
          }
        });
      } else {
        console.log('用户未登录，重定向到登录页');
        next('/login');
      }
    }
  }
  // 目标路由是登录/注册页
  else if (to.name === 'Login' || to.name === 'Register') {
    const isGuest = localStorage.getItem('isGuest') === 'true';

    if (isLoggedIn && !isGuest) {
      // 如果已真正登录，跳转到首页
      console.log('真实用户已登录，重定向到首页');
      next('/');
    } else {
      // 如果是游客尝试进入登录注册页，直接在此处抹除它的游客痕迹，方便后面注册新号
      if (isGuest) {
        console.log('游客前往登录注册页，自动清除游客凭证');
        localStorage.removeItem('token');
        localStorage.removeItem('isGuest');
        localStorage.removeItem('guestMessageCount');
      }
      // 允许访问登录/注册页
      console.log('用户未登录或正从游客模式退出，允许访问登录/注册页');
      next();
    }
  }
  // 其他不需要认证的页面，直接放行
  else {
    console.log('访问公开页面，直接放行');
    next();
  }
});


export default router;