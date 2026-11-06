import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))

vi.mock('@/services/api', async (importOriginal) => ({
  ...(await importOriginal()),
  apiRequest,
}))

import { clearLocationMapCache } from '@/services/locationMapCache'
import {
  findLocationByName,
  getLocation,
  getLocationDetail,
  getLocationPage,
  getLocations,
  getNearbyLocations,
} from '@/services/locationService'

function tourResponse(item, { totalCount, pageNo = 1, numOfRows = 20, resultCode = '0000' } = {}) {
  const count = Array.isArray(item) ? item.length : item ? 1 : 0

  return {
    response: {
      header: { resultCode, resultMsg: resultCode === '0000' ? 'OK' : 'ERROR' },
      body: {
        items: item === undefined ? '' : { item },
        numOfRows,
        pageNo,
        totalCount: totalCount ?? count,
      },
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

const tourItem = {
  addr1: '서울특별시 중구 명동길 25-7',
  addr2: '',
  contentid: '134268',
  contenttypeid: '39',
  dist: '78.36967950987834',
  firstimage: 'http://tong.visitkorea.or.kr/a.jpg',
  firstimage2: 'http://tong.visitkorea.or.kr/b.jpg',
  mapx: '126.9837706082',
  mapy: '37.5641518402',
  title: '명동부대찌개',
}

describe('location service', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_TOUR_API_KEY', 'test-key')
    apiRequest.mockReset()
    clearLocationMapCache()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('requests a location-based page sorted by distance and normalizes items', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem], { totalCount: 45 }))

    const result = await getLocationPage({
      mode: 'viewport',
      category: '음식점',
      center: { mapX: 126.98, mapY: 37.56, radius: 1000 },
      page: 2,
      size: 20,
    })

    const { operation, params } = getRequest()

    expect(operation).toBe('locationBasedList2')
    expect(params).toMatchObject({
      mapX: '126.98',
      mapY: '37.56',
      radius: '1000',
      arrange: 'E',
      contentTypeId: '39',
      pageNo: '2',
      numOfRows: '20',
    })
    expect(result).toMatchObject({ totalCount: 45, page: 2, size: 20, totalPages: 3 })
    expect(result.items[0]).toMatchObject({
      id: '134268',
      contentId: '134268',
      contentTypeId: '39',
      name: '명동부대찌개',
      category: '음식점',
      region: '서울특별시 중구',
      latitude: 37.5641518402,
      longitude: 126.9837706082,
      thumbnailImageUrl: 'https://tong.visitkorea.or.kr/b.jpg',
      originalImageUrl: 'https://tong.visitkorea.or.kr/a.jpg',
    })
    expect(result.items[0].distanceKm).toBeCloseTo(0.0784, 3)
  })

  it('uses searchKeyword2 for keyword mode and omits contentTypeId for 전체', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))

    await getLocationPage({ mode: 'keyword', keyword: ' 시장 ', page: 1 })

    const { operation, params } = getRequest()

    expect(operation).toBe('searchKeyword2')
    expect(params.keyword).toBe('시장')
    expect(params.arrange).toBe('A')
    expect(params).not.toHaveProperty('contentTypeId')
  })

  it('uses areaBasedList2 with the legal-dong area code for area mode', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))

    await getLocationPage({ mode: 'area', areaCode: '11', category: '관광지', size: 1 })

    const { operation, params } = getRequest()

    expect(operation).toBe('areaBasedList2')
    expect(params).toMatchObject({ lDongRegnCd: '11', contentTypeId: '12', numOfRows: '1' })
  })

  it('sends the sigungu and classification codes with an area request', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))

    await getLocationPage({
      mode: 'area',
      areaCode: '26',
      sigunguCode: '350',
      category: '숙박',
      lclsSystm1: 'AC',
      lclsSystm2: 'AC03',
      lclsSystm3: 'AC030100',
    })

    const { operation, params } = getRequest()

    expect(operation).toBe('areaBasedList2')
    expect(params).toMatchObject({
      lDongRegnCd: '26',
      lDongSignguCd: '350',
      contentTypeId: '32',
      lclsSystm1: 'AC',
      lclsSystm2: 'AC03',
      lclsSystm3: 'AC030100',
    })
  })

  it('sends only the classification codes that belong to the category', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))
    const camping = { lclsSystm1: 'AC', lclsSystm2: 'AC05', lclsSystm3: 'AC050100' }

    // 캠핑(AC05)은 명세에서 레포츠 분류라 숙박 요청에는 대분류까지만 나갑니다.
    await getLocationPage({ mode: 'area', category: '숙박', ...camping })
    await getLocationPage({ mode: 'area', category: '레포츠', ...camping })
    await getLocationPage({ mode: 'area', lclsSystm1: 'AC', lclsSystm2: 'AC05' })

    expect(getRequest(0).params).toMatchObject({ contentTypeId: '32', lclsSystm1: 'AC' })
    expect(getRequest(0).params).not.toHaveProperty('lclsSystm2')
    expect(getRequest(0).params).not.toHaveProperty('lclsSystm3')
    expect(getRequest(1).params).toMatchObject({ contentTypeId: '28', ...camping })
    expect(getRequest(2).params).toMatchObject({
      lclsSystm1: 'AC',
      lclsSystm2: 'AC05',
    })
    expect(getRequest(2).params).not.toHaveProperty('contentTypeId')
  })

  it('applies the region to a keyword search and drops codes without their parent', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))

    await getLocationPage({
      mode: 'keyword',
      keyword: '시장',
      areaCode: '26',
      lclsSystm2: 'SH01',
      lclsSystm3: 'SH010100',
    })
    await getLocationPage({ mode: 'keyword', keyword: '바다', sigunguCode: '350' })

    const first = getRequest(0)
    const second = getRequest(1)

    expect(first.operation).toBe('searchKeyword2')
    expect(first.params.lDongRegnCd).toBe('26')
    expect(first.params).not.toHaveProperty('lclsSystm2')
    expect(first.params).not.toHaveProperty('lclsSystm3')
    expect(second.params).not.toHaveProperty('lDongSignguCd')
  })

  it.each([
    ['empty string items', undefined, 0],
    ['a single object item', tourItem, 1],
  ])('normalizes %s into an array', async (_name, item, length) => {
    apiRequest.mockResolvedValue(tourResponse(item))

    const result = await getLocationPage({ mode: 'keyword', keyword: '없음' })

    expect(result.items).toHaveLength(length)
  })

  it('keeps missing coordinates invalid instead of converting them to zero', async () => {
    apiRequest.mockResolvedValue(tourResponse([{ ...tourItem, mapx: '', mapy: '' }]))

    const result = await getLocationPage({ mode: 'keyword', keyword: '좌표 없음' })

    expect(Number.isNaN(result.items[0].latitude)).toBe(true)
    expect(Number.isNaN(result.items[0].longitude)).toBe(true)
  })

  it('throws when the API result code is not 0000', async () => {
    apiRequest.mockResolvedValue(tourResponse(undefined, { resultCode: '10' }))

    await expect(getLocationPage({ mode: 'keyword', keyword: '에러' })).rejects.toThrow('ERROR')
  })

  it('serves a repeated page request from the cache', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))
    const options = { mode: 'viewport', center: { mapX: 127, mapY: 37.5, radius: 2000 }, page: 1 }

    await getLocationPage(options)
    await getLocationPage(options)

    expect(apiRequest).toHaveBeenCalledTimes(1)
  })

  it('shares an in-flight request and still caches it after the caller aborts', async () => {
    let resolveRequest
    apiRequest.mockReturnValue(new Promise((resolve) => (resolveRequest = resolve)))
    const controller = new AbortController()
    const options = { mode: 'keyword', keyword: '해운대', page: 1 }

    const first = getLocationPage({ ...options, signal: controller.signal })
    const second = getLocationPage(options)
    controller.abort()

    await expect(first).rejects.toMatchObject({ name: 'AbortError' })

    resolveRequest(tourResponse([tourItem]))
    await expect(second).resolves.toMatchObject({ totalCount: 1 })
    await getLocationPage(options)

    expect(apiRequest).toHaveBeenCalledTimes(1)
  })

  it('maps getLocations bounds to a snapped location-based request', async () => {
    apiRequest.mockResolvedValue(tourResponse([tourItem]))

    const items = await getLocations({
      bounds: { minLat: 37.55, maxLat: 37.57, minLng: 126.97, maxLng: 126.99 },
      limit: 10,
    })

    const { operation, params } = getRequest()

    expect(operation).toBe('locationBasedList2')
    expect(params.radius).toBe('2000')
    expect(params.numOfRows).toBe('10')
    expect(items).toHaveLength(1)
  })

  it('loads a single location with detailCommon2', async () => {
    apiRequest.mockResolvedValue(tourResponse({ ...tourItem, overview: '소개<br>문구' }))

    const location = await getLocation('134268')

    expect(getRequest().operation).toBe('detailCommon2')
    expect(location).toMatchObject({ contentId: '134268', description: '소개\n문구' })
  })

  it('resolves a posted place name to a real tourism API location', async () => {
    apiRequest.mockResolvedValue(
      tourResponse([
        {
          ...tourItem,
          contentid: 'real-place-id',
          title: '해운대 해수욕장',
          addr1: '부산광역시 해운대구 해운대해변로 264',
        },
      ]),
    )

    const location = await findLocationByName('해운대해수욕장', '부산광역시 해운대구')

    expect(getRequest().operation).toBe('searchKeyword2')
    expect(getRequest().params.keyword).toBe('해운대해수욕장')
    expect(location).toMatchObject({
      contentId: 'real-place-id',
      name: '해운대 해수욕장',
      latitude: 37.5641518402,
      longitude: 126.9837706082,
    })
  })

  it('does not select an unrelated location when a posted place name cannot be matched', async () => {
    apiRequest.mockResolvedValue(
      tourResponse([{ ...tourItem, title: '전혀 다른 장소', addr1: '부산광역시 해운대구' }]),
    )

    await expect(findLocationByName('없는 장소', '부산광역시 해운대구')).rejects.toThrow(
      '해당하는 관광지 정보를 찾을 수 없습니다.',
    )
  })

  it('finds nearby locations around a location object without refetching it', async () => {
    apiRequest.mockResolvedValue(
      tourResponse([tourItem, { ...tourItem, contentid: '2', title: '근처' }]),
    )

    const nearby = await getNearbyLocations(
      { contentId: '134268', latitude: 37.56, longitude: 126.98 },
      { limit: 5 },
    )

    const { operation, params } = getRequest()

    expect(apiRequest).toHaveBeenCalledTimes(1)
    expect(operation).toBe('locationBasedList2')
    expect(params).toMatchObject({ radius: '5000', arrange: 'E', numOfRows: '6' })
    expect(nearby.map((item) => item.contentId)).toEqual(['2'])
  })

  it('combines intro, images, and repeated info while tolerating a failed part', async () => {
    apiRequest.mockImplementation(async (url) => {
      const operation = new URL(url, 'http://localhost').pathname.split('/').at(-1)

      if (operation === 'detailCommon2') return tourResponse({ overview: '설명' })
      if (operation === 'detailIntro2') {
        return tourResponse({ restdate: '연중무휴', parking: '가능', chkpet: '' })
      }
      if (operation === 'detailImage2') {
        return tourResponse([{ originimgurl: 'http://tong.visitkorea.or.kr/1.jpg' }])
      }

      throw new Error('info failed')
    })

    const detail = await getLocationDetail({ contentId: '126128', contentTypeId: '12' })

    expect(detail.overview).toBe('설명')
    expect(detail.intro).toEqual([
      { field: 'restdate', label: '쉬는 날', value: '연중무휴' },
      { field: 'parking', label: '주차', value: '가능' },
    ])
    expect(detail.images).toEqual(['https://tong.visitkorea.or.kr/1.jpg'])
    expect(detail.infos).toEqual([])
  })
})
