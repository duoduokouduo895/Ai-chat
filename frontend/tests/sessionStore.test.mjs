import assert from 'node:assert/strict';
import {
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
  setToken
} from '../src/services/sessionStore.mjs';

const createLocalStorage = () => {
  const values = new Map();
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
    clear() {
      values.clear();
    }
  };
};

globalThis.localStorage = createLocalStorage();

{
  setToken('  abc.def  ');
  assert.equal(getToken(), 'abc.def');
  assert.equal(isLoggedIn(), true);
  clearToken();
  assert.equal(getToken(), null);
  assert.equal(isLoggedIn(), false);
}

{
  markGuestSession(' guest-token ', 12345);
  assert.equal(getToken(), 'guest-token');
  assert.equal(isGuest(), true);
  assert.equal(getGuestMessageCount(), 0);
  assert.equal(getGuestSessionStartedAt(), 12345);
  assert.equal(incrementGuestMessageCount(), 1);
  assert.equal(getGuestMessageCount(), 1);
}

{
  setGuestMessageCount(-10);
  assert.equal(getGuestMessageCount(), 0);
  setGuestMessageCount(4);
  assert.equal(getGuestMessageCount(), 4);
}

{
  localStorage.setItem('guestId', 'guest-1');
  assert.equal(getGuestId(), 'guest-1');
  clearGuestId();
  assert.equal(getGuestId(), null);
}

{
  markGuestSession('guest-token', 10);
  localStorage.setItem('guestId', 'guest-1');
  markRegisteredSession('registered-token');
  assert.equal(getToken(), 'registered-token');
  assert.equal(getGuestId(), null);
  assert.equal(isGuest(), false);
  assert.equal(getGuestMessageCount(), 0);
  assert.equal(getGuestSessionStartedAt(), 0);
}

{
  markGuestSession('guest-token', 10);
  clearGuestState();
  assert.equal(getToken(), 'guest-token');
  assert.equal(isGuest(), false);
  assert.equal(getGuestMessageCount(), 0);
}

{
  markGuestSession('guest-token', 10);
  localStorage.setItem('user', '{"name":"Dduo"}');
  clearSessionState();
  assert.equal(getToken(), null);
  assert.equal(isGuest(), false);
  assert.equal(getGuestMessageCount(), 0);
  assert.equal(localStorage.getItem('user'), null);
}
