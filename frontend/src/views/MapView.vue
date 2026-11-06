<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import LocationFilterBar from '@/components/location/LocationFilterBar.vue'
import KakaoTravelMap from '@/components/map/KakaoTravelMap.vue'
import MapSidePanel from '@/components/map/MapSidePanel.vue'
import { createLocationFilters, normalizeLocationFilters, toLocationQuery } from '@/services/locationFilters'
import { snapViewportQuery } from '@/services/locationMapCache'
import {
  getLocation,
  getLocationDetail,
  getLocationPage,
  getNearbyLocations,
  findLocationByName,
} from '@/services/locationService'
import { getPosts } from '@/services/posts'
import { ALL_CATEGORY } from '@/services/tourApi'
import { isValidKoreanLocation } from '@/utils/geo'

const PAGE_SIZE = 20
const LOCATION_REQUEST_DELAY = 300

const route = useRoute()
const router = useRouter()
const mapComponent = ref(null)

function getRoutePage() {
  const page = Number(route.query.page)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function getRouteString(key) {
  return typeof route.query[key] === 'string' ? route.query[key] : ''
}

// 주소로 들어온 조건은 아는 카테고리와 그 카테고리에 실제로 있는 분류체계 코드만 받아들입니다.
function getRouteFilters() {
  const category = getRouteString('category')
  const lclsSystm1 = getRouteString('lcls1')
  const lclsSystm2 = getRouteString('lcls2')
  const lclsSystm3 = getRouteString('lcls3')
  const hasClassification = Boolean(lclsSystm1 || lclsSystm2 || lclsSystm3)
  const defaults = createLocationFilters()
  const useDefaultCategory = !category && !hasClassification

  return normalizeLocationFilters({
    keyword: getRouteString('query'),
    category: category || (useDefaultCategory ? defaults.category : ALL_CATEGORY),
    region: { sido: getRouteString('sido'), sigungu: getRouteString('sigungu') },
    classification: {
      lclsSystm1: lclsSystm1 || (useDefaultCategory ? defaults.classification.lclsSystm1 : ''),
      lclsSystm2,
      lclsSystm3,
    },
  })
}

const allLocations = ref([])
const filters = ref(getRouteFilters())
const selectedLocation = ref(null)
const focusLocation = ref(null)
const viewportBounds = ref(null)
const currentPage = ref(getRoutePage())
const totalCount = ref(0)
const totalPages = ref(0)
const relatedPosts = ref([])
const nearbyLocations = ref([])
const locationDetail = ref(null)
const locationsLoading = ref(false)
const hasLoadedLocations = ref(false)
const postsLoading = ref(false)
const pageError = ref('')

let locationRequestTimer = null
let activeLocationRequest = null
let loadedQueryKey = ''
let locationContextRequestId = 0
const viewportMode = ref(false)

const listMode = computed(() => {
  if (filters.value.keyword) return 'keyword'

  if (filters.value.region.sido) return 'area'

  return viewportMode.value ? 'viewport' : 'nationwide'
})
const visibleLocations = computed(() => allLocations.value)
const hasActiveFilter = computed(() => {
  const { keyword, category, region, classification } = filters.value

  return Boolean(keyword || category !== ALL_CATEGORY || region.sido || classification.lclsSystm1)
})
const selectedLocationId = computed(() => selectedLocation.value?.id ?? null)
const initialLocationsLoading = computed(() => locationsLoading.value && !hasLoadedLocations.value)
const locationsRefreshing = computed(() => locationsLoading.value && hasLoadedLocations.value)

function getRouteQuery(overrides = {}) {
  const { keyword, category, region, classification } = filters.value
  const query = {
    query: keyword || undefined,
    category:
      category !== ALL_CATEGORY || !classification.lclsSystm1 ? category : undefined,
    sido: region.sido || undefined,
    sigungu: region.sigungu || undefined,
    lcls1: classification.lclsSystm1 || undefined,
    lcls2: classification.lclsSystm2 || undefined,
    lcls3: classification.lclsSystm3 || undefined,
    page: currentPage.value > 1 ? String(currentPage.value) : undefined,
    placeId: route.query.placeId || undefined,
    ...overrides,
  }

  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined))
}

