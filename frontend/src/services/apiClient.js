// src/services/apiClient.js
import axios from 'axios';
import { API_BASE_URL } from '../config/api.js';

// 此处不再导入router，以避免循环依赖
// import router from '../router/index.js';

// 创建一个统一的、可配置的axios实例
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000, // 10秒超时
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器：统一添加认证Token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    // 如果请求数据是FormData，则删除Content-Type让浏览器自动设置
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器：统一处理全局错误，特别是401
apiClient.interceptors.response.use(
  (response) => {
    // 如果后端在响应头中返回了新的token，则更新它
    const refreshedToken = response.headers['x-refreshed-token'];
    if (refreshedToken) {
      console.log('Token has been refreshed by the server.');
      // 修复：去除token中的空白字符，防止JWT解析错误
      const cleanToken = refreshedToken.trim();
      localStorage.setItem('token', cleanToken);
    }
    return response;
  },
  (error) => {
    // 只处理响应错误
    if (error.response) {
      const { status } = error.response;

      // 当遇到401 Unauthorized错误时
      if (status === 401) {
        console.error('Unauthorized access (401). Token may be expired or invalid. Redirecting to login.');
        
        // 停止心跳服务
        import('./heartbeatService.js').then(heartbeatModule => {
          heartbeatModule.default.stop();
        });
        
        // 清除本地存储的用户凭证
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('guestId');

        // **关键改动**：动态导入router以解决循环依赖问题
        import('../router/index.js').then(routerModule => {
          const router = routerModule.default;
          router.push('/login').catch(err => {
            console.error('Error navigating to login page:', err);
          });
        });
      }
    }
    
    // 无论如何，都将错误继续抛出，以便调用方可以进行局部处理（如果需要）
    return Promise.reject(error);
  }
);

export default apiClient;
