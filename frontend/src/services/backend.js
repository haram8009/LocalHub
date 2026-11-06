import { apiRequest } from '@/services/api'

const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
let csrfRequest
export function clearCsrf() {
  csrfRequest = null
}

export async function backendRequest(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    csrfRequest ??= apiRequest(`${base}/auth/csrf`, {
      credentials: 'include',
      showLoading: false,
    }).catch((error) => {
      csrfRequest = null
      throw error
    })
    const csrf = await csrfRequest
    headers.set(csrf.headerName, csrf.token)
  }
  try {
    return await apiRequest(`${base}${path}`, {
      ...options,
      method,
      headers,
      credentials: 'include',
    })
  } catch (error) {
    if (error.status === 403) clearCsrf()
    throw error
  }
}

export function queryString(params = {}) {
  return new URLSearchParams(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  ).toString()
}
