import {
  backendRequest,
  clearAccessToken,
  refreshAccessToken,
  setAccessToken,
} from '@/services/backend'

let sessionUser = null
export function getSessionUser() {
  return sessionUser
}
// 로그인·토큰 재발급 응답({ accessToken, user })을 반영합니다.
function adopt(data) {
  setAccessToken(data.accessToken)
  sessionUser = data.user
  return sessionUser
}
function clear() {
  sessionUser = null
  clearAccessToken()
}
export async function restoreSession() {
  try {
    // 새로고침 직후에는 메모리에 토큰이 없으므로 리프레시 쿠키로 복구합니다.
    adopt(await refreshAccessToken())
  } catch (error) {
    clear()
    if (error.status !== 401) throw error
  }
  return sessionUser
}
export const signUp = (body) => backendRequest('/auth/signup', { method: 'POST', body })
export async function login(body) {
  return adopt(await backendRequest('/auth/login', { method: 'POST', body }))
}
export async function logout() {
  try {
    await backendRequest('/auth/logout', { method: 'POST' })
  } finally {
    clear()
  }
}
export const findUserId = (body) => backendRequest('/auth/find-id', { method: 'POST', body })
export const verifyResetTarget = (body) =>
  backendRequest('/auth/reset-target', { method: 'POST', body })
export async function resetPassword(body) {
  await backendRequest('/auth/reset-password', { method: 'POST', body })
  clear()
}
export async function updateProfile(body) {
  sessionUser = await backendRequest('/auth/profile', { method: 'PUT', body })
  return sessionUser
}
// 비밀번호를 바꾸면 기존 토큰이 모두 무효화되므로 응답의 새 토큰으로 교체합니다.
export async function changePassword(body) {
  adopt(await backendRequest('/auth/password', { method: 'PUT', body }))
}
export async function withdraw(password) {
  await backendRequest('/auth/me', { method: 'DELETE', body: { password } })
  clear()
}
