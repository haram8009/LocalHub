import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const tourismService = vi.hoisted(() => ({
  findLocationByName: vi.fn(),
  getLocation: vi.fn(),
  getLocationDetail: vi.fn(),
}))
const postsService = vi.hoisted(() => ({
  deletePost: vi.fn(),
  getPost: vi.fn(),
  getSimilarPosts: vi.fn(),
}))

vi.mock('@/services/locationService', async (importOriginal) => ({
  ...(await importOriginal()),
  ...tourismService,
}))
vi.mock('@/services/posts', async (importOriginal) => ({
  ...(await importOriginal()),
  ...postsService,
}))

import PostDetailView from '@/views/PostDetailView.vue'

const postedLocation = {
  id: 'demo-1-1',
  contentId: 'demo-1-1',
  contentTypeId: '12',
  name: '해운대해수욕장',
  category: '관광지',
  categoryLabel: '관광지',
  address: '부산광역시 해운대구 해운대해변로 264',
  latitude: 35.1587,
  longitude: 129.1583,
}

async function mountPostDetail() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/posts/:postId', component: PostDetailView },
      { path: '/posts', component: { template: '<div />' } },
      { path: '/map', component: { template: '<div />' } },
    ],
  })
  await router.push('/posts/1')
  await router.isReady()

  const wrapper = mount(PostDetailView, {
    global: {
      plugins: [createPinia(), router],
      stubs: { CourseKakaoMap: true },
    },
  })
  await flushPromises()

  return { wrapper, router }
}

describe('PostDetailView place photos', () => {
  beforeEach(() => {
    tourismService.findLocationByName.mockReset()
    tourismService.getLocation.mockReset()
    tourismService.getLocationDetail.mockReset()
    postsService.deletePost.mockReset()
    postsService.getPost.mockReset()
    postsService.getSimilarPosts.mockReset()
    postsService.getSimilarPosts.mockResolvedValue({ items: [] })

    postsService.getPost.mockResolvedValue({
      id: 1,
      categoryLabel: '여행 코스',
      title: '부산 하루 코스',
      content: '해운대에서 보낸 하루',
      authorId: 'someone-else',
      authorName: '여행자',
      createdAt: '2026-10-01T00:00:00.000Z',
      locations: [postedLocation],
    })
    tourismService.getLocation.mockRejectedValue(new Error('관광지 ID 없음'))
    tourismService.findLocationByName.mockResolvedValue({
      ...postedLocation,
      contentId: 'real-tourism-id',
      thumbnailImageUrl: 'https://example.com/cover.jpg',
      originalImageUrl: 'https://example.com/original.jpg',
    })
    tourismService.getLocationDetail.mockResolvedValue({
      overview: '공식 관광지 소개',
      images: ['https://example.com/detail-1.jpg', 'https://example.com/detail-2.jpg'],
    })
  })

  it('loads a single tourism API photo and keeps the description collapsed', async () => {
    const { wrapper, router } = await mountPostDetail()

    expect(tourismService.findLocationByName).toHaveBeenCalledWith(
      postedLocation.name,
      postedLocation.address,
    )
    expect(tourismService.getLocationDetail).toHaveBeenCalledWith(
      expect.objectContaining({ contentId: 'real-tourism-id' }),
    )
    expect(wrapper.find('.place-card h3').text()).toBe(postedLocation.name)
    expect(wrapper.findAll('.place-gallery img')).toHaveLength(1)
    expect(wrapper.find('.place-gallery img').attributes('src')).toBe(
      'https://example.com/cover.jpg',
    )
    expect(wrapper.text()).toContain('공식 관광지 소개')
    expect(wrapper.find('.place-description').classes()).not.toContain(
      'place-description--expanded',
    )
    expect(wrapper.text()).not.toContain('상세보기')

    await wrapper.find('.place-actions .primary').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toMatchObject({
      placeId: 'real-tourism-id',
      postPlaceId: 'demo-1-1',
    })
  })

  it('shows recommendation scores and reasons and loads a new route in the same view', async () => {
    postsService.getSimilarPosts.mockResolvedValue({
      items: [
        {
          id: 2,
          title: '비슷한 부산 코스',
          score: 0.66,
          commonPlaceCount: 3,
          lcsLength: 3,
          locationCount: 4,
          authorName: '다른 여행자',
        },
      ],
    })
    const { wrapper, router } = await mountPostDetail()
    expect(wrapper.find('.similar-card').attributes('href')).toBe('/posts/2')
    expect(wrapper.find('.similar-card').text()).toContain('유사도 66%')
    expect(wrapper.find('.similar-card').text()).toContain('공통 관광지 3곳 · 같은 방문 순서 3곳')
    await router.push('/posts/2')
    await flushPromises()
    expect(postsService.getPost).toHaveBeenLastCalledWith('2')
    expect(postsService.getSimilarPosts).toHaveBeenLastCalledWith(
      '2',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('keeps the detail usable when recommendations fail and supports retry', async () => {
    postsService.getSimilarPosts.mockRejectedValueOnce(new Error('추천 서버 오류'))
    const { wrapper } = await mountPostDetail()
    expect(wrapper.find('h1').text()).toBe('부산 하루 코스')
    expect(wrapper.find('.similar-error').text()).toContain('추천 서버 오류')
    await wrapper.find('.similar-error button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.similar-empty').exists()).toBe(true)
  })
})
