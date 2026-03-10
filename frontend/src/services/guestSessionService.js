import heartbeatService from './heartbeatService.js';

const GUEST_SESSION_KEY = 'guestSessionStartedAt';
const GUEST_SESSION_MAX_MS = 3 * 60 * 60 * 1000;
const GUEST_SESSION_CHECK_INTERVAL_MS = 60 * 1000;

class GuestSessionService {
  constructor() {
    this.intervalId = null;
    this.hasShownExpiryNotice = false;
  }

  markGuestSessionStarted(startedAt = Date.now()) {
    localStorage.setItem(GUEST_SESSION_KEY, String(startedAt));
    this.hasShownExpiryNotice = false;
  }

  clearGuestSession() {
    this.stop();
    localStorage.removeItem(GUEST_SESSION_KEY);
    this.hasShownExpiryNotice = false;
  }

  isGuestSessionExpired() {
    if (localStorage.getItem('isGuest') !== 'true') {
      return false;
    }

    const startedAt = Number(localStorage.getItem(GUEST_SESSION_KEY) || 0);
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

    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('isGuest');
    localStorage.removeItem('guestMessageCount');
    this.clearGuestSession();

    if (!this.hasShownExpiryNotice) {
      this.hasShownExpiryNotice = true;
      window.alert('游客会话已过期，请重新体验或登录后继续使用。');
    }

    window.location.href = '/login';
    return true;
  }

  start() {
    this.stop();
    if (localStorage.getItem('isGuest') !== 'true') {
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
    if (localStorage.getItem('isGuest') === 'true') {
      this.start();
      return;
    }
    this.stop();
  }
}

export const guestSessionService = new GuestSessionService();
export default guestSessionService;
