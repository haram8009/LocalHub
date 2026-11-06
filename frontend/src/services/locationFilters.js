import { ALL_CATEGORY, LOCATION_CATEGORIES } from '@/services/tourApi'
import { normalizeClassification } from '@/services/tourCodes'

/**
 * 여행지 검색 조건을 화면과 상관없이 같은 규칙으로 정리합니다.
 * 모르는 카테고리는 전체로 바꾸고, 시도가 없는 구군과 카테고리에 속하지 않는 분류체계 코드는 비웁니다.
 *
 * @param {object} [filters]
 * @param {string} [filters.keyword]
 * @param {string} [filters.category] 카테고리 라벨(전체, 관광지, 숙박 ...)
 * @param {{ sido?: string, sigungu?: string }} [filters.region] 법정동 시도·시군구 코드
 * @param {{ lclsSystm1?: string, lclsSystm2?: string, lclsSystm3?: string }} [filters.classification]
 */
export function normalizeLocationFilters(filters = {}) {
  const category = LOCATION_CATEGORIES.includes(filters.category) ? filters.category : ALL_CATEGORY
  const classificationCategory =
    !filters.category || filters.category === ALL_CATEGORY
      ? ALL_CATEGORY
      : LOCATION_CATEGORIES.includes(filters.category)
        ? filters.category
        : ''
  const sido = filters.region?.sido ?? ''

  return {
    keyword: String(filters.keyword ?? '').trim(),
    category,
    region: { sido, sigungu: sido ? (filters.region?.sigungu ?? '') : '' },
    classification: normalizeClassification(classificationCategory, filters.classification),
  }
}

export function createLocationFilters() {
  return normalizeLocationFilters({
    category: ALL_CATEGORY,
    classification: { lclsSystm1: 'FD' },
  })
}

/**
 * 검색 조건을 getLocationPage에 넘길 조회 옵션으로 바꿉니다(mode·page 제외).
 */
export function toLocationQuery(filters) {
  return {
    keyword: filters.keyword,
    category: filters.category === ALL_CATEGORY ? '' : filters.category,
    areaCode: filters.region.sido,
    sigunguCode: filters.region.sigungu,
    ...filters.classification,
  }
}
