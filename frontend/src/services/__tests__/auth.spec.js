import { beforeEach, describe, expect, it, vi } from 'vitest'
const { backendRequest, clearAccessToken, refreshAccessToken, setAccessToken } = vi.hoisted(() => ({
  backendRequest: vi.fn(),
  clearAccessToken: vi.fn(),
  refreshAccessToken: vi.fn(),
  setAccessToken: vi.fn(),
}))
vi.mock('@/services/backend', () => ({
  backendRequest,
  clearAccessToken,
  refreshAccessToken,
  setAccessToken,
}))
import * as auth from '@/services/auth'

const user = { id: 'tester', name: '여행자', email: 'test@example.com' }

describe('auth API integration adapter', () => {
  beforeEach(async () => {
    backendRequest.mockResolvedValue(null)
    await auth.logout()
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })
  it('restores the user via the refresh cookie and ignores forged browser storage', async () => {
    sessionStorage.setItem('enjoytrip:session', JSON.stringify({ id: 'forged' }))
    expect(auth.getSessionUser()).toBeNull()
    refreshAccessToken.mockResolvedValue({ accessToken: 'a1', user })
    expect(await auth.restoreSession()).toEqual(user)
    expect(auth.getSessionUser()).toEqual(user)
    expect(setAccessToken).toHaveBeenCalledWith('a1')
  })
  it('treats 401 as logged out but propagates network failures', async () => {
    refreshAccessToken.mockRejectedValue({ status: 401 })
    expect(await auth.restoreSession()).toBeNull()
    refreshAccessToken.mockRejectedValue(new Error('offline'))
    await expect(auth.restoreSession()).rejects.toThrow('offline')
  })
  it('sends signup and login to the server and keeps the token only in memory', async () => {
    const body = {
      userId: 'tester',
      password: 'Trip1234!',
      name: '여행자',
      email: 'test@example.com',
    }
    backendRequest.mockResolvedValue(user)
    await auth.signUp(body)
    expect(backendRequest).toHaveBeenCalledWith('/auth/signup', { method: 'POST', body })
    backendRequest.mockResolvedValue({ accessToken: 'a2', user })
    expect(await auth.login(body)).toEqual(user)
    expect(setAccessToken).toHaveBeenCalledWith('a2')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
  it('swaps in the new token after a password change', async () => {
    backendRequest.mockResolvedValue({ accessToken: 'a3', user })
    await auth.changePassword({ currentPassword: 'a', newPassword: 'b' })
    expect(setAccessToken).toHaveBeenCalledWith('a3')
  })
  it('clears the transient user and access token after logout', async () => {
    backendRequest.mockResolvedValue({ accessToken: 'a2', user })
    await auth.login({ userId: 'tester', password: 'Trip1234!' })
    await auth.logout()
    expect(auth.getSessionUser()).toBeNull()
    expect(clearAccessToken).toHaveBeenCalled()
  })
  it('passes field errors through to the form', async () => {
    backendRequest.mockRejectedValue({ status: 409, data: { field: 'email' } })
    await expect(auth.signUp({})).rejects.toMatchObject({ status: 409, data: { field: 'email' } })
  })
})
