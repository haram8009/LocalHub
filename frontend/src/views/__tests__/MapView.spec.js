import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const locationService = vi.hoisted(() => ({
  DEFAULT_AREA_CODE: '11',
  getLocation: vi.fn(),
  getLocationPage: vi.fn(),
  findLocationByName: vi.fn(),
  getLocationDetail: vi.fn(),
  getNearbyLocations: vi.fn(),
}))
const postsService = vi.hoisted(() => ({ getPosts: vi.fn() }))

vi.mock('@/services/locationService', () => locationService)
vi.mock('@/services/posts', () => postsService)

import MapView from '@/views/MapView.vue'

const fitLocations = vi.fn()

const KakaoTravelMapStub = defineComponent({
  name: 'KakaoTravelMap',
  props: ['focusLocation'],
  emits: ['map-ready', 'viewport-change', 'select-location'],
  setup(_props, { expose }) {
    expose({ fitLocations })
  },
  template: '<div class="kakao-map-stub" />',
})

const LocationFilterBarStub = defineComponent({
  name: 'LocationFilterBar',
  props: ['filters', 'loading'],
  emits: ['change', 'reset'],
  template: '<div />',
})

const MapSidePanelStub = defineComponent({
  name: 'MapSidePanel',
  props: [
    'selectedLocation',
    'visibleLocations',
    'locationsLoading',
    'locationsRefreshing',
    'totalCount',
    'currentPage',
    'totalPages',
    'listMode',
  ],
  emits: ['change-page', 'select-location', 'open-all-posts'],
  template: '<div />',
})

// 약 1.1km x 0.9km 크기의 지도 영역
const bounds = { minLat: 37.561, maxLat: 37.571, minLng: 126.972, maxLng: 126.984 }
const firstAreaItem = { id: 'a1', contentId: 'a1', latitude: 35.1, longitude: 129.1 }

function page(items, { totalCount = items.length, pageNo = 1, totalPages = 1 } = {}) {
  return { items, totalCount, page: pageNo, size: 20, totalPages }
}

function location(id, latitude = 37.566, longitude = 126.978) {
  return { id, contentId: id, latitude, longitude }
}

function callsByMode(mode) {
  return locationService.getLocationPage.mock.calls
    .map(([options]) => options)
    .filter((options) => options.mode === mode)
}

// 필터 바가 하듯이 현재 조건에서 일부만 바꾼 전체 조건을 내보냅니다.
function changeFilters(filterBar, changes) {
  filterBar.vm.$emit('change', { ...filterBar.props('filters'), ...changes })
}

async function mountView(path = '/map') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/map', component: MapView }],
  })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(MapView, {
    global: {
      plugins: [router],
      stubs: {
        KakaoTravelMap: KakaoTravelMapStub,
        LocationFilterBar: LocationFilterBarStub,
        MapSidePanel: MapSidePanelStub,
      },
    },
  })

  return { wrapper, router }
}

async function mountReadyView(path) {
  const result = await mountView(path)
  const map = result.wrapper.findComponent(KakaoTravelMapStub)

  map.vm.$emit('map-ready', bounds)
  await flushPromises()

  return { ...result, map, sidePanel: result.wrapper.findComponent(MapSidePanelStub) }
}

