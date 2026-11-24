import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { backendRequest, clearAccessToken, setAccessToken } from '@/services/backend'

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  })
describe('backend JWT integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearAccessToken()
    vi.stubGlobal('fetch', vi.fn())
  })
  it('sends the bearer token, cookies and JSON without any CSRF round trip', async () => {
    setAccessToken('access-1')
    fetch.mockResolvedValueOnce(json({ id: 1 }))
    await backendRequest('/posts', { method: 'POST', body: { title: '코스' } })
    expect(fetch).toHaveBeenCalledTimes(1)
    const [url, request] = fetch.mock.calls[0]
    expect(url).toBe('/api/posts')
    expect(request.credentials).toBe('include')
    expect(request.headers.get('Authorization')).toBe('Bearer access-1')
    expect(request.headers.get('Content-Type')).toBe('application/json')
  })
  it('omits Authorization when there is no token', async () => {
    fetch.mockResolvedValueOnce(json([]))
    await backendRequest('/posts')
    expect(fetch.mock.calls[0][1].headers.has('Authorization')).toBe(false)
  })
  it('refreshes once on 401 and retries the original request with the new token', async () => {
    setAccessToken('expired')
    fetch
      .mockResolvedValueOnce(json({ message: '로그인이 필요합니다.' }, 401))
      .mockResolvedValueOnce(json({ accessToken: 'fresh', user: { id: 'tester' } }))
      .mockResolvedValueOnce(json({ id: 'tester' }))
    expect(await backendRequest('/auth/me')).toEqual({ id: 'tester' })
    expect(fetch.mock.calls[1][0]).toBe('/api/auth/refresh')
    expect(fetch.mock.calls[2][1].headers.get('Authorization')).toBe('Bearer fresh')
  })
  it('shares one refresh among concurrent 401s', async () => {
    setAccessToken('expired')
    fetch.mockImplementation(async (url, request) => {
      if (url === '/api/auth/refresh') return json({ accessToken: 'fresh', user: {} })
      return request.headers.get('Authorization') === 'Bearer fresh'
        ? json({ ok: true })
        : json({ message: 'x' }, 401)
    })
    await Promise.all([backendRequest('/auth/me'), backendRequest('/auth/profile')])
    expect(fetch.mock.calls.filter(([url]) => url === '/api/auth/refresh')).toHaveLength(1)
  })
  it('surfaces the original 401 when refresh fails, and never retries login', async () => {
    setAccessToken('expired')
    fetch
      .mockResolvedValueOnce(json({ message: '로그인이 필요합니다.' }, 401))
      .mockResolvedValueOnce(json({ message: '만료' }, 401))
    await expect(backendRequest('/auth/me')).rejects.toMatchObject({ status: 401 })
    expect(fetch).toHaveBeenCalledTimes(2)
    fetch.mockResolvedValueOnce(json({ message: '불일치' }, 401))
    await expect(backendRequest('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject(
      { status: 401 },
    )
    expect(fetch).toHaveBeenCalledTimes(3)
  })
})
