import { apiRequest } from '@/services/api'

const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

// 액세스 토큰은 XSS로 인한 탈취를 줄이기 위해 메모리에만 보관합니다.
// 새로고침 후에는 HttpOnly 리프레시 쿠키로 다시 발급받습니다.
let accessToken = null
let refreshRequest = null

export function setAccessToken(token) {
  accessToken = token || null
}

export function clearAccessToken() {
  accessToken = null
}

/**
 * 리프레시 쿠키로 새 액세스 토큰을 받습니다. 동시에 여러 요청이 만료되어도
 * 한 번만 호출합니다(리프레시 토큰은 1회용이라 중복 호출하면 실패합니다).
 */
export function refreshAccessToken() {
  refreshRequest ??= apiRequest(`${base}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
    showLoading: false,
  })
    .then((data) => {
      setAccessToken(data.accessToken)
      return data
    })
    .catch((error) => {
      if (error.status === 401) clearAccessToken()
      throw error
    })
    .finally(() => {
      refreshRequest = null
    })
  return refreshRequest
}

// 이 경로의 401은 "인증 만료"가 아니라 자격 증명 오류이므로 재시도하지 않습니다.
const NO_RETRY = ['/auth/login', '/auth/signup', '/auth/refresh', '/auth/logout']

export async function backendRequest(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const send = () => {
    const headers = new Headers(options.headers)
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    return apiRequest(`${base}${path}`, { ...options, method, headers, credentials: 'include' })
  }
  try {
    return await send()
  } catch (error) {
    if (error.status !== 401 || NO_RETRY.includes(path)) throw error
    try {
      await refreshAccessToken()
    } catch {
      throw error
    }
    return send()
  }
}

export function queryString(params = {}) {
  return new URLSearchParams(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  ).toString()
}
