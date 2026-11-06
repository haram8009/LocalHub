import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('@/services/api', async (importOriginal) => ({
  ...(await importOriginal()),
  apiRequest,
}))

import { CONTENT_TYPE_ROWS, LCLS_SYSTM_ROWS } from '@/services/lclsSystm'
import { getContentTypeId, LOCATION_CATEGORIES } from '@/services/tourApi'
import {
  clearTourCodeCache,
  getClassificationCodes,
  getSidoCodes,
  getSigunguCodes,
  normalizeClassification,
} from '@/services/tourCodes'

function codeResponse(item) {
  return {
    response: {
      header: { resultCode: '0000', resultMsg: 'OK' },
      body: { items: { item }, numOfRows: 100, pageNo: 1, totalCount: item.length },
    },
  }
}

function getRequest(callIndex = 0) {
  const url = new URL(apiRequest.mock.calls[callIndex][0], 'http://localhost')

  return {
    operation: url.pathname.split('/').at(-1),
    params: Object.fromEntries(url.searchParams),
  }
}

describe('tour code service', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_TOUR_API_KEY', 'test-key')
    apiRequest.mockReset()
    clearTourCodeCache()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('loads sido codes once and reuses them', async () => {
    apiRequest.mockResolvedValue(codeResponse([{ code: 11, name: '서울특별시', rnum: 1 }]))

    const first = await getSidoCodes()
    const second = await getSidoCodes()

    const { operation, params } = getRequest()

    expect(operation).toBe('ldongCode2')
    expect(params).toMatchObject({ lDongListYn: 'N', numOfRows: '100' })
    expect(params).not.toHaveProperty('lDongRegnCd')
    expect(first).toEqual([{ code: '11', name: '서울특별시' }])
    expect(second).toBe(first)
    expect(apiRequest).toHaveBeenCalledTimes(1)
  })

  it('loads sigungu codes for a sido and skips the request without one', async () => {
    apiRequest.mockResolvedValue(codeResponse([{ code: '350', name: '해운대구', rnum: 1 }]))

    expect(await getSigunguCodes('')).toEqual([])
    expect(apiRequest).not.toHaveBeenCalled()

    expect(await getSigunguCodes('26')).toEqual([{ code: '350', name: '해운대구' }])
    expect(getRequest().params.lDongRegnCd).toBe('26')
  })

  it('retries after a failed request instead of caching the failure', async () => {
    apiRequest.mockRejectedValueOnce(new Error('network error'))
    apiRequest.mockResolvedValue(codeResponse([{ code: '11', name: '서울특별시', rnum: 1 }]))

    await expect(getSidoCodes()).rejects.toThrow('network error')
    await expect(getSidoCodes()).resolves.toHaveLength(1)
  })
})