describe('MapView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    fitLocations.mockReset()
    Object.values(locationService).forEach((mock) => mock.mockReset?.())
    postsService.getPosts.mockReset()

    locationService.getLocationPage.mockImplementation(async ({ mode }) =>
      page(
        mode === 'area' ? [firstAreaItem] : [location('1')],
        { totalCount: 45, totalPages: 3 },
      ),
    )
    locationService.getNearbyLocations.mockResolvedValue([])
    locationService.getLocationDetail.mockResolvedValue({ overview: '', intro: [], images: [], infos: [] })
    postsService.getPosts.mockResolvedValue({ items: [] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads and displays nationwide results on first entry', async () => {
    const { wrapper, map, sidePanel } = await mountReadyView()
    const filterBar = wrapper.findComponent(LocationFilterBarStub)

    expect(callsByMode('area')).toEqual([
      expect.objectContaining({
        category: '',
        lclsSystm1: 'FD',
        areaCode: '',
        sigunguCode: '',
        page: 1,
        size: 20,
      }),
    ])
    expect(filterBar.props('filters')).toMatchObject({
      category: '전체',
      classification: { lclsSystm1: 'FD' },
      region: { sido: '', sigungu: '' },
    })
    expect(callsByMode('viewport')).toHaveLength(0)
    expect(fitLocations).toHaveBeenCalledWith([firstAreaItem])
    expect(map.props('focusLocation')).toBeNull()

    expect(sidePanel.props()).toMatchObject({
      totalCount: 45,
      currentPage: 1,
      totalPages: 3,
      listMode: 'nationwide',
    })
  })

  it('keeps API results in the list when their map coordinates are missing', async () => {
    const resultWithoutCoordinates = {
      id: 'no-coordinates',
      contentId: 'no-coordinates',
      name: '좌표 정보가 없는 코스',
      category: '여행코스',
      address: '',
      latitude: Number.NaN,
      longitude: Number.NaN,
    }
    locationService.getLocationPage.mockResolvedValue(page([resultWithoutCoordinates], { totalCount: 1 }))

    const { wrapper, sidePanel } = await mountReadyView()

    expect(sidePanel.props('totalCount')).toBe(1)
    expect(sidePanel.props('visibleLocations')).toEqual([resultWithoutCoordinates])
    expect(fitLocations).toHaveBeenCalledWith([resultWithoutCoordinates])
    expect(wrapper.text()).not.toContain('표시할 여행지가 없습니다.')
  })

  it('does not reload after a programmatic map move', async () => {
    const { map } = await mountReadyView()
    locationService.getLocationPage.mockClear()

    map.vm.$emit('viewport-change', { ...bounds, minLat: 36, maxLat: 36.01, programmatic: true })
    await vi.advanceTimersByTimeAsync(1000)

    expect(locationService.getLocationPage).not.toHaveBeenCalled()
  })

  it('debounces a user move to another area and resets to page 1', async () => {
    const { map, sidePanel } = await mountReadyView()

    sidePanel.vm.$emit('change-page', 2)
    await flushPromises()
    expect(sidePanel.props('currentPage')).toBe(2)
    locationService.getLocationPage.mockClear()

    map.vm.$emit('viewport-change', bounds)
    await vi.advanceTimersByTimeAsync(299)
    expect(locationService.getLocationPage).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(locationService.getLocationPage).toHaveBeenCalledTimes(1)
    expect(callsByMode('viewport')[0]).toMatchObject({ page: 1 })
    expect(sidePanel.props('currentPage')).toBe(1)
  })

  it('keeps the current page when a small pan produces the same snapped query', async () => {
    const { map, sidePanel } = await mountReadyView()

    map.vm.$emit('viewport-change', bounds)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    sidePanel.vm.$emit('change-page', 3)
    await flushPromises()
    locationService.getLocationPage.mockClear()

    map.vm.$emit('viewport-change', { ...bounds })
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(locationService.getLocationPage).not.toHaveBeenCalled()
    expect(sidePanel.props('currentPage')).toBe(3)
  })

  it('loads the requested page and stores it in the route', async () => {
    const { router, sidePanel } = await mountReadyView()

    sidePanel.vm.$emit('change-page', 2)
    await flushPromises()

    expect(callsByMode('area').at(-1)).toMatchObject({ page: 2 })
    expect(router.currentRoute.value.query.page).toBe('2')
  })

  it('uses keyword mode for a search, fits the map, and ignores user map moves', async () => {
    locationService.getLocationPage.mockResolvedValue(page([location('k1')]))
    const { map, sidePanel } = await mountReadyView('/map?query=해운대')

    expect(callsByMode('area')).toHaveLength(0)
    expect(callsByMode('keyword')).toEqual([
      expect.objectContaining({ keyword: '해운대', page: 1 }),
    ])
    expect(fitLocations).toHaveBeenCalledWith([expect.objectContaining({ id: 'k1' })])
    expect(sidePanel.props('listMode')).toBe('keyword')

    locationService.getLocationPage.mockClear()
    map.vm.$emit('viewport-change', { ...bounds, minLat: 36, maxLat: 36.01 })
    await vi.advanceTimersByTimeAsync(1000)

    expect(locationService.getLocationPage).not.toHaveBeenCalled()
  })

  it('lists the selected region in area mode, fits the map, and ignores user map moves', async () => {
    const { wrapper, router, map, sidePanel } = await mountReadyView()
    const filterBar = wrapper.findComponent(LocationFilterBarStub)
    locationService.getLocationPage.mockClear()
    locationService.getLocationPage.mockResolvedValue(page([location('r1', 35.16, 129.16)]))

    changeFilters(filterBar, { region: { sido: '11', sigungu: '' } })
    await flushPromises()

    expect(callsByMode('area')).toEqual([
      expect.objectContaining({ areaCode: '11', sigunguCode: '', page: 1, size: 20 }),
    ])
    expect(fitLocations).toHaveBeenCalledWith([expect.objectContaining({ id: 'r1' })])
    expect(sidePanel.props('listMode')).toBe('area')
    expect(router.currentRoute.value.query).toMatchObject({ sido: '11' })

    locationService.getLocationPage.mockClear()
    map.vm.$emit('viewport-change', { ...bounds, minLat: 36, maxLat: 36.01 })
    await vi.advanceTimersByTimeAsync(1000)

    expect(locationService.getLocationPage).not.toHaveBeenCalled()
  })

  it('applies the category and classification from the filter bar and keeps them in the route', async () => {
    const { wrapper, router } = await mountReadyView()
    const filterBar = wrapper.findComponent(LocationFilterBarStub)

    changeFilters(filterBar, {
      category: '숙박',
      classification: { lclsSystm1: 'AC', lclsSystm2: 'AC03', lclsSystm3: '' },
    })
    await flushPromises()

    expect(callsByMode('area').at(-1)).toMatchObject({
      category: '숙박',
      lclsSystm1: 'AC',
      lclsSystm2: 'AC03',
      page: 1,
    })
    expect(router.currentRoute.value.query).toMatchObject({
      category: '숙박',
      lcls1: 'AC',
      lcls2: 'AC03',
    })
    expect(filterBar.props('filters')).toMatchObject({
      category: '숙박',
      classification: { lclsSystm1: 'AC', lclsSystm2: 'AC03' },
    })

    // 카테고리만 바뀐 조건이 와도 그 카테고리에 없는 분류체계 코드는 남기지 않습니다.
    changeFilters(filterBar, { category: '쇼핑' })
    await flushPromises()

    expect(callsByMode('area').at(-1)).toMatchObject({ category: '쇼핑', lclsSystm1: '' })
    expect(router.currentRoute.value.query).not.toHaveProperty('lcls1')
  })

  it('returns to the refreshed list when a category changes from the detail panel', async () => {
    const { wrapper, router, map } = await mountReadyView()
    const filterBar = wrapper.findComponent(LocationFilterBarStub)
    const sidePanel = wrapper.findComponent(MapSidePanelStub)

    map.vm.$emit('viewport-change', bounds)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(callsByMode('viewport')).toHaveLength(1)

    sidePanel.vm.$emit('select-location', location('detail-1'))
    await flushPromises()
    expect(sidePanel.props('selectedLocation')).toMatchObject({ id: 'detail-1' })
    expect(router.currentRoute.value.query.placeId).toBe('detail-1')

    let resolveFilteredPage
    locationService.getLocationPage.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFilteredPage = resolve }),
    )
    changeFilters(filterBar, {
      category: '전체',
      classification: { lclsSystm1: 'LS', lclsSystm2: '', lclsSystm3: '' },
    })
    await flushPromises()

    expect(sidePanel.props('selectedLocation')).toBeNull()
    expect(router.currentRoute.value.query).not.toHaveProperty('placeId')
    expect(sidePanel.props('visibleLocations')).toEqual([])
    expect(sidePanel.props('locationsLoading')).toBe(true)
    expect(locationService.getLocationPage.mock.calls.at(-1)[0]).toMatchObject({
      mode: 'area',
      lclsSystm1: 'LS',
      areaCode: '',
      sigunguCode: '',
    })

    resolveFilteredPage(page([location('filtered-result')]))
    await flushPromises()
    expect(sidePanel.props('visibleLocations')).toEqual([expect.objectContaining({ id: 'filtered-result' })])
    expect(sidePanel.props('locationsLoading')).toBe(false)
  })

  it('restores a main-class selection from a URL without a content category', async () => {
    const { wrapper } = await mountReadyView('/map?lcls1=LS')
    const filterBar = wrapper.findComponent(LocationFilterBarStub)

    expect(filterBar.props('filters')).toMatchObject({
      category: '전체',
      classification: { lclsSystm1: 'LS' },
    })
    expect(callsByMode('area').at(-1)).toMatchObject({
      category: '',
      lclsSystm1: 'LS',
    })
  })

  it('drops classification codes that do not belong to the category', async () => {
    const { wrapper } = await mountReadyView(
      '/map?category=숙박&lcls1=AC&lcls2=AC05&lcls3=AC050100',
    )
    const filterBar = wrapper.findComponent(LocationFilterBarStub)

    // 캠핑(AC05)은 레포츠 분류라 숙박에서는 대분류까지만 남습니다.
    expect(callsByMode('area').at(-1)).toMatchObject({
      category: '숙박',
      lclsSystm1: 'AC',
      lclsSystm2: '',
      lclsSystm3: '',
    })

    changeFilters(filterBar, {
      classification: { lclsSystm1: 'SH', lclsSystm2: 'SH01', lclsSystm3: '' },
    })
    await flushPromises()

    expect(callsByMode('area').at(-1)).toMatchObject({ category: '숙박', lclsSystm1: '' })
  })

  it('treats an unknown route category as all categories', async () => {
    const { wrapper, sidePanel } = await mountReadyView('/map?category=축제공연행사&lcls1=EV')

    expect(wrapper.findComponent(LocationFilterBarStub).props('filters')).toMatchObject({
      category: '전체',
      classification: { lclsSystm1: '' },
    })
    expect(callsByMode('area').at(-1)).toMatchObject({ category: '', lclsSystm1: '' })
    expect(sidePanel.props('listMode')).toBe('nationwide')
  })

  it('searches by keyword from the filter bar and clears every filter on reset', async () => {
    locationService.getLocationPage.mockResolvedValue(page([location('k1')]))
    const { wrapper, router, sidePanel } = await mountReadyView('/map?category=숙박')
    const filterBar = wrapper.findComponent(LocationFilterBarStub)

    changeFilters(filterBar, { keyword: '해운대' })
    await flushPromises()

    expect(callsByMode('keyword').at(-1)).toMatchObject({
      keyword: '해운대',
      category: '숙박',
      page: 1,
    })
    expect(router.currentRoute.value.query).toMatchObject({ query: '해운대', category: '숙박' })
    expect(sidePanel.props('listMode')).toBe('keyword')

    filterBar.vm.$emit('reset')
    await flushPromises()

    expect(callsByMode('area').at(-1)).toMatchObject({
      category: '',
      lclsSystm1: 'FD',
      keyword: '',
    })
    expect(router.currentRoute.value.query).toEqual({})
    expect(sidePanel.props('listMode')).toBe('nationwide')
  })

  it('restores the region and keyword from the route as a region-limited search', async () => {
    locationService.getLocationPage.mockResolvedValue(page([location('k1')]))

    await mountReadyView('/map?query=시장&sido=26&sigungu=350')

    expect(callsByMode('keyword')).toEqual([
      expect.objectContaining({ keyword: '시장', areaCode: '26', sigunguCode: '350' }),
    ])
  })

  it('shows an error when the page request fails and retries on demand', async () => {
    locationService.getLocationPage.mockRejectedValueOnce(new Error('network error'))

    const { wrapper } = await mountReadyView()

    expect(wrapper.text()).toContain('network error')

    locationService.getLocationPage.mockResolvedValue(page([location('1')]))
    await wrapper.find('.map-view__error button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.map-view__error').exists()).toBe(false)
  })

  it('selects a list item by moving focus only', async () => {
    const { map, sidePanel } = await mountReadyView()
    locationService.getLocationPage.mockClear()
    const target = location('9', 37.6, 127)

    sidePanel.vm.$emit('select-location', target)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)

    expect(map.props('focusLocation')).toMatchObject({ id: '9' })
    expect(locationService.getLocationPage).not.toHaveBeenCalled()
    expect(locationService.getNearbyLocations).toHaveBeenCalledWith(target, { limit: 5 })
    expect(locationService.getLocationDetail).toHaveBeenCalledWith(target)
  })

  it('loads and selects a placeId from the route, then lists around it', async () => {
    const place = location('126508', 35.1, 129.1)
    locationService.getLocation.mockResolvedValue(place)

    await mountReadyView('/map?placeId=126508')

    expect(locationService.getLocation).toHaveBeenCalledWith('126508')
    expect(postsService.getPosts).toHaveBeenCalledWith({
      placeId: '126508',
      placeName: undefined,
      placeAddress: undefined,
      latitude: 35.1,
      longitude: 129.1,
      page: 1,
      size: 3,
    })
    expect(callsByMode('area')).toHaveLength(0)
    expect(callsByMode('viewport')[0].center.mapY).toBeCloseTo(35.1, 2)
  })

  it('searches by the posted place name when its id is not in the tourism API', async () => {
    const resolvedPlace = {
      ...location('real-place-id', 35.16, 129.16),
      contentId: 'real-place-id',
      name: '해운대해수욕장',
    }
    locationService.getLocation.mockRejectedValue(new Error('not found'))
    locationService.findLocationByName.mockResolvedValue(resolvedPlace)

    const { sidePanel, router } = await mountReadyView(
      '/map?placeId=demo-place&placeName=해운대해수욕장&placeAddress=부산광역시',
    )

    expect(locationService.findLocationByName).toHaveBeenCalledWith(
      '해운대해수욕장',
      '부산광역시',
    )
    expect(sidePanel.props('selectedLocation')).toMatchObject({
      contentId: 'real-place-id',
      name: '해운대해수욕장',
      relatedPostPlaceId: 'demo-place',
    })
    expect(postsService.getPosts).toHaveBeenCalledWith({
      placeId: 'demo-place',
      placeName: '해운대해수욕장',
      placeAddress: undefined,
      latitude: 35.16,
      longitude: 129.16,
      page: 1,
      size: 3,
    })
    expect(locationService.getLocationDetail).toHaveBeenCalledWith({
      ...resolvedPlace,
      relatedPostPlaceId: 'demo-place',
    })

    sidePanel.vm.$emit('open-all-posts', sidePanel.props('selectedLocation'))
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toContain('/posts?')
    expect(router.currentRoute.value.query).toMatchObject({
      placeId: 'demo-place',
      placeName: '해운대해수욕장',
    })
  })
})
