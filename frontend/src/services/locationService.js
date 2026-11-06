import {
  createCacheKey,
  getCachedPage,
  setCachedPage,
  snapViewportQuery,
} from '@/services/locationMapCache'
import {
  ALL_CATEGORY,
  getContentTypeId,
  getContentTypeLabel,
  tourRequest,
} from '@/services/tourApi'
import { normalizeClassification } from '@/services/tourCodes'
import { USE_TOUR_DATABASE } from '@/services/tourSource'

export const DEFAULT_PAGE_SIZE = 20
export const DEFAULT_AREA_CODE = USE_TOUR_DATABASE ? '1' : '11'
const NEARBY_RADIUS_METERS = 5000

const inFlightRequests = new Map()

function toHttps(url) {
  return typeof url === 'string' ? url.replace(/^http:\/\//, 'https://') : ''
}

function stripHtml(value) {
  return String(value ?? '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
}

function getRegion(address) {
  return String(address ?? '')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .join(' ')
}

function toCoordinate(value) {
  if (value === null || value === undefined || String(value).trim() === '') {
    return Number.NaN
  }

  const coordinate = Number(value)

  return Number.isFinite(coordinate) ? coordinate : Number.NaN
}

/**
 * 관광공사 응답 항목(또는 이미 정규화된 항목)을 화면용 여행지 객체로 바꿉니다.
 */
function normalizeLocation(location) {
  const contentId = String(location.contentid ?? location.contentId ?? location.id ?? '')
  const contentTypeId = String(location.contenttypeid ?? location.contentTypeId ?? '')
  const category = location.category ?? getContentTypeLabel(contentTypeId)
  const address =
    location.address ?? [location.addr1, location.addr2].filter(Boolean).join(' ').trim()
  const distanceMeters = Number(location.dist)

  return {
    id: contentId,
    contentId,
    contentTypeId,
    name: location.name ?? location.title ?? '',
    category,
    contentType: category,
    categoryLabel: category,
    address,
    region: location.region ?? getRegion(location.addr1 ?? address),
    description: location.description ?? stripHtml(location.overview),
    tel: location.tel ?? '',
    latitude: toCoordinate(location.latitude ?? location.mapy),
    longitude: toCoordinate(location.longitude ?? location.mapx),
    thumbnailImageUrl: toHttps(
      location.thumbnailImageUrl || location.firstimage2 || location.firstimage,
    ),
    originalImageUrl: toHttps(
      location.originalImageUrl || location.firstimage || location.firstimage2,
    ),
    distanceKm: Number.isFinite(distanceMeters)
      ? distanceMeters / 1000
      : Number(location.distanceKm),
  }
}

export function normalizeLocationResponse(location) {
  return normalizeLocation(location)
}

function withAbort(promise, signal) {
  if (!signal) return promise

  if (signal.aborted) {
    return Promise.reject(new DOMException('Aborted', 'AbortError'))
  }

  return new Promise((resolve, reject) => {
    const handleAbort = () => reject(new DOMException('Aborted', 'AbortError'))

    signal.addEventListener('abort', handleAbort, { once: true })
    promise.then(resolve, reject).finally(() => {
      signal.removeEventListener('abort', handleAbort)
    })
  })
}

/**
 * 캐시 → 진행 중 요청 → 실제 호출 순서로 관광공사 목록을 조회합니다.
 * 실제 요청은 호출자의 signal과 분리해서 끝까지 받아 캐시에 저장하고,
 * 호출자는 자신의 signal이 취소되면 기다리기만 멈춥니다.
 */
async function cachedTourRequest(operation, params, { signal } = {}) {
  const key = createCacheKey(operation, params)
  const cached = getCachedPage(key)

  if (cached) return cached

  if (!inFlightRequests.has(key)) {
    const request = tourRequest(operation, params)
      .then((result) => {
        setCachedPage(key, result)
        return result
      })
      .finally(() => {
        inFlightRequests.delete(key)
      })

    inFlightRequests.set(key, request)
  }

  return withAbort(inFlightRequests.get(key), signal)
}

function buildListRequest({
  mode,
  category,
  keyword,
  center,
  areaCode,
  sigunguCode,
  lclsSystm1,
  lclsSystm2,
  lclsSystm3,
  arrange,
  page,
  size,
}) {
  // 분류체계 코드는 명세 표에서 그 카테고리에 속하고 상위 코드와 이어지는 것만 보냅니다.
  const common = {
    contentTypeId: getContentTypeId(category),
    ...normalizeClassification(category || ALL_CATEGORY, USE_TOUR_DATABASE ? {} : {
      lclsSystm1,
      lclsSystm2,
      lclsSystm3,
    }),
    pageNo: page,
    numOfRows: size,
  }
  const region = {
    lDongRegnCd: areaCode,
    lDongSignguCd: areaCode ? sigunguCode : '',
  }

  if (mode === 'keyword') {
    return ['searchKeyword2', { ...common, ...region, keyword, arrange: arrange ?? 'A' }]
  }

  if (mode === 'viewport') {
    return [
      'locationBasedList2',
      {
        ...common,
        mapX: center.mapX,
        mapY: center.mapY,
        radius: center.radius,
        arrange: arrange ?? 'E',
      },
    ]
  }

  return ['areaBasedList2', { ...common, ...region, arrange: arrange ?? 'Q' }]
}

/**
 * 한 페이지 분량의 여행지를 조회합니다.
 *
 * @param {object} options
 * @param {'viewport' | 'keyword' | 'area'} options.mode
 * @param {string} [options.category] 카테고리 라벨(관광지, 숙박 ...). 비우면 전체
 * @param {string} [options.keyword] keyword 모드 검색어
 * @param {{ mapX: number, mapY: number, radius: number }} [options.center] viewport 모드 중심·반경
 * @param {string} [options.areaCode] area·keyword 모드 법정동 시도 코드
 * @param {string} [options.sigunguCode] area·keyword 모드 법정동 시군구 코드(areaCode 필요)
 * @param {string} [options.lclsSystm1] 분류체계 대분류 코드(category에 속한 코드만 전송)
 * @param {string} [options.lclsSystm2] 분류체계 중분류 코드(lclsSystm1 필요)
 * @param {string} [options.lclsSystm3] 분류체계 소분류 코드(lclsSystm1, lclsSystm2 필요)
 * @returns {Promise<{ items: object[], totalCount: number, page: number, size: number, totalPages: number }>}
 */
export async function getLocationPage({
  mode = 'area',
  category = '',
  keyword = '',
  center = null,
  areaCode = '',
  sigunguCode = '',
  lclsSystm1 = '',
  lclsSystm2 = '',
  lclsSystm3 = '',
  arrange,
  page = 1,
  size = DEFAULT_PAGE_SIZE,
  signal,
} = {}) {
  const [operation, params] = buildListRequest({
    mode,
    category,
    keyword: keyword.trim(),
    center,
    areaCode,
    sigunguCode,
    lclsSystm1,
    lclsSystm2,
    lclsSystm3,
    arrange,
    page,
    size,
  })
  const result = await cachedTourRequest(operation, params, { signal })

  return {
    items: result.items.map(normalizeLocation),
    totalCount: result.totalCount,
    page,
    size,
    totalPages: Math.ceil(result.totalCount / size),
  }
}

/**
 * 첫 페이지 목록만 필요한 화면(코스 작성, 홈)용 조회 함수입니다.
 */
export async function getLocations({
  category = '',
  query = '',
  bounds = null,
  limit = DEFAULT_PAGE_SIZE,
  signal,
} = {}) {
  const trimmedQuery = query.trim()
  let options

  if (trimmedQuery) {
    options = { mode: 'keyword', keyword: trimmedQuery }
  } else if (bounds) {
    options = { mode: 'viewport', center: snapViewportQuery(bounds) }
  } else {
    options = { mode: 'area' }
  }

  const result = await getLocationPage({ ...options, category, size: limit, signal })

  return result.items
}

export async function getLocation(contentId, { signal } = {}) {
  const result = await cachedTourRequest('detailCommon2', { contentId: String(contentId) }, {
    signal,
  })

  if (result.items.length === 0) {
    throw new Error('여행지 정보를 찾을 수 없습니다.')
  }

  return normalizeLocation(result.items[0])
}

function normalizePlaceName(name) {
  return String(name ?? '')
    .normalize('NFKC')
    .toLocaleLowerCase('ko-KR')
    .replace(/[\s\p{P}\p{S}]/gu, '')
}

export async function findLocationByName(name, address = '', { signal } = {}) {
  const normalizedName = normalizePlaceName(name)
  if (!normalizedName) {
    throw new Error('여행지 이름이 없어 관광정보를 검색할 수 없습니다.')
  }

  const { items } = await getLocationPage({
    mode: 'keyword',
    keyword: name,
    page: 1,
    size: 20,
    signal,
  })
  const normalizedAddress = normalizePlaceName(address)
  const exactMatches = items.filter((item) => normalizePlaceName(item.name) === normalizedName)
  const candidates = exactMatches.length
    ? exactMatches
    : items.filter((item) => {
        const candidateName = normalizePlaceName(item.name)
        const candidateAddress = normalizePlaceName(item.address)

        return (
          (candidateName.includes(normalizedName) || normalizedName.includes(candidateName)) &&
          normalizedAddress.slice(0, 8) &&
          candidateAddress.includes(normalizedAddress.slice(0, 8))
        )
      })

  const location = candidates.find(
    (item) => Number.isFinite(item.latitude) && Number.isFinite(item.longitude),
  )

  if (!location) {
    throw new Error(`'${name}'에 해당하는 관광지 정보를 찾을 수 없습니다.`)
  }

  return location
}

/**
 * 기준 여행지 주변의 여행지를 거리순으로 조회합니다.
 *
 * @param {string | object} target contentId 또는 좌표가 있는 여행지 객체
 */
export async function getNearbyLocations(target, { limit = 5, signal } = {}) {
  const location =
    typeof target === 'object' && target !== null && Number.isFinite(Number(target.latitude))
      ? target
      : await getLocation(typeof target === 'object' ? target.contentId : target, { signal })

  const result = await getLocationPage({
    mode: 'viewport',
    center: {
      mapX: location.longitude,
      mapY: location.latitude,
      radius: NEARBY_RADIUS_METERS,
    },
    page: 1,
    size: limit + 1,
    signal,
  })

  return result.items
    .filter((item) => String(item.contentId) !== String(location.contentId))
    .slice(0, limit)
}

const INTRO_FIELD_LABELS = {
  infocenter: '문의 및 안내',
  infocenterculture: '문의 및 안내',
  infocenterleports: '문의 및 안내',
  infocenterlodging: '문의 및 안내',
  infocentershopping: '문의 및 안내',
  infocenterfood: '문의 및 안내',
  sponsor1: '주최',
  sponsor1tel: '주최자 연락처',
  eventstartdate: '행사 시작일',
  eventenddate: '행사 종료일',
  eventplace: '행사 장소',
  playtime: '공연 시간',
  usetime: '이용 시간',
  usetimeculture: '이용 시간',
  usetimeleports: '이용 요금',
  usetimefestival: '이용 요금',
  opentime: '영업 시간',
  opentimefood: '영업 시간',
  openperiod: '개장 기간',
  useseason: '이용 시기',
  restdate: '쉬는 날',
  restdateculture: '쉬는 날',
  restdateleports: '쉬는 날',
  restdateshopping: '쉬는 날',
  restdatefood: '쉬는 날',
  usefee: '이용 요금',
  usefeeleports: '이용 요금',
  spendtime: '관람 소요 시간',
  distance: '코스 총 거리',
  taketime: '코스 소요 시간',
  theme: '코스 테마',
  checkintime: '체크인',
  checkouttime: '체크아웃',
  roomcount: '객실 수',
  subfacility: '부대시설',
  firstmenu: '대표 메뉴',
  treatmenu: '취급 메뉴',
  saleitem: '판매 품목',
  fairday: '장서는 날',
  restroom: '화장실',
  parking: '주차',
  parkingculture: '주차',
  parkingleports: '주차',
  parkinglodging: '주차',
  parkingshopping: '주차',
  parkingfood: '주차',
  reservation: '예약 안내',
  reservationlodging: '예약 안내',
  reservationfood: '예약 안내',
  packing: '포장',
  expguide: '체험 안내',
  agelimit: '관람 가능 연령',
  chkbabycarriage: '유모차 대여',
  chkbabycarriageculture: '유모차 대여',
  chkbabycarriageleports: '유모차 대여',
  chkpet: '반려동물 동반',
  chkpetculture: '반려동물 동반',
  chkpetleports: '반려동물 동반',
  chkcreditcard: '신용카드',
  chkcreditcardculture: '신용카드',
  chkcreditcardleports: '신용카드',
  chkcreditcardshopping: '신용카드',
  chkcreditcardfood: '신용카드',
}

function normalizeIntro(item) {
  if (!item) return []

  return Object.entries(INTRO_FIELD_LABELS)
    .map(([field, label]) => ({ field, label, value: stripHtml(item[field]) }))
    .filter(({ value }) => value && value !== '0')
}

function normalizeInfo(item) {
  return {
    name: stripHtml(item.infoname ?? item.subname ?? item.roomtitle ?? ''),
    text: stripHtml(item.infotext ?? item.subdetailoverview ?? item.roomintro ?? ''),
  }
}

/**
 * 상세 패널용 공통(detailCommon2)·소개(detailIntro2)·이미지(detailImage2)·반복(detailInfo2)
 * 정보를 함께 조회합니다. 일부 API가 실패해도 나머지 정보는 표시할 수 있도록 각각 독립적으로 처리합니다.
 *
 * @returns {Promise<{ overview: string, intro: { field: string, label: string, value: string }[], images: string[], infos: { name: string, text: string }[] }>}
 */
export async function getLocationDetail(location, { signal } = {}) {
  const contentId = String(location.contentId)
  const contentTypeId = String(location.contentTypeId ?? '')

  const [commonResult, introResult, imageResult, infoResult] = await Promise.allSettled([
    cachedTourRequest('detailCommon2', { contentId }, { signal }),
    contentTypeId
      ? cachedTourRequest('detailIntro2', { contentId, contentTypeId }, { signal })
      : Promise.resolve({ items: [] }),
    cachedTourRequest('detailImage2', { contentId, imageYN: 'Y' }, { signal }),
    contentTypeId
      ? cachedTourRequest('detailInfo2', { contentId, contentTypeId }, { signal })
      : Promise.resolve({ items: [] }),
  ])

  const valueOf = (result) => (result.status === 'fulfilled' ? result.value.items : [])

  return {
    overview: stripHtml(valueOf(commonResult)[0]?.overview),
    intro: normalizeIntro(valueOf(introResult)[0]),
    images: valueOf(imageResult)
      .map((image) => toHttps(image.originimgurl || image.smallimageurl))
      .filter(Boolean),
    infos: valueOf(infoResult)
      .map(normalizeInfo)
      .filter((info) => info.name && info.text),
  }
}
