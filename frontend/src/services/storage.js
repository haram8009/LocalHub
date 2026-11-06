function getStorage(type) {
  try {
    return type === 'session' ? window.sessionStorage : window.localStorage
  } catch {
    return null
  }
}

export function readJson(key, fallback = null, { type = 'local' } = {}) {
  try {
    const raw = getStorage(type)?.getItem(key)

    return raw ? JSON.parse(raw) : fallback
  } catch {
    return fallback
  }
}

export function writeJson(key, value, { type = 'local' } = {}) {
  try {
    getStorage(type)?.setItem(key, JSON.stringify(value))
    return true
  } catch {
    return false
  }
}

export function removeItem(key, { type = 'local' } = {}) {
  try {
    getStorage(type)?.removeItem(key)
  } catch {
    // 저장소 접근이 막힌 환경에서는 무시합니다.
  }
}
