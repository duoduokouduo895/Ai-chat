// src/services/apiClient.js
import axios from 'axios';
import { API_BASE_URL } from '../config/api.js';
import guestSessionService from './guestSessionService.js';
import authSessionService from './authSessionService.js';
import { normalizeApiError } from './apiErrorService.js';

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

const PUBLIC_AUTH_ENDPOINTS = ['/users/register', '/users/login', '/users/guest'];

const isPublicAuthRequest = (config = {}) => {
  if (config.skipAuth === true) {
    return true;
  }

  const url = config.url || '';
  return PUBLIC_AUTH_ENDPOINTS.some(endpoint => url.endsWith(endpoint));
};

// 请求拦截器：统一添加认证Token
apiClient.interceptors.request.use(
  (config) => {
    const token = authSessionService.getToken();

    if (isPublicAuthRequest(config)) {
      delete config.headers.Authorization;
      delete config.headers.authorization;
    } else if (token) {
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
    authSessionService.applyRefreshedToken(response.headers);

    guestSessionService.enforceIfExpired();
    return response;
  },
  async (error) => {
    error.normalized = normalizeApiError(error);

    // 只处理响应错误
    if (error.response) {
      const { status } = error.response;

      // 当遇到401 Unauthorized错误时
      if (status === 401 && !isPublicAuthRequest(error.config)) {
        await authSessionService.handleUnauthorized();
      }
    }
    
    // 无论如何，都将错误继续抛出，以便调用方可以进行局部处理（如果需要）
    return Promise.reject(error);
  }
);

export default apiClient;
