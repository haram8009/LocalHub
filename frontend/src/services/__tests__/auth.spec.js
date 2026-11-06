import { beforeEach, describe, expect, it, vi } from 'vitest'
const { backendRequest, clearCsrf } = vi.hoisted(() => ({
  backendRequest: vi.fn(),
  clearCsrf: vi.fn(),
}))
vi.mock('@/services/backend', () => ({ backendRequest, clearCsrf }))
import * as auth from '@/services/auth'

describe('auth API integration adapter', () => {
  beforeEach(async () => {
    backendRequest.mockResolvedValue(null)
    await auth.logout()
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })
  it('restores the user from the server and ignores forged browser storage', async () => {
    sessionStorage.setItem('enjoytrip:session', JSON.stringify({ id: 'forged' }))
    expect(auth.getSessionUser()).toBeNull()
    const user = { id: 'tester', name: '여행자', email: 'test@example.com' }
    backendRequest.mockResolvedValue(user)
    expect(await auth.restoreSession()).toEqual(user)
    expect(auth.getSessionUser()).toEqual(user)
    expect(backendRequest).toHaveBeenCalledWith('/auth/me', { showLoading: false })
  })
  it('treats 401 as logged out but propagates network failures', async () => {
    backendRequest.mockRejectedValue({ status: 401 })
    expect(await auth.restoreSession()).toBeNull()
    backendRequest.mockRejectedValue(new Error('offline'))
    await expect(auth.restoreSession()).rejects.toThrow('offline')
  })
  it('sends signup and login to the server without persisting credentials', async () => {
    const body = {
      userId: 'tester',
      password: 'Trip1234!',
      name: '여행자',
      email: 'test@example.com',
    }
    const user = { id: 'tester', name: '여행자', email: 'test@example.com' }
    backendRequest.mockResolvedValue(user)
    await auth.signUp(body)
    expect(backendRequest).toHaveBeenCalledWith('/auth/signup', { method: 'POST', body })
    expect(await auth.login(body)).toEqual(user)
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
  it('clears the transient user and CSRF token after server logout', async () => {
    backendRequest.mockResolvedValue({ id: 'tester' })
    await auth.login({ userId: 'tester', password: 'Trip1234!' })
    await auth.logout()
    expect(auth.getSessionUser()).toBeNull()
    expect(clearCsrf).toHaveBeenCalled()
  })
  it('passes field errors through to the form', async () => {
    backendRequest.mockRejectedValue({ status: 409, data: { field: 'email' } })
    await expect(auth.signUp({})).rejects.toMatchObject({ status: 409, data: { field: 'email' } })
  })
})
