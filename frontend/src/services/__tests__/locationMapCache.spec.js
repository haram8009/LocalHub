import { beforeEach, describe, expect, it } from 'vitest'

import {
  LOCATION_MAP_CACHE_MAX_ENTRIES,
  LOCATION_MAP_CACHE_TTL_MS,
  clearLocationMapCache,
  createCacheKey,
  getCachedPage,
  getLocationMapCacheSize,
  setCachedPage,
  snapViewportQuery,
} from '@/services/locationMapCache'

// 서울 시청 주변, 대략 1.1km x 0.9km 영역
const bounds = { minLat: 37.561, maxLat: 37.571, minLng: 126.972, maxLng: 126.984 }

function shift(target, latitudeDelta, longitudeDelta) {
  return {
    minLat: target.minLat + latitudeDelta,
    maxLat: target.maxLat + latitudeDelta,
    minLng: target.minLng + longitudeDelta,
    maxLng: target.maxLng + longitudeDelta,
  }
}

describe('location map cache', () => {
  beforeEach(() => {
    clearLocationMapCache()
  })

  describe('snapViewportQuery', () => {
    it('rounds the radius up to a bucket that covers the viewport corner', () => {
      expect(snapViewportQuery(bounds).radius).toBe(1000)
    })

    it('returns the same query for a small pan within a grid cell', () => {
      const { mapX, mapY } = snapViewportQuery(bounds)
      const halfLat = (bounds.maxLat - bounds.minLat) / 2
      const halfLng = (bounds.maxLng - bounds.minLng) / 2
      const cellCentered = {
        minLat: mapY - halfLat,
        maxLat: mapY + halfLat,
        minLng: mapX - halfLng,
        maxLng: mapX + halfLng,
      }

      // 반경 1km의 격자는 약 250m(위도 0.00225°) 간격이므로 100m 이동은 같은 칸입니다.
      expect(snapViewportQuery(shift(cellCentered, 0.0009, 0.0009))).toEqual(
        snapViewportQuery(cellCentered),
      )
    })

    it('returns a different query for a large pan', () => {
      expect(snapViewportQuery(shift(bounds, 0.02, 0))).not.toEqual(snapViewportQuery(bounds))
    })

    it('caps the radius at 20km', () => {
      const wide = { minLat: 35, maxLat: 38, minLng: 126, maxLng: 129 }

      expect(snapViewportQuery(wide).radius).toBe(20000)
    })
  })

  it('builds a stable key regardless of parameter order and ignores empty values', () => {
    expect(createCacheKey('op', { b: 2, a: 1, c: '' })).toBe(createCacheKey('op', { a: 1, b: 2 }))
  })

  it('returns cached values until the TTL expires', () => {
    setCachedPage('key', { items: [1] }, 1000)

    expect(getCachedPage('key', 1000 + LOCATION_MAP_CACHE_TTL_MS - 1)).toEqual({ items: [1] })
    expect(getCachedPage('key', 1000 + LOCATION_MAP_CACHE_TTL_MS)).toBeNull()
  })

  it('evicts the least recently used entry', () => {
    const now = Date.now()

    for (let index = 0; index < LOCATION_MAP_CACHE_MAX_ENTRIES; index += 1) {
      setCachedPage(`key-${index}`, index, now)
    }

    getCachedPage('key-0', now)
    setCachedPage('overflow', 'new', now)

    expect(getLocationMapCacheSize()).toBe(LOCATION_MAP_CACHE_MAX_ENTRIES)
    expect(getCachedPage('key-0', now)).toBe(0)
    expect(getCachedPage('key-1', now)).toBeNull()
  })

  it('restores entries from sessionStorage after the memory cache is lost', () => {
    setCachedPage('key', { items: ['saved'] })

    clearLocationMapCache({ keepSession: true })

    expect(getCachedPage('key')).toEqual({ items: ['saved'] })
  })
})
