import heartbeatService from './heartbeatService.js';
import { ElMessage } from 'element-plus/es/components/message/index';
import {
  clearGuestState,
  clearSessionState,
  getGuestSessionStartedAt,
  isGuest,
  setGuestSessionStartedAt
} from './sessionStore.mjs';

const GUEST_SESSION_MAX_MS = 3 * 60 * 60 * 1000;
const GUEST_SESSION_CHECK_INTERVAL_MS = 60 * 1000;

class GuestSessionService {
  constructor() {
    this.intervalId = null;
    this.hasShownExpiryNotice = false;
  }

  markGuestSessionStarted(startedAt = Date.now()) {
    setGuestSessionStartedAt(startedAt);
    this.hasShownExpiryNotice = false;
  }

  clearGuestSession() {
    this.stop();
    clearGuestState();
    this.hasShownExpiryNotice = false;
  }

  isGuestSessionExpired() {
    if (!isGuest()) {
      return false;
    }

    const startedAt = getGuestSessionStartedAt();
    if (!startedAt) {
      return false;
    }

    return Date.now() - startedAt >= GUEST_SESSION_MAX_MS;
  }

  enforceIfExpired() {
    if (!this.isGuestSessionExpired()) {
      return false;
    }

    this.stop();
    heartbeatService.stop();
    clearSessionState();
    this.clearGuestSession();

    if (!this.hasShownExpiryNotice) {
      this.hasShownExpiryNotice = true;
      ElMessage.warning('游客会话已过期，请重新体验或登录后继续使用。');
    }

    window.location.href = '/login';
    return true;
  }

  start() {
    this.stop();
    if (!isGuest()) {
      return;
    }

    this.enforceIfExpired();
    this.intervalId = window.setInterval(() => {
      this.enforceIfExpired();
    }, GUEST_SESSION_CHECK_INTERVAL_MS);
  }

  stop() {
    if (this.intervalId) {
      window.clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  syncWithAppState() {
    if (isGuest()) {
      this.start();
      return;
    }
    this.stop();
  }
}

export const guestSessionService = new GuestSessionService();
export default guestSessionService;
