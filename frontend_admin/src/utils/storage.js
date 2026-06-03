export function getStorage(key, fallback = '') {
  const rawValue = window.localStorage.getItem(key)
  return rawValue ?? fallback
}

export function setStorage(key, value) {
  window.localStorage.setItem(key, value)
}

export function removeStorage(key) {
  window.localStorage.removeItem(key)
}
