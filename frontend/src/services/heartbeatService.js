import apiClient from './apiClient.js';

class HeartbeatService {
  constructor() {
    this.heartbeatInterval = null;
    this.isRunning = false;
    this.intervalMs = 4 * 60 * 1000;
  }

  start() {
    if (this.isRunning || !this.shouldRun()) {
      return;
    }

    this.isRunning = true;
    this.sendHeartbeat();
    this.heartbeatInterval = setInterval(() => {
      this.sendHeartbeat();
    }, this.intervalMs);
  }

  stop() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    this.isRunning = false;
  }

  restart() {
    this.stop();
    this.start();
  }

  syncWithAppState() {
    if (this.shouldRun()) {
      this.start();
      return;
    }
    this.stop();
  }

  shouldRun() {
    return !!localStorage.getItem('token') && document.visibilityState === 'visible';
  }

  async sendHeartbeat() {
    if (!this.shouldRun()) {
      this.stop();
      return;
    }

    try {
      await apiClient.get('/users/me', {
        timeout: 10000
      });
    } catch (error) {
      if (error.code === 'ECONNABORTED' && !error.response) {
        return;
      }

      if (error.response?.status === 401) {
        this.stop();
      }

      throw error;
    }
  }
}

export const heartbeatService = new HeartbeatService();
export default heartbeatService;
