import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
const tourCodes = vi.hoisted(() => ({
  getSidoCodes: vi.fn(),
  getSigunguCodes: vi.fn(),
}))

vi.mock('@/services/api', () => ({ apiRequest }))
vi.mock('@/services/tourCodes', () => tourCodes)

import { getCurrentLocationContext } from '@/services/currentLocation'

describe('current location service', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_KAKAO_REST_API_KEY', 'test-rest-key')
    apiRequest.mockReset()
    tourCodes.getSidoCodes.mockReset()
    tourCodes.getSigunguCodes.mockReset()
    tourCodes.getSidoCodes.mockResolvedValue([{ code: '26', name: '부산광역시' }])
    tourCodes.getSigunguCodes.mockResolvedValue([{ code: '350', name: '해운대구' }])
    vi.stubGlobal('navigator', {
      geolocation: {
        getCurrentPosition: vi.fn((success) =>
          success({ coords: { latitude: 35.16, longitude: 129.16 } }),
        ),
      },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('resolves current coordinates to tourism API city and district codes', async () => {
    apiRequest.mockResolvedValue({
      documents: [
        {
          region_type: 'B',
          region_1depth_name: '부산광역시',
          region_2depth_name: '해운대구',
        },
      ],
    })

    await expect(getCurrentLocationContext()).resolves.toEqual({
      position: { latitude: 35.16, longitude: 129.16 },
      region: { sido: '26', sigungu: '350' },
      regionName: '부산광역시 해운대구',
    })

    const [url, options] = apiRequest.mock.calls[0]
    expect(url).toContain('x=129.16')
    expect(url).toContain('y=35.16')
    expect(url).toContain('/api/regions?')
    expect(options.headers.has('Authorization')).toBe(false)
    expect(tourCodes.getSigunguCodes).toHaveBeenCalledWith('26')
  })

  it('reports denied location permission instead of fabricating a region', async () => {
    navigator.geolocation.getCurrentPosition.mockImplementation((_success, error) =>
      error({ code: 1, PERMISSION_DENIED: 1 }),
    )

    await expect(getCurrentLocationContext()).rejects.toMatchObject({
      code: 'LOCATION_PERMISSION_DENIED',
      message: expect.stringContaining('위치 권한을 허용'),
    })
    expect(apiRequest).not.toHaveBeenCalled()
  })

  it('keeps usable coordinates for nearby posts when region lookup fails', async () => {
    apiRequest.mockRejectedValue(new Error('지역 서비스 오류'))

    await expect(getCurrentLocationContext()).resolves.toMatchObject({
      position: { latitude: 35.16, longitude: 129.16 },
      regionName: '현재 위치',
    })
  })
})