async function replaceRouteQuery(overrides = {}) {
  await router.replace({ path: '/map', query: getRouteQuery(overrides) })
}

/**
 * 현재 검색 조건으로 목록 요청 옵션을 만듭니다(page 제외).
 * viewport 모드는 스냅된 중심·반경을 쓰므로 지도를 조금 움직이면 같은 요청이 됩니다.
 * 지역을 고르면 지도 영역 대신 그 지역(area) 기준으로 조회하고, 검색어에도 지역을 함께 적용합니다.
 */
function buildListQuery(mode = listMode.value) {
  const query = toLocationQuery(filters.value)

  if (mode === 'nationwide') {
    return { mode: 'area', ...query }
  }

  if (mode !== 'viewport') {
    return { mode, ...query }
  }

  if (!viewportBounds.value) return null

  return { mode: 'viewport', center: snapViewportQuery(viewportBounds.value), ...query }
}

// 현재 지도 크기를 유지한 채 중심만 location으로 옮긴 영역을 계산합니다.
function centerBoundsOn(bounds, location) {
  const halfLat = (bounds.maxLat - bounds.minLat) / 2
  const halfLng = (bounds.maxLng - bounds.minLng) / 2

  return {
    minLat: location.latitude - halfLat,
    maxLat: location.latitude + halfLat,
    minLng: location.longitude - halfLng,
    maxLng: location.longitude + halfLng,
  }
}

function toBounds({ minLat, maxLat, minLng, maxLng }) {
  return { minLat, maxLat, minLng, maxLng }
}

async function handleMapReady(bounds) {
  if (!bounds) return

  viewportBounds.value = toBounds(bounds)

  // placeId가 있으면 loadRouteLocation이 그 위치 기준으로 목록을 불러옵니다.
  if (route.query.placeId) {
    if (selectedLocation.value) {
      viewportBounds.value = centerBoundsOn(viewportBounds.value, selectedLocation.value)
      if (!filters.value.region.sido && !filters.value.keyword) {
        viewportMode.value = true
      }
      await loadLocations({ page: currentPage.value })
    }
    return
  }

  await loadLocations({ page: currentPage.value })
}

function handleViewportChange({ programmatic = false, ...bounds }) {
  viewportBounds.value = toBounds(bounds)

  // 목록 클릭·검색 결과 맞춤 등 코드로 지도를 옮긴 경우에는 재조회하지 않습니다.
  if (programmatic || filters.value.region.sido || filters.value.keyword) return

  viewportMode.value = true
  scheduleLocationLoad()
}

function scheduleLocationLoad() {
  window.clearTimeout(locationRequestTimer)
  locationRequestTimer = window.setTimeout(() => {
    loadLocations({ page: 1, keepPageIfSameQuery: true })
  }, LOCATION_REQUEST_DELAY)
}

/**
 * @param {{ page?: number, force?: boolean, keepPageIfSameQuery?: boolean, mode?: 'viewport' | 'keyword' | 'area' | 'nationwide' }} options
 */
async function loadLocations({
  page = 1,
  force = false,
  keepPageIfSameQuery = false,
  mode = listMode.value,
} = {}) {
  window.clearTimeout(locationRequestTimer)

  const listQuery = buildListQuery(mode)

  if (!listQuery) return

  const queryKey = JSON.stringify(listQuery)
  const isSameQuery = queryKey === loadedQueryKey

  // 지도를 조금만 움직여 같은 요청이 되면 보던 페이지를 유지합니다.
  if (!force && isSameQuery && (keepPageIfSameQuery || page === currentPage.value)) return
  if (activeLocationRequest?.key === `${queryKey}#${page}`) return

  activeLocationRequest?.controller.abort()
  const controller = new AbortController()
  const request = { key: `${queryKey}#${page}`, controller }
  activeLocationRequest = request
  locationsLoading.value = true
  pageError.value = ''

  try {
    const result = await getLocationPage({
      ...listQuery,
      page,
      size: PAGE_SIZE,
      signal: controller.signal,
    })

    if (activeLocationRequest !== request) return

    loadedQueryKey = queryKey
    currentPage.value = page
    totalCount.value = result.totalCount
    totalPages.value = result.totalPages
    applyLocations(result.items)

    if (listQuery.mode !== 'viewport') {
      mapComponent.value?.fitLocations?.(result.items)
    }

    if (getRoutePage() !== page) {
      await replaceRouteQuery()
    }
  } catch (error) {
    if (error?.name !== 'AbortError' && activeLocationRequest === request) {
      pageError.value =
        error instanceof Error ? error.message : '여행지 정보를 불러오지 못했습니다.'
    }
  } finally {
    if (activeLocationRequest === request) {
      activeLocationRequest = null
      locationsLoading.value = false
    }
  }
}

