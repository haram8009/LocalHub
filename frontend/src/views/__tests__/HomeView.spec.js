import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const currentLocationService = vi.hoisted(() => ({ getCurrentLocationContext: vi.fn() }))
const homeService = vi.hoisted(() => ({
  getRecentPosts: vi.fn(),
  getRecommendedPosts: vi.fn(),
}))

vi.mock('@/services/currentLocation', () => currentLocationService)
vi.mock('@/services/home', () => homeService)

import HomeView from '@/views/HomeView.vue'

describe('HomeView current-location recommendations', () => {
  beforeEach(() => {
    currentLocationService.getCurrentLocationContext.mockReset()
    homeService.getRecentPosts.mockReset().mockResolvedValue([])
    homeService.getRecommendedPosts.mockReset().mockResolvedValue([])
  })

  async function mountHome() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: HomeView },
        { path: '/map', component: { template: '<div />' } },
        { path: '/posts', component: { template: '<div />' } },
        { path: '/posts/create', component: { template: '<div />' } },
        { path: '/posts/:id', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(HomeView, {
      global: {
        plugins: [router],
      },
    })

    await flushPromises()
    return wrapper
  }

  it('keeps the map entry visible and retries location recommendations after a location error', async () => {
    currentLocationService.getCurrentLocationContext
      .mockRejectedValueOnce(
        new Error('기기의 위치 신호를 확인할 수 없습니다. 위치 설정을 확인하고 다시 시도해 주세요.'),
      )
      .mockResolvedValueOnce({
        position: { latitude: 35.16, longitude: 129.16 },
        regionName: '부산광역시 해운대구',
      })
    homeService.getRecommendedPosts.mockResolvedValue([
      {
        id: 1,
        title: '해운대 해변 코스',
        distanceKm: 0.2,
        nearestLocation: { name: '해운대해수욕장' },
      },
    ])

    const wrapper = await mountHome()

    expect(wrapper.get('[role="alert"]').text()).toContain('위치 신호를 확인할 수 없습니다')
    expect(wrapper.get('.hero-actions a[href="/map"]').text()).toContain('여행지 둘러보기')
    expect(wrapper.find('.map-preview').exists()).toBe(false)

    await wrapper.get('.retry-button').trigger('click')
    await flushPromises()

    expect(currentLocationService.getCurrentLocationContext).toHaveBeenCalledTimes(2)
    expect(homeService.getRecommendedPosts).toHaveBeenCalledWith(3, {
      latitude: 35.16,
      longitude: 129.16,
    })
    expect(wrapper.text()).toContain('해운대 해변 코스')

    await wrapper.unmount()
  })

  it('shows three nearby posts with distances and links to their details', async () => {
    currentLocationService.getCurrentLocationContext.mockResolvedValue({
      position: { latitude: 35.16, longitude: 129.16 },
      regionName: '부산광역시 해운대구',
    })
    homeService.getRecommendedPosts.mockResolvedValue([1, 2, 3].map((id) => ({
      id, title: `주변 게시글 ${id}`, content: '부산 여행 일정', authorName: '여행자',
      categoryLabel: '여행 후기', distanceKm: id === 1 ? 0.2 : id,
      nearestLocation: { name: `가까운 장소 ${id}` },
    })))

    const wrapper = await mountHome()
    const cards = wrapper.findAll('.nearby-post-card')
    expect(cards).toHaveLength(3)
    expect(cards.map((card) => card.attributes('href'))).toEqual(['/posts/1', '/posts/2', '/posts/3'])
    expect(cards[0].text()).toContain('주변 게시글 1')
    expect(cards[0].text()).toContain('가까운 장소 1')
    expect(cards[0].text()).toContain('200m')
    expect(cards[1].text()).toContain('2.0km')
    expect(wrapper.find('.map-preview').exists()).toBe(false)
    wrapper.unmount()
  })

  it('explains blocked location access and only requests posts after permission is granted', async () => {
    currentLocationService.getCurrentLocationContext
      .mockRejectedValueOnce(Object.assign(new Error('위치 접근 차단'), { code: 'LOCATION_PERMISSION_DENIED' }))
      .mockResolvedValueOnce({ position: { latitude: 35.16, longitude: 129.16 }, regionName: '부산' })

    const wrapper = await mountHome()
    expect(wrapper.get('.location-permission').text()).toContain('사이트 설정')
    expect(wrapper.get('.location-permission').text()).toContain('Windows 설정')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(homeService.getRecommendedPosts).not.toHaveBeenCalled()

    await wrapper.get('.location-permission__retry').trigger('click')
    await flushPromises()
    expect(homeService.getRecommendedPosts).toHaveBeenCalledWith(3, { latitude: 35.16, longitude: 129.16 })
    expect(wrapper.find('.location-permission').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows an empty result only after a successful lookup with no nearby posts', async () => {
    let resolvePosition
    currentLocationService.getCurrentLocationContext.mockReturnValue(new Promise((resolve) => {
      resolvePosition = resolve
    }))
    const wrapper = await mountHome()
    expect(wrapper.get('[role="status"]').text()).toContain('현재 위치를 확인')
    expect(wrapper.find('.map-preview').exists()).toBe(false)

    resolvePosition({ position: { latitude: 35.16, longitude: 129.16 }, regionName: '부산' })
    await flushPromises()
    expect(wrapper.get('.map-preview').text()).toContain('주변 장소가 포함된 게시글이 아직 없습니다')
    expect(wrapper.findAll('.nearby-post-card')).toHaveLength(0)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
