import { getStorage, removeStorage, setStorage } from './storage'

const TOKEN_KEY = 'admin_access_token'

export function getToken() {
  return getStorage(TOKEN_KEY, '')
}

export function setToken(token) {
  setStorage(TOKEN_KEY, token)
}

export function clearToken() {
  removeStorage(TOKEN_KEY)
}