function applyLocations(locations) {
  // Keep API results visible in the list even when an item has no usable map coordinates.
  const nextLocations = [...locations]

  // 선택한 여행지가 현재 페이지에 없어도 마커는 유지합니다.
  if (
    selectedLocation.value &&
    isValidKoreanLocation(selectedLocation.value) &&
    !nextLocations.some((location) => String(location.id) === String(selectedLocation.value.id))
  ) {
    nextLocations.push(selectedLocation.value)
  }

  allLocations.value = nextLocations
  hasLoadedLocations.value = true
}

function retryLocations() {
  loadLocations({ page: currentPage.value, force: true })
}

function clearLocationResults() {
  activeLocationRequest?.controller.abort()
  activeLocationRequest = null
  loadedQueryKey = ''
  allLocations.value = []
  totalCount.value = 0
  totalPages.value = 0
  hasLoadedLocations.value = false
  pageError.value = ''
  locationsLoading.value = Boolean(buildListQuery())
}

function changePage(page) {
  if (page < 1 || (totalPages.value && page > totalPages.value) || page === currentPage.value) {
    return
  }

  loadLocations({ page })
}

// 검색어·카테고리·지역·분류체계 중 무엇이 바뀌든 첫 페이지부터 다시 조회합니다.
async function applyFilters(nextFilters) {
  const normalizedFilters = normalizeLocationFilters(nextFilters)

  // Filter changes must use the selected region (or nationwide), not a stale map viewport.
  viewportMode.value = false

  locationContextRequestId += 1
  filters.value = normalizedFilters
  currentPage.value = 1
  selectedLocation.value = null
  relatedPosts.value = []
  nearbyLocations.value = []
  locationDetail.value = null
  postsLoading.value = false
  clearLocationResults()
  await replaceRouteQuery({ placeId: undefined })
  await loadLocations({ page: 1, force: true })
}

async function resetFilters() {
  viewportMode.value = false
  filters.value = createLocationFilters()
  currentPage.value = 1
  clearLocationResults()
  selectedLocation.value = null
  relatedPosts.value = []
  nearbyLocations.value = []
  locationDetail.value = null
  await router.replace({ path: '/map' })
  await loadLocations({ page: 1, force: true })
}

async function loadLocationContext(location) {
  const requestId = ++locationContextRequestId
  const relatedPostPlaceId = location.relatedPostPlaceId || location.contentId
  postsLoading.value = true
  relatedPosts.value = []
  nearbyLocations.value = []
  locationDetail.value = null

  const [postsResult, nearbyResult, detailResult] = await Promise.allSettled([
    getPosts({
      placeId: relatedPostPlaceId,
      placeName: location.name,
      placeAddress: location.address,
      latitude: location.latitude,
      longitude: location.longitude,
      page: 1,
      size: 3,
    }),
    getNearbyLocations(location, { limit: 5 }),
    getLocationDetail(location),
  ])

  if (requestId !== locationContextRequestId) return

  relatedPosts.value = postsResult.status === 'fulfilled' ? postsResult.value.items : []
  nearbyLocations.value = nearbyResult.status === 'fulfilled' ? nearbyResult.value : []
  locationDetail.value = detailResult.status === 'fulfilled' ? detailResult.value : null
  postsLoading.value = false

  ;[postsResult, nearbyResult, detailResult]
    .filter((result) => result.status === 'rejected')
    .forEach((result) => console.error('여행지 관련 정보 조회 실패:', result.reason))
}

