// 心跳服务 - 用于维持JWT令牌活跃状态
import apiClient from './apiClient.js';
import { userApiService } from './userApi.js';

class HeartbeatService {
  constructor() {
    this.heartbeatInterval = null;
    this.isRunning = false;
    // 心跳间隔：10分钟（600,000毫秒）
    this.intervalMs = 10 * 60 * 1000;
  }

  /**
   * 启动心跳服务
   */
  start() {
    if (this.isRunning) {
      console.log('心跳服务已经在运行');
      return;
    }

    console.log('启动JWT心跳服务，间隔：', this.intervalMs / 1000, '秒');
    this.isRunning = true;

    // 立即执行一次心跳
    this.sendHeartbeat();

    // 设置定时心跳
    this.heartbeatInterval = setInterval(() => {
      this.sendHeartbeat();
    }, this.intervalMs);
  }

  /**
   * 停止心跳服务
   */
  stop() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    this.isRunning = false;
    console.log('JWT心跳服务已停止');
  }

  /**
   * 发送心跳请求
   */
  async sendHeartbeat() {
    // 检查用户是否登录
    if (!userApiService.isLoggedIn()) {
      console.log('用户未登录，停止心跳服务');
      this.stop();
      return;
    }

    try {
      // 发送一个轻量级的请求来触发token刷新
      // 使用更轻量级的端点并增加超时时间
      const response = await apiClient.get('/conversations/recent', {
        timeout: 15000 // 增加到15秒超时
      });

      console.log('心跳请求成功，对话数量:', response.data.length);
      
      // 检查是否有刷新的token
      const refreshedToken = response.headers['x-refreshed-token'];
      if (refreshedToken) {
        console.log('💓 心跳触发了Token刷新');
        // 修复：去除token中的空白字符，防止JWT解析错误
        const cleanToken = refreshedToken.trim();
        localStorage.setItem('token', cleanToken);
      }

    } catch (error) {
      console.error('心跳请求失败:', error);
      
      // 如果是超时错误，但不是401错误，可以继续尝试
      if (error.code === 'ECONNABORTED' && !error.response) {
        console.log('心跳请求超时，但token可能仍然有效，继续保持心跳服务');
        return;
      }
      
      // 如果是401错误，说明token已完全失效，停止心跳
      if (error.response?.status === 401) {
        console.log('Token已失效，停止心跳服务');
        this.stop();
      }
    }
  }

  /**
   * 重新启动心跳服务
   */
  restart() {
    this.stop();
    this.start();
  }
}

// 导出单例实例
export const heartbeatService = new HeartbeatService();
export default heartbeatService;