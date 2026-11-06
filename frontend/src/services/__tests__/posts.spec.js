import { beforeEach, describe, expect, it, vi } from 'vitest'
const { backendRequest } = vi.hoisted(() => ({ backendRequest: vi.fn() }))
vi.mock('@/services/backend', async (original) => ({ ...(await original()), backendRequest }))
import { createPost, getPosts, getPost, updatePost, deletePost, getNearbyPosts } from '@/services/posts'
const post = {
  id: 7,
  category: 'COURSE',
  title: '부산 코스',
  content: '본문',
  authorId: 'tester',
  locations: [{ contentId: '123', name: '해운대', latitude: 35.16, longitude: 129.16 }],
}
describe('posts API integration adapter', () => {
  beforeEach(() => {
    backendRequest.mockReset()
    localStorage.clear()
  })
  it('passes filters and pagination to the server and normalizes locations', async () => {
    backendRequest.mockResolvedValue({ items: [post], total: 1, page: 2, size: 5 })
    const result = await getPosts({ query: '부산 여행', authorId: 'tester', page: 2, size: 5 })
    const url = new URL(backendRequest.mock.calls[0][0], 'http://localhost')
    expect(url.searchParams.get('query')).toBe('부산 여행')
    expect(url.searchParams.get('page')).toBe('2')
    expect(result.items[0].locations[0].name).toBe('해운대')
    expect(result.total).toBe(1)
  })
  it('sends only editable fields and ordered location snapshots', async () => {
    backendRequest.mockResolvedValue(post)
    await createPost({ ...post, authorId: 'forged', viewCount: 999 })
    const [path, options] = backendRequest.mock.calls[0]
    expect(path).toBe('/posts')
    expect(options.method).toBe('POST')
    expect(options.body).not.toHaveProperty('authorId')
    expect(options.body).not.toHaveProperty('viewCount')
    expect(options.body.locations[0].visitOrder).toBe(1)
    expect(localStorage.length).toBe(0)
  })
  it('uses server detail, update and delete endpoints', async () => {
    backendRequest.mockResolvedValue(post)
    await getPost(7)
    await updatePost(7, post)
    await deletePost(7)
    expect(backendRequest.mock.calls[0][0]).toBe('/posts/7')
    expect(backendRequest.mock.calls[1][1].method).toBe('PUT')
    expect(backendRequest.mock.calls[2]).toEqual(['/posts/7', { method: 'DELETE' }])
  })
  it('propagates server ownership failures', async () => {
    backendRequest.mockRejectedValue({ status: 403, message: '작성자만 수정·삭제할 수 있습니다.' })
    await expect(deletePost(7)).rejects.toMatchObject({ status: 403 })
  })
  it('preserves nearby post summaries and their nearest place without loading post details', async () => {
    backendRequest.mockResolvedValue({ items: [{
      ...post,
      locations: undefined,
      locationCount: 4,
      distanceKm: 0.15,
      nearestLocation: post.locations[0],
    }] })

    const result = await getNearbyPosts({ latitude: 35.16, longitude: 129.16 })
    const url = new URL(backendRequest.mock.calls[0][0], 'http://localhost')
    expect(url.pathname).toBe('/posts/nearby')
    expect(Object.fromEntries(url.searchParams)).toEqual({ latitude: '35.16', longitude: '129.16', limit: '3' })
    expect(result.items[0]).toMatchObject({ id: 7, title: '부산 코스', locationCount: 4, distanceKm: 0.15,
      nearestLocation: { contentId: '123', name: '해운대' },
    })
    expect(backendRequest).toHaveBeenCalledTimes(1)
  })
})