async function selectLocation(location) {
  selectedLocation.value = location
  focusLocation.value = { ...location }

  if (!allLocations.value.some((item) => String(item.id) === String(location.id))) {
    allLocations.value = [...allLocations.value, location]
  }

  if (String(route.query.placeId ?? '') !== String(location.contentId)) {
    await replaceRouteQuery({ placeId: location.contentId })
  }

  await loadLocationContext(location)
}

async function selectNearbyLocation(location) {
  await selectLocation(location)
}

async function closeDetail() {
  locationContextRequestId += 1
  selectedLocation.value = null
  relatedPosts.value = []
  nearbyLocations.value = []
  locationDetail.value = null
  await replaceRouteQuery({ placeId: undefined })
}

async function loadRouteLocation(placeId, placeName = '', placeAddress = '', postPlaceId = '') {
  if (!placeId) return
  if (String(selectedLocation.value?.contentId ?? '') === String(placeId)) return

  try {
    let location

    try {
      location = await getLocation(placeId)
    } catch (error) {
      if (!placeName) throw error
      location = await findLocationByName(placeName, placeAddress)
    }

    if (postPlaceId || placeName) {
      location = {
        ...location,
        relatedPostPlaceId: postPlaceId || placeId,
      }
    }

    selectedLocation.value = location
    focusLocation.value = { ...location }

    if (!allLocations.value.some((item) => item.id === location.id)) {
      allLocations.value = [...allLocations.value, location]
    }

    // 지도가 준비된 뒤 처음 들어온 경우 그 여행지 주변을 목록으로 불러옵니다.
    if (
      viewportBounds.value &&
      !hasLoadedLocations.value &&
      !filters.value.region.sido &&
      !filters.value.keyword
    ) {
      viewportMode.value = true
      viewportBounds.value = centerBoundsOn(viewportBounds.value, location)
      loadLocations({ page: currentPage.value })
    }

    await loadLocationContext(location)
  } catch (error) {
    pageError.value =
      error instanceof Error ? error.message : '선택한 여행지를 불러오지 못했습니다.'
  }
}

function createCourse(location) {
  router.push({ path: '/posts/create', query: { placeId: location.contentId } })
}

function openPost(post) {
  router.push(`/posts/${post.id}`)
}

function openAllPosts(location) {
  router.push({
    path: '/posts',
    query: {
      placeId: location.relatedPostPlaceId || location.contentId,
      placeName: location.name || undefined,
      placeAddress: location.address || undefined,
      latitude: Number.isFinite(location.latitude) ? String(location.latitude) : undefined,
      longitude: Number.isFinite(location.longitude) ? String(location.longitude) : undefined,
    },
  })
}

watch(
  () =>
    ['query', 'category', 'sido', 'sigungu', 'lcls1', 'lcls2', 'lcls3'].map(
      (key) => route.query[key],
    ),
  () => {
    filters.value = getRouteFilters()
  },
)

watch(
  () => [
    route.query.placeId,
    route.query.placeName,
    route.query.placeAddress,
    route.query.postPlaceId,
  ],
  ([placeId, placeName, placeAddress, postPlaceId]) =>
    loadRouteLocation(
      typeof placeId === 'string' ? placeId : '',
      typeof placeName === 'string' ? placeName : '',
      typeof placeAddress === 'string' ? placeAddress : '',
      typeof postPlaceId === 'string' ? postPlaceId : '',
    ),
  { immediate: true },
)

onBeforeUnmount(() => {
  window.clearTimeout(locationRequestTimer)
  activeLocationRequest?.controller.abort()
  locationContextRequestId += 1
})
</script>

