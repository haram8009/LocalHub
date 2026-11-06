import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import KakaoTravelMap from '@/components/map/KakaoTravelMap.vue'

const overlays = []
const maps = []
let animationFrameCallback
let boundsInstances

class LatLngMock {
  constructor(latitude, longitude) {
    this.latitude = latitude
    this.longitude = longitude
  }
}

class LatLngBoundsMock {
  constructor() {
    this.positions = []
    boundsInstances.push(this)
  }

  extend(position) {
    this.positions.push(position)
  }
}

class MapMock {
  constructor() {
    this.setCenter = vi.fn()
    this.setBounds = vi.fn()
    this.panTo = vi.fn()
    this.level = 4
    this.setLevel = vi.fn()
    this.getLevel = vi.fn(() => this.level)
    this.getCenter = vi.fn(() => new LatLngMock(37.5666, 126.9784))
    this.setMaxLevel = vi.fn()
    this.setMinLevel = vi.fn()
    this.addControl = vi.fn()
    this.relayout = vi.fn()
    this.getBounds = vi.fn(() => ({
      getSouthWest: () => ({ getLat: () => 35, getLng: () => 128 }),
      getNorthEast: () => ({ getLat: () => 36, getLng: () => 130 }),
    }))
    maps.push(this)
  }
}

class CustomOverlayMock {
  constructor(options) {
    this.options = options
    this.setMap = vi.fn()
    overlays.push(this)
  }
}

describe('KakaoTravelMap', () => {
  beforeEach(() => {
    overlays.length = 0
    maps.length = 0
    boundsInstances = []

    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }
    window.requestAnimationFrame = vi.fn((callback) => {
      animationFrameCallback = callback
      return 1
    })
    window.cancelAnimationFrame = vi.fn()

    window.kakao = {
      maps: {
        load: (callback) => callback(),
        Map: MapMock,
        LatLng: LatLngMock,
        LatLngBounds: LatLngBoundsMock,
        CustomOverlay: CustomOverlayMock,
        ZoomControl: class {},
        MapTypeControl: class {},
        ControlPosition: { RIGHT: 'RIGHT', TOPRIGHT: 'TOPRIGHT' },
        event: {
          addListener: vi.fn(),
          removeListener: vi.fn(),
        },
      },
    }
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    delete window.kakao
    delete globalThis.ResizeObserver
    delete window.requestAnimationFrame
    delete window.cancelAnimationFrame
  })

  it('keeps the base map visible without any location results and refreshes its layout', async () => {
    const wrapper = mount(KakaoTravelMap)
    await flushPromises()

    expect(wrapper.find('.travel-map__canvas').exists()).toBe(true)
    expect(maps).toHaveLength(1)

    animationFrameCallback()

    expect(maps[0].relayout).toHaveBeenCalledTimes(1)
    expect(maps[0].setCenter).toHaveBeenCalledWith(maps[0].getCenter())

    await wrapper.unmount()
  })

  it('keeps the map available and offers retry when current location is unavailable', async () => {
    const getCurrentPosition = vi.fn((_success, error) =>
      error({ code: 2, POSITION_UNAVAILABLE: 2 }),
    )
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } })

    const wrapper = mount(KakaoTravelMap)
    await flushPromises()

    await wrapper.get('.travel-map__location-button').trigger('click')

    expect(wrapper.find('.travel-map__canvas').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('위치 신호를 확인한 뒤 다시 시도')
    expect(wrapper.get('[role="alert"]').text()).toContain('지도와 지역 검색을 계속 이용')

    await wrapper.get('.travel-map__location-retry').trigger('click')
    expect(getCurrentPosition).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.travel-map__location-retry').text()).toBe('다시 시도')

    await wrapper.unmount()
  })

  it('renders the selected pin with a distinct class, label, and higher layer', async () => {
    const locations = [
      { id: '1', name: '첫 번째 장소', latitude: 35.1, longitude: 129.1 },
      { id: '2', name: '선택한 장소', latitude: 35.2, longitude: 129.2 },
    ]

    const wrapper = mount(KakaoTravelMap, {
      props: { locations, selectedLocationId: '2' },
    })
    await flushPromises()

    expect(overlays).toHaveLength(2)
    expect(overlays[0].options.content.classList.contains('is-selected')).toBe(false)
    expect(overlays[1].options.content.classList.contains('is-selected')).toBe(true)
    expect(overlays[1].options.content.textContent).toContain('선택한 장소')
    expect(overlays[1].options.content.getAttribute('aria-label')).toContain('선택된 여행지')
    expect(overlays[1].options.zIndex).toBeGreaterThan(overlays[0].options.zIndex)

    await wrapper.unmount()
  })

  it('ignores locations without Korean map coordinates when fitting the map', async () => {
    const wrapper = mount(KakaoTravelMap)
    await flushPromises()

    wrapper.vm.fitLocations([
      { id: '1', latitude: 35.1, longitude: 129.1 },
      { id: '2', latitude: 36.2, longitude: 127.2 },
      { id: 'missing', latitude: 0, longitude: 0 },
      { id: 'outside', latitude: 35.2, longitude: 0 },
    ])

    expect(maps[0].setBounds).toHaveBeenCalledTimes(1)
    expect(boundsInstances[0].positions).toHaveLength(2)

    await wrapper.unmount()
  })

  const focusTarget = { id: '2', name: '선택한 장소', latitude: 35.2, longitude: 129.2 }

  it('keeps the zoom level and only pans when focusing at a close zoom', async () => {
    const wrapper = mount(KakaoTravelMap)
    await flushPromises()

    await wrapper.setProps({ focusLocation: focusTarget })

    const map = maps[0]
    expect(map.setLevel).not.toHaveBeenCalled()
    expect(map.panTo).toHaveBeenCalledWith(
      expect.objectContaining({ latitude: 35.2, longitude: 129.2 }),
    )

    await wrapper.unmount()
  })

  it('zooms in first when focusing from a wide zoom level', async () => {
    const wrapper = mount(KakaoTravelMap)
    await flushPromises()
    maps[0].level = 9

    await wrapper.setProps({ focusLocation: focusTarget })

    const map = maps[0]
    expect(map.setLevel).toHaveBeenCalledWith(5)
    expect(map.setLevel.mock.invocationCallOrder[0]).toBeLessThan(
      map.panTo.mock.invocationCallOrder[0],
    )

    await wrapper.unmount()
  })

  it('marks the idle event after a focus move as programmatic', async () => {
    const wrapper = mount(KakaoTravelMap)
    await flushPromises()

    const [, , idleListener] = window.kakao.maps.event.addListener.mock.calls[0]

    idleListener()
    await wrapper.setProps({ focusLocation: focusTarget })
    idleListener()

    expect(wrapper.emitted('viewport-change').map(([event]) => event.programmatic)).toEqual([
      false,
      true,
    ])

    await wrapper.unmount()
  })
})
