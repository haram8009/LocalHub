import { ApiError } from '@/services/api'
import { backendRequest, queryString } from '@/services/backend'
import { CONTENT_TYPE_ROWS } from '@/services/lclsSystm'

export const ALL_CATEGORY = '전체'

// 카테고리(관광타입)의 이름과 코드는 명세의 관광타입 표 한 곳에서만 가져옵니다.
export const CATEGORY_CONTENT_TYPE_IDS = Object.fromEntries(CONTENT_TYPE_ROWS)

// 화면의 카테고리 선택지입니다. 화면마다 목록을 따로 만들지 않고 이 배열을 씁니다.
export const LOCATION_CATEGORIES = [ALL_CATEGORY, ...Object.keys(CATEGORY_CONTENT_TYPE_IDS)]

const CONTENT_TYPE_LABELS = Object.fromEntries(
  Object.entries(CATEGORY_CONTENT_TYPE_IDS).map(([label, id]) => [id, label]),
)

export function getContentTypeId(category) {
  return CATEGORY_CONTENT_TYPE_IDS[category] ?? ''
}

export function getContentTypeLabel(contentTypeId) {
  return CONTENT_TYPE_LABELS[String(contentTypeId)] ?? '기타'
}

function toArray(items) {
  const item = items?.item

  if (Array.isArray(item)) return item
  if (item && typeof item === 'object') return [item]

  return []
}

export function buildTourUrl(operation, params = {}) {
  return '/tour/' + encodeURIComponent(operation) + '?' + queryString(params)
}

/**
 * 한국관광공사 KorService2 API를 호출하고 목록 응답을 정규화합니다.
 *
 * @returns {Promise<{ items: object[], totalCount: number, pageNo: number, numOfRows: number }>}
 */
export async function tourRequest(operation, params = {}, { signal, showLoading = false } = {}) {
  const data = await backendRequest(buildTourUrl(operation, params), { signal, showLoading })

  // 인증 오류 등은 JSON 대신 XML 문자열로 오는 경우가 있습니다.
  if (typeof data === 'string') {
    throw new ApiError('관광정보 API 응답을 해석하지 못했습니다. 인증키를 확인해 주세요.', { data })
  }

  const header = data?.response?.header
  const body = data?.response?.body

  if (header?.resultCode !== '0000') {
    throw new ApiError(header?.resultMsg || '관광정보를 불러오지 못했습니다.', { data })
  }

  const items = toArray(body?.items)

  return {
    items,
    totalCount: Number(body?.totalCount ?? items.length),
    pageNo: Number(body?.pageNo ?? 1),
    numOfRows: Number(body?.numOfRows ?? items.length),
  }
}
