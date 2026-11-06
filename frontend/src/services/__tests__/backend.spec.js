import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { backendRequest, clearCsrf } from '@/services/backend'

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  })
describe('backend HTTP session and CSRF integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearCsrf()
    vi.stubGlobal('fetch', vi.fn())
  })
  it('obtains a server CSRF token and sends cookies and JSON on mutations', async () => {
    fetch
      .mockResolvedValueOnce(json({ token: 'server-token', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json({ id: 'tester' }))
      .mockResolvedValueOnce(json({ id: 1 }))
    await backendRequest('/auth/login', {
      method: 'POST',
      body: { userId: 'tester', password: 'Trip1234!' },
    })
    await backendRequest('/posts', { method: 'POST', body: { title: '코스' } })
    expect(fetch).toHaveBeenCalledTimes(3)
    expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf')
    const [, request] = fetch.mock.calls[1]
    expect(request.credentials).toBe('include')
    expect(request.headers.get('X-CSRF-TOKEN')).toBe('server-token')
    expect(request.headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(request.body).userId).toBe('tester')
  })
  it('clears a rejected token without repeating a write automatically', async () => {
    fetch
      .mockResolvedValueOnce(json({ token: 'old', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json({ message: '만료' }, 403))
      .mockResolvedValueOnce(json({ token: 'new', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json({}))
    await expect(backendRequest('/auth/logout', { method: 'POST' })).rejects.toMatchObject({
      status: 403,
    })
    expect(fetch).toHaveBeenCalledTimes(2)
    await backendRequest('/auth/logout', { method: 'POST' })
    expect(fetch.mock.calls[3][1].headers.get('X-CSRF-TOKEN')).toBe('new')
  })
})
