// 用户API服务
import apiClient from './apiClient'; // 导入统一的apiClient
import router from '../router/index.js';
import heartbeatService from './heartbeatService.js';
import guestSessionService from './guestSessionService.js';
import authSessionService from './authSessionService.js';
import { getApiErrorMessage } from './apiErrorService.js';

// 用户API服务
export const userApiService = {
  // 用户注册
  register: async (userData) => {
    try {
      const response = await apiClient.post('/users/register', userData, { skipAuth: true });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      return {
        success: false,
        message: getApiErrorMessage(error, '注册失败，请稍后重试')
      };
    }
  },

  // 用户登录
  login: async (credentials) => {
    try {
      const response = await apiClient.post('/users/login', credentials, { skipAuth: true });
      const { token } = response.data;

      // 存储token（去除空白字符）
      const cleanToken = token.trim();
      authSessionService.setToken(cleanToken);
      localStorage.removeItem('isGuest');
      localStorage.removeItem('guestMessageCount');
      guestSessionService.clearGuestSession();

      // 登录成功后启动心跳服务
      heartbeatService.start();

      return {
        success: true,
        token: token
      };
    } catch (error) {
      return {
        success: false,
        message: error.response?.status === 401 ? '用户名或密码错误' : getApiErrorMessage(error, '登录失败，请稍后重试')
      };
    }
  },

  // 游客登录 (自动创建并获取 Token)
  loginGuest: async () => {
    try {
      const response = await apiClient.post('/users/guest', null, { skipAuth: true });
      const { token } = response.data;

      const cleanToken = token.trim();
      authSessionService.setToken(cleanToken);
      // 存储游客标记和消息计数
      localStorage.setItem('isGuest', 'true');
      localStorage.setItem('guestMessageCount', '0');
      guestSessionService.markGuestSessionStarted();

      heartbeatService.start();
      guestSessionService.start();

      return {
        success: true,
        token: token
      };
    } catch (error) {
      return {
        success: false,
        message: getApiErrorMessage(error, '游客登录失败，请稍后重试')
      };
    }
  },

  // 退出登录
  logout: async () => {
    try {
      // 停止心跳服务
      heartbeatService.stop();

      // 调用后端的登出接口，使 token 在服务器端立即失效
      // 注意：即使这里失败（例如因为token已过期导致401），全局拦截器也会处理跳转
      await apiClient.post('/users/logout');
    } catch (error) {
    } finally {

      // 无论后端调用结果如何，都必须清空本地存储并跳转到登录页
      // 全局拦截器会处理401的情况，但为了确保用户点击登出按钮总能成功退出，这里保留清理和跳转逻辑
      authSessionService.clearSession();
      guestSessionService.clearGuestSession();
      router.push('/login');
    }
  },

  // 检查登录状态
  isLoggedIn: () => {
    return !!authSessionService.getToken();
  },

  // 获取当前用户token
  getToken: () => {
    return authSessionService.getToken();
  },

  // 获取当前用户简要信息（用于心跳检测）
  getMe: async () => {
    try {
      // 使用用户信息端点进行心跳检测
      const response = await apiClient.get('/users/me');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      // 心跳检测失败会自动被401拦截器处理，所以这里只需要返回失败状态
      return {
        success: false,
        message: getApiErrorMessage(error, '用户状态获取失败')
      };
    }
  }
};

export default userApiService;
