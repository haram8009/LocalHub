import { describe, expect, it } from 'vitest'

import {
  createLocationFilters,
  normalizeLocationFilters,
  toLocationQuery,
} from '@/services/locationFilters'

const noClassification = { lclsSystm1: '', lclsSystm2: '', lclsSystm3: '' }

describe('location filters', () => {
  it('starts with the food classification', () => {
    expect(createLocationFilters()).toEqual({
      keyword: '',
      category: '전체',
      region: { sido: '', sigungu: '' },
      classification: { lclsSystm1: 'FD', lclsSystm2: '', lclsSystm3: '' },
    })
  })

  it('keeps a combination that the definition table allows', () => {
    const filters = {
      keyword: '바다',
      category: '숙박',
      region: { sido: '26', sigungu: '350' },
      classification: { lclsSystm1: 'AC', lclsSystm2: 'AC03', lclsSystm3: 'AC030100' },
    }

    expect(normalizeLocationFilters(filters)).toEqual(filters)
  })

  it('falls back to all categories for an unknown category and drops its classification', () => {
    expect(
      normalizeLocationFilters({ category: '축제공연행사', classification: { lclsSystm1: 'EV' } }),
    ).toMatchObject({ category: '전체', classification: noClassification })
  })

  it('drops classification codes that the category does not have', () => {
    // 캠핑(AC05)은 숙박 대분류 아래에 있지만 명세의 관광타입은 레포츠입니다.
    const camping = { lclsSystm1: 'AC', lclsSystm2: 'AC05', lclsSystm3: 'AC050100' }

    expect(normalizeLocationFilters({ category: '숙박', classification: camping })).toMatchObject({
      classification: { lclsSystm1: 'AC', lclsSystm2: '', lclsSystm3: '' },
    })
    expect(normalizeLocationFilters({ category: '레포츠', classification: camping })).toMatchObject(
      { classification: camping },
    )
    expect(normalizeLocationFilters({ category: '전체', classification: camping })).toMatchObject({
      classification: camping,
    })
  })

  it('drops a sigungu without a sido and trims the keyword', () => {
    expect(
      normalizeLocationFilters({ keyword: '  시장 ', region: { sido: '', sigungu: '350' } }),
    ).toMatchObject({ keyword: '시장', region: { sido: '', sigungu: '' } })
  })

  it('builds the options for getLocationPage', () => {
    expect(
      toLocationQuery(
        normalizeLocationFilters({
          keyword: '바다',
          category: '숙박',
          region: { sido: '26', sigungu: '350' },
          classification: { lclsSystm1: 'AC', lclsSystm2: 'AC03' },
        }),
      ),
    ).toEqual({
      keyword: '바다',
      category: '숙박',
      areaCode: '26',
      sigunguCode: '350',
      lclsSystm1: 'AC',
      lclsSystm2: 'AC03',
      lclsSystm3: '',
    })
    expect(toLocationQuery(createLocationFilters())).toMatchObject({
      keyword: '',
      category: '',
      lclsSystm1: 'FD',
    })
  })
})
