export const SESSION_KEYS = {
  token: 'token',
  user: 'user',
  guestId: 'guestId',
  isGuest: 'isGuest',
  guestMessageCount: 'guestMessageCount',
  guestSessionStartedAt: 'guestSessionStartedAt'
};

const getStorage = () => {
  if (typeof globalThis === 'undefined') {
    return null;
  }
  return globalThis.localStorage || null;
};

const getItem = (key) => {
  const storage = getStorage();
  return storage ? storage.getItem(key) : null;
};

const setItem = (key, value) => {
  const storage = getStorage();
  if (!storage) return;
  storage.setItem(key, value);
};

const removeItem = (key) => {
  const storage = getStorage();
  if (!storage) return;
  storage.removeItem(key);
};

export const getToken = () => getItem(SESSION_KEYS.token);

export const setToken = (token) => {
  if (!token) return;
  setItem(SESSION_KEYS.token, token.trim());
};

export const clearToken = () => {
  removeItem(SESSION_KEYS.token);
};

export const isLoggedIn = () => Boolean(getToken());

export const isGuest = () => getItem(SESSION_KEYS.isGuest) === 'true';

export const getGuestId = () => getItem(SESSION_KEYS.guestId);

export const clearGuestId = () => {
  removeItem(SESSION_KEYS.guestId);
};

export const markRegisteredSession = (token) => {
  setToken(token);
  removeItem(SESSION_KEYS.guestId);
  removeItem(SESSION_KEYS.isGuest);
  removeItem(SESSION_KEYS.guestMessageCount);
  removeItem(SESSION_KEYS.guestSessionStartedAt);
};

export const markGuestSession = (token, startedAt = Date.now()) => {
  setToken(token);
  setItem(SESSION_KEYS.isGuest, 'true');
  setGuestMessageCount(0);
  setItem(SESSION_KEYS.guestSessionStartedAt, String(startedAt));
};

export const getGuestMessageCount = () => {
  const count = Number.parseInt(getItem(SESSION_KEYS.guestMessageCount) || '0', 10);
  return Number.isFinite(count) ? count : 0;
};

export const setGuestMessageCount = (count) => {
  const normalizedCount = Number.isFinite(Number(count)) ? Math.max(0, Number(count)) : 0;
  setItem(SESSION_KEYS.guestMessageCount, String(normalizedCount));
};

export const incrementGuestMessageCount = () => {
  const nextCount = getGuestMessageCount() + 1;
  setGuestMessageCount(nextCount);
  return nextCount;
};

export const getGuestSessionStartedAt = () => {
  const startedAt = Number(getItem(SESSION_KEYS.guestSessionStartedAt) || 0);
  return Number.isFinite(startedAt) ? startedAt : 0;
};

export const setGuestSessionStartedAt = (startedAt = Date.now()) => {
  setItem(SESSION_KEYS.guestSessionStartedAt, String(startedAt));
};

export const clearGuestState = () => {
  removeItem(SESSION_KEYS.guestId);
  removeItem(SESSION_KEYS.isGuest);
  removeItem(SESSION_KEYS.guestMessageCount);
  removeItem(SESSION_KEYS.guestSessionStartedAt);
};

export const clearSessionState = () => {
  clearToken();
  removeItem(SESSION_KEYS.user);
  clearGuestState();
};

export default {
  SESSION_KEYS,
  clearGuestId,
  clearGuestState,
  clearSessionState,
  clearToken,
  getGuestId,
  getGuestMessageCount,
  getGuestSessionStartedAt,
  getToken,
  incrementGuestMessageCount,
  isGuest,
  isLoggedIn,
  markGuestSession,
  markRegisteredSession,
  setGuestMessageCount,
  setGuestSessionStartedAt,
  setToken
};
