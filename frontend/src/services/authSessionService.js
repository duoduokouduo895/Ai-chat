import {
  clearSessionState,
  getToken,
  setToken
} from './sessionStore.mjs';

const REFRESHED_TOKEN_HEADER = 'x-refreshed-token';

const clearSession = () => {
  clearSessionState();
};

const clearGuestSession = async () => {
  const guestSessionModule = await import('./guestSessionService.js');
  guestSessionModule.default.clearGuestSession();
};

const stopHeartbeat = async () => {
  const heartbeatModule = await import('./heartbeatService.js');
  heartbeatModule.default.stop();
};

const redirectToLogin = async () => {
  const routerModule = await import('../router/index.js');
  const router = routerModule.default;

  if (router.currentRoute.value.path !== '/login') {
    await router.push('/login').catch(() => {});
  }
};

const handleUnauthorized = async () => {
  await stopHeartbeat().catch(() => {});
  clearSession();
  await clearGuestSession().catch(() => {});
  await redirectToLogin().catch(() => {
    window.location.href = '/login';
  });
};

const applyRefreshedToken = (headers) => {
  if (!headers) {
    return;
  }

  const refreshedToken = typeof headers.get === 'function'
    ? headers.get(REFRESHED_TOKEN_HEADER)
    : headers[REFRESHED_TOKEN_HEADER];

  if (refreshedToken) {
    setToken(refreshedToken);
  }
};

const buildAuthHeaders = (headers = {}) => {
  const token = getToken();
  return token ? { ...headers, Authorization: `Bearer ${token}` } : headers;
};

export default {
  applyRefreshedToken,
  buildAuthHeaders,
  clearSession,
  getToken,
  handleUnauthorized,
  setToken
};
