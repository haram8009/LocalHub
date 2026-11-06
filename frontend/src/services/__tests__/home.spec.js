import { beforeEach, describe, expect, it, vi } from 'vitest'

const postsService = vi.hoisted(() => ({ getPosts: vi.fn(), getNearbyPosts: vi.fn() }))

vi.mock('@/services/posts', () => postsService)

import { getRecommendedPosts } from '@/services/home'

describe('home recommendations', () => {
  beforeEach(() => {
    postsService.getNearbyPosts.mockReset()
    postsService.getNearbyPosts.mockResolvedValue({ items: [{ id: 1, title: '주변 코스 게시글' }] })
  })

  it('requests posts containing a nearby place using the current coordinates', async () => {
    const position = { latitude: 35.16, longitude: 129.16 }

    await expect(getRecommendedPosts(3, position)).resolves.toEqual([{ id: 1, title: '주변 코스 게시글' }])
    expect(postsService.getNearbyPosts).toHaveBeenCalledWith(position, { limit: 3 })
  })

  it('requires current coordinates', async () => {
    await expect(getRecommendedPosts()).rejects.toThrow('현재 위치를 확인할 수 없어')
    expect(postsService.getNearbyPosts).not.toHaveBeenCalled()
  })
})