describe('classification system', () => {
  it('holds the content types of the definition table as the only category list', () => {
    expect(CONTENT_TYPE_ROWS).toEqual([
      ['관광지', '12'],
      ['문화시설', '14'],
      ['축제/공연/행사', '15'],
      ['여행코스', '25'],
      ['레포츠', '28'],
      ['숙박', '32'],
      ['쇼핑', '38'],
      ['음식점', '39'],
    ])
    expect(LOCATION_CATEGORIES).toEqual(['전체', ...CONTENT_TYPE_ROWS.map(([name]) => name)])
    expect(getContentTypeId('축제/공연/행사')).toBe('15')
    expect(getContentTypeId('전체')).toBe('')
  })

  it('holds the 240 sub classes of the definition table and nothing else', () => {
    const countByContentType = {}

    LCLS_SYSTM_ROWS.forEach(([code1, , code2, , code3, , contentTypeId]) => {
      expect(code2.startsWith(code1) && code3.startsWith(code2)).toBe(true)
      countByContentType[contentTypeId] = (countByContentType[contentTypeId] ?? 0) + 1
    })

    expect(LCLS_SYSTM_ROWS).toHaveLength(240)
    expect(new Set(LCLS_SYSTM_ROWS.map((row) => row[0])).size).toBe(10)
    expect(new Set(LCLS_SYSTM_ROWS.map((row) => row[2])).size).toBe(58)
    expect(new Set(LCLS_SYSTM_ROWS.map((row) => row[4])).size).toBe(240)
    expect(countByContentType).toEqual({
      12: 105,
      14: 17,
      15: 20,
      25: 6,
      28: 47,
      32: 11,
      38: 13,
      39: 21,
    })
    // 교통시설(VE11)은 API 코드 목록에는 있지만 명세에 없어 어느 관광타입에도 연결하지 않습니다.
    expect(LCLS_SYSTM_ROWS.some((row) => row[2] === 'VE11')).toBe(false)
  })

  it.each([
    ['관광지', ['EX', 'HS', 'NA', 'VE']],
    ['문화시설', ['VE']],
    ['축제/공연/행사', ['EV']],
    ['여행코스', ['C01']],
    ['레포츠', ['AC', 'LS', 'VE']],
    ['숙박', ['AC', 'VE']],
    ['쇼핑', ['SH']],
    ['음식점', ['FD']],
  ])('lists the main classes of %s in the order of the definition table', (category, codes) => {
    expect(getClassificationCodes({ category }).map(({ code }) => code)).toEqual(codes)
  })

  it('lists all main classes for the all category in definition-table order', () => {
    expect(getClassificationCodes({ category: '전체' })).toEqual([
      { code: 'AC', name: '숙박' },
      { code: 'EV', name: '축제/공연/행사' },
      { code: 'EX', name: '체험관광' },
      { code: 'FD', name: '음식' },
      { code: 'HS', name: '역사관광' },
      { code: 'LS', name: '레저스포츠' },
      { code: 'NA', name: '자연관광' },
      { code: 'SH', name: '쇼핑' },
      { code: 'VE', name: '문화관광' },
      { code: 'C01', name: '추천코스' },
    ])
    expect(getClassificationCodes({ category: '전체', lclsSystm1: 'AC' })).toContainEqual({
      code: 'AC05',
      name: '캠핑',
    })
  })

  it('has no classification for an unknown category', () => {
    expect(getClassificationCodes()).toEqual([])
    expect(getClassificationCodes({ category: '축제공연행사' })).toEqual([])
  })

  it('keeps only the middle classes that belong to the category', () => {
    const middleCodes = (category, lclsSystm1) =>
      getClassificationCodes({ category, lclsSystm1 }).map(({ code }) => code)

    // 캠핑(AC05)은 숙박 대분류에 있지만 관광타입은 레포츠입니다.
    expect(middleCodes('숙박', 'AC')).toEqual(['AC01', 'AC02', 'AC03', 'AC04', 'AC06'])
    expect(middleCodes('레포츠', 'AC')).toEqual(['AC05'])
    expect(middleCodes('숙박', 'VE')).toEqual(['VE05'])
    expect(middleCodes('문화시설', 'VE')).toEqual(['VE06', 'VE07', 'VE08', 'VE09', 'VE12'])
    expect(middleCodes('관광지', 'VE')).toEqual(['VE01', 'VE02', 'VE03', 'VE04', 'VE05'])
    expect(middleCodes('레포츠', 'VE')).toEqual(['VE10', 'VE12'])
    expect(middleCodes('쇼핑', 'AC')).toEqual([])
  })

  it('keeps only the sub classes that belong to the category', () => {
    const subClasses = (category, lclsSystm1, lclsSystm2) =>
      getClassificationCodes({ category, lclsSystm1, lclsSystm2 })

    // 같은 중분류 안에서도 소분류마다 관광타입이 다릅니다.
    expect(subClasses('관광지', 'VE', 'VE05')).toEqual([{ code: 'VE050100', name: '관광단지' }])
    expect(subClasses('숙박', 'VE', 'VE05')).toEqual([{ code: 'VE050200', name: '리조트' }])
    expect(subClasses('레포츠', 'VE', 'VE12')).toEqual([{ code: 'VE120200', name: '카지노' }])
    // 추천코스 소분류 코드만 명세 표기(C0112001) 대신 API가 인식하는 9자리로 적습니다.
    expect(subClasses('여행코스', 'C01', 'C0112')).toEqual([
      { code: 'C01120001', name: '가족코스' },
    ])
    expect(subClasses('숙박', '', 'AC03')).toEqual(getClassificationCodes({ category: '숙박' }))
  })

  it('drops codes that are not under the category or their parent', () => {
    expect(
      normalizeClassification('숙박', {
        lclsSystm1: 'AC',
        lclsSystm2: 'AC03',
        lclsSystm3: 'AC030200',
      }),
    ).toEqual({ lclsSystm1: 'AC', lclsSystm2: 'AC03', lclsSystm3: 'AC030200' })
    expect(
      normalizeClassification('숙박', {
        lclsSystm1: 'AC',
        lclsSystm2: 'AC05',
        lclsSystm3: 'AC050100',
      }),
    ).toEqual({ lclsSystm1: 'AC', lclsSystm2: '', lclsSystm3: '' })
    expect(
      normalizeClassification('숙박', {
        lclsSystm1: 'AC',
        lclsSystm2: 'AC03',
        lclsSystm3: 'AC010100',
      }),
    ).toEqual({ lclsSystm1: 'AC', lclsSystm2: 'AC03', lclsSystm3: '' })
    expect(normalizeClassification('숙박', { lclsSystm2: 'AC03' })).toEqual({
      lclsSystm1: '',
      lclsSystm2: '',
      lclsSystm3: '',
    })
    expect(normalizeClassification('전체', { lclsSystm1: 'AC' }).lclsSystm1).toBe('AC')
  })
})
