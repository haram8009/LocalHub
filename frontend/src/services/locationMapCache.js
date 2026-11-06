import { readJson, removeItem, writeJson } from '@/services/storage'
import { calculateDistanceKm } from '@/utils/geo'

export const LOCATION_MAP_CACHE_TTL_MS = 10 * 60 * 1000
export const LOCATION_MAP_CACHE_MAX_ENTRIES = 50
export const RADIUS_BUCKETS = [500, 1000, 2000, 5000, 10000, 20000]

const SESSION_STORAGE_KEY = 'enjoytrip:locationPages'
const METERS_PER_LATITUDE_DEGREE = 111_000
const GRID_DIVISOR = 4
// 경도 격자 크기가 중심 위도에 따라 바뀌면 격자 원점이 흔들리므로 한국 중심 위도로 고정합니다.
const METERS_PER_LONGITUDE_DEGREE = METERS_PER_LATITUDE_DEGREE * Math.cos((36 * Math.PI) / 180)

const cache = new Map()
let isHydrated = false

function isFresh(entry, now) {
  return now - entry.cachedAt < LOCATION_MAP_CACHE_TTL_MS
}

function touchEntry(key, entry) {
  cache.delete(key)
  cache.set(key, entry)
}

function removeExpiredEntries(now) {
  cache.forEach((entry, key) => {
    if (!isFresh(entry, now)) {
      cache.delete(key)
    }
  })
}

function evictOldestEntries() {
  while (cache.size > LOCATION_MAP_CACHE_MAX_ENTRIES) {
    const oldestKey = cache.keys().next().value
    cache.delete(oldestKey)
  }
}

function hydrateFromSession() {
  if (isHydrated) return
  isHydrated = true

  const entries = readJson(SESSION_STORAGE_KEY, [], { type: 'session' })

  if (!Array.isArray(entries)) return

  entries.forEach(([key, entry]) => {
    if (typeof key === 'string' && entry?.cachedAt) {
      cache.set(key, entry)
    }
  })
}

function persistToSession() {
  writeJson(SESSION_STORAGE_KEY, Array.from(cache.entries()), { type: 'session' })
}

function roundToGrid(value, gridSize) {
  return Number((Math.round(value / gridSize) * gridSize).toFixed(6))
}

/**
 * 지도 영역을 위치기반 조회 파라미터로 바꿉니다.
 * 반경은 버킷 단위로 올리고 중심은 반경에 비례한 격자로 반올림해서,
 * 지도를 조금 움직였을 때 같은 요청(같은 캐시 키)이 되도록 합니다.
 *
 * @param {{ minLat: number, maxLat: number, minLng: number, maxLng: number }} bounds
 * @returns {{ mapX: number, mapY: number, radius: number }}
 */
export function snapViewportQuery(bounds) {
  const center = {
    latitude: (bounds.minLat + bounds.maxLat) / 2,
    longitude: (bounds.minLng + bounds.maxLng) / 2,
  }
  const cornerDistanceMeters =
    calculateDistanceKm(center, { latitude: bounds.maxLat, longitude: bounds.maxLng }) * 1000

  const radius =
    RADIUS_BUCKETS.find((bucket) => bucket >= cornerDistanceMeters) ?? RADIUS_BUCKETS.at(-1)

  const latitudeGrid = radius / GRID_DIVISOR / METERS_PER_LATITUDE_DEGREE
  const longitudeGrid = radius / GRID_DIVISOR / METERS_PER_LONGITUDE_DEGREE

  return {
    mapX: roundToGrid(center.longitude, longitudeGrid),
    mapY: roundToGrid(center.latitude, latitudeGrid),
    radius,
  }
}

export function createCacheKey(operation, params) {
  const serializedParams = Object.keys(params)
    .sort()
    .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map((key) => `${key}=${params[key]}`)
    .join('&')

  return `${operation}?${serializedParams}`
}

export function getCachedPage(key, now = Date.now()) {
  hydrateFromSession()
  removeExpiredEntries(now)

  const entry = cache.get(key)

  if (!entry) return null

  touchEntry(key, entry)
  return entry.value
}

export function setCachedPage(key, value, now = Date.now()) {
  hydrateFromSession()
  touchEntry(key, { value, cachedAt: now })
  removeExpiredEntries(now)
  evictOldestEntries()
  persistToSession()
}

/**
 * @param {{ keepSession?: boolean }} options keepSession=true면 메모리만 비웁니다(새로고침 상황).
 */
export function clearLocationMapCache({ keepSession = false } = {}) {
  cache.clear()
  isHydrated = false

  if (!keepSession) {
    removeItem(SESSION_STORAGE_KEY, { type: 'session' })
  }
}

export function getLocationMapCacheSize() {
  return cache.size
}