<template>
  <main class="map-view">
    <div class="container map-view__container">
      <header class="map-view__header">
        <div>
          <p class="map-view__eyebrow">LocalHub</p>

          <h1 class="map-view__title">여행지 지도</h1>

          <p class="map-view__description">
            지도에서 지역 여행지를 탐색하고 주변 여행지를 함께 확인해 보세요.
          </p>
        </div>
      </header>

      <LocationFilterBar
        class="map-view__filter"
        :filters="filters"
        :loading="initialLocationsLoading"
        @change="applyFilters"
        @reset="resetFilters"
      />

      <div v-if="pageError" class="map-view__error" role="alert">
        <div>
          <strong>여행지 정보를 불러오지 못했습니다.</strong>
          <p>{{ pageError }}</p>
        </div>

        <button type="button" @click="retryLocations">다시 시도</button>
      </div>

      <section class="map-view__workspace">
        <KakaoTravelMap
          ref="mapComponent"
          :locations="allLocations"
          :selected-location-id="selectedLocationId"
          :focus-location="focusLocation"
          @select-location="selectLocation"
          @map-ready="handleMapReady"
          @viewport-change="handleViewportChange"
        />

        <MapSidePanel
          :selected-location="selectedLocation"
          :visible-locations="visibleLocations"
          :related-posts="relatedPosts"
          :nearby-locations="nearbyLocations"
          :location-detail="locationDetail"
          :locations-loading="initialLocationsLoading"
          :locations-refreshing="locationsRefreshing"
          :posts-loading="postsLoading"
          :has-active-filter="hasActiveFilter"
          :list-mode="listMode"
          :search-keyword="filters.keyword"
          :total-count="totalCount"
          :current-page="currentPage"
          :total-pages="totalPages"
          @select-location="selectLocation"
          @change-page="changePage"
          @close-detail="closeDetail"
          @select-nearby="selectNearbyLocation"
          @create-course="createCourse"
          @open-post="openPost"
          @open-all-posts="openAllPosts"
        />
      </section>
    </div>
  </main>
</template>

<style scoped>
.map-view {
  min-height: calc(100vh - var(--header-height));
  padding: var(--spacing-8) 0 var(--spacing-12);
  background-color: var(--color-background);
}

.map-view__container {
  display: grid;
  gap: var(--spacing-5);
}

.map-view__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--spacing-6);
}

.map-view__eyebrow {
  margin: 0 0 var(--spacing-1);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.map-view__title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-3xl);
  line-height: 1.25;
}

.map-view__description {
  margin: var(--spacing-2) 0 0;
  color: var(--color-text-secondary);
}

.map-view__filter {
  padding: var(--spacing-5);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.map-view__location-warning {
  margin: calc(-1 * var(--spacing-2)) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.map-view__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  grid-template-rows: minmax(0, 1fr);
  height: clamp(620px, 70dvh, 720px);
  overflow: hidden;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.map-view__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  padding: var(--spacing-5);
  color: var(--color-error);
  background-color: rgb(220 90 90 / 8%);
  border: 1px solid rgb(220 90 90 / 20%);
  border-radius: var(--radius-md);
}

.map-view__error strong {
  display: block;
}

.map-view__error p {
  margin: var(--spacing-1) 0 0;
  font-size: var(--font-size-sm);
}

.map-view__error button {
  flex: 0 0 auto;
  min-height: 40px;
  padding: 0 var(--spacing-4);
  color: var(--color-error);
  font-weight: var(--font-weight-semibold);
  background-color: var(--color-surface);
  border: 1px solid rgb(220 90 90 / 30%);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.map-view__error button:hover {
  background-color: rgb(220 90 90 / 8%);
}

@media (max-width: 900px) {
  .map-view {
    padding-top: var(--spacing-6);
  }

  .map-view__workspace {
    grid-template-columns: 1fr;
    grid-template-rows: 460px 420px;
    height: 880px;
  }
}

@media (max-width: 640px) {
  .map-view {
    padding-top: var(--spacing-5);
  }

  .map-view__container {
    gap: var(--spacing-4);
    padding-inline: 0;
  }

  .map-view__header {
    padding-inline: var(--content-padding-mobile);
  }

  .map-view__title {
    font-size: var(--font-size-2xl);
  }

  .map-view__description {
    font-size: var(--font-size-sm);
  }

  .map-view__filter,
  .map-view__workspace,
  .map-view__error {
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }

  .map-view__filter {
    padding: var(--spacing-4);
  }

  .map-view__workspace {
    grid-template-rows: 390px 420px;
    height: 810px;
  }
}
</style>
