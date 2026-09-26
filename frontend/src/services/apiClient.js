// src/services/apiClient.js
import { createHttpClient } from './httpClient.mjs';
import { API_BASE_URL } from '../config/api.js';
import guestSessionService from './guestSessionService.js';
import authSessionService from './authSessionService.js';
import { normalizeApiError } from './apiErrorService.js';

// 此处不再导入router，以避免循环依赖
// import router from '../router/index.js';

const PUBLIC_AUTH_ENDPOINTS = ['/users/register', '/users/login', '/users/guest'];

const isPublicAuthRequest = (config = {}) => {
  if (config.skipAuth === true) {
    return true;
  }

  const url = config.url || '';
  return PUBLIC_AUTH_ENDPOINTS.some(endpoint => url.endsWith(endpoint));
};

// 统一的、可配置的请求实例（请求头键统一为小写）
const apiClient = createHttpClient({
  baseURL: API_BASE_URL,
  timeout: 10000, // 10秒超时
  headers: {
    'Content-Type': 'application/json',
  },

  // 请求拦截：统一添加认证Token；FormData 的 Content-Type 由客户端交给浏览器生成
  onRequest(config) {
    const token = authSessionService.getToken();

    if (isPublicAuthRequest(config)) {
      delete config.headers.authorization;
    } else if (token) {
      config.headers.authorization = `Bearer ${token}`;
    }
  },

  // 响应拦截：应用续期 Token 并检查游客会话
  onResponse(response) {
    authSessionService.applyRefreshedToken(response.headers);

    guestSessionService.enforceIfExpired();
    return response;
  },

  // 错误拦截：统一处理全局错误，特别是401
  async onError(error) {
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
});

export default apiClient;
