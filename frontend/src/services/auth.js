import { backendRequest, clearCsrf } from '@/services/backend'

let sessionUser = null
export function getSessionUser() {
  return sessionUser
}
export async function restoreSession() {
  try {
    sessionUser = await backendRequest('/auth/me', { showLoading: false })
  } catch (error) {
    sessionUser = null
    if (error.status !== 401) throw error
  }
  return sessionUser
}
export const signUp = (body) => backendRequest('/auth/signup', { method: 'POST', body })
export async function login(body) {
  sessionUser = await backendRequest('/auth/login', { method: 'POST', body })
  return sessionUser
}
export async function logout() {
  await backendRequest('/auth/logout', { method: 'POST' })
  sessionUser = null
  clearCsrf()
}
export const findUserId = (body) => backendRequest('/auth/find-id', { method: 'POST', body })
export const verifyResetTarget = (body) =>
  backendRequest('/auth/reset-target', { method: 'POST', body })
export async function resetPassword(body) {
  await backendRequest('/auth/reset-password', { method: 'POST', body })
  sessionUser = null
  clearCsrf()
}
export async function updateProfile(body) {
  sessionUser = await backendRequest('/auth/profile', { method: 'PUT', body })
  return sessionUser
}
export const changePassword = (body) => backendRequest('/auth/password', { method: 'PUT', body })
export async function withdraw(password) {
  await backendRequest('/auth/me', { method: 'DELETE', body: { password } })
  sessionUser = null
  clearCsrf()
}
