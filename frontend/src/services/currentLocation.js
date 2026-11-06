import { backendRequest, queryString } from '@/services/backend'
import { getSidoCodes, getSigunguCodes } from '@/services/tourCodes'

function requestCurrentPosition() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return Promise.reject(new Error('이 브라우저에서는 현재 위치 기능을 지원하지 않습니다.'))
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude }),
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          const permissionError = new Error(
            '브라우저 또는 Windows에서 위치 접근이 차단되어 있습니다. 위치 권한을 허용한 뒤 다시 확인해 주세요.',
          )
          permissionError.code = 'LOCATION_PERMISSION_DENIED'
          reject(permissionError)
          return
        }

        if (error.code === error.TIMEOUT) {
          reject(new Error('현재 위치 확인 시간이 초과되었습니다. 다시 시도해 주세요.'))
          return
        }

        reject(
          new Error(
            '기기의 위치 신호를 확인할 수 없습니다. 위치 설정을 확인하고 다시 시도해 주세요.',
          ),
        )
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 },
    )
  })
}

function normalizeRegionName(name) {
  const compact = String(name ?? '').replace(/\s/g, '')
  const aliases = { 서울특별시: '서울', 부산광역시: '부산', 대구광역시: '대구', 인천광역시: '인천', 광주광역시: '광주', 대전광역시: '대전', 울산광역시: '울산', 제주특별자치도: '제주도' }
  return aliases[compact] ?? compact
}

async function findLocationRegion(position) {
  const response = await backendRequest(
    '/regions?' + queryString({ x: position.longitude, y: position.latitude }),
    { showLoading: false },
  )
  const region = response.documents?.find((item) => item.region_type === 'B')

  if (!region?.region_1depth_name) {
    throw new Error('현재 위치의 시·구 정보를 찾을 수 없습니다.')
  }

  const sidoCodes = await getSidoCodes()
  const sido = sidoCodes.find(
    (item) => normalizeRegionName(item.name) === normalizeRegionName(region.region_1depth_name),
  )

  if (!sido) {
    throw new Error(`현재 위치의 시도 코드(${region.region_1depth_name})를 찾을 수 없습니다.`)
  }

  const sigunguName = region.region_2depth_name
  const sigunguCodes = sigunguName ? await getSigunguCodes(sido.code) : []
  const sigungu = sigunguCodes.find(
    (item) => normalizeRegionName(item.name) === normalizeRegionName(sigunguName),
  )

  if (sigunguName && !sigungu) {
    throw new Error(`현재 위치의 구·군 코드(${sigunguName})를 찾을 수 없습니다.`)
  }

  return {
    region: { sido: sido.code, sigungu: sigungu?.code ?? '' },
    regionName: [sido.name, sigungu?.name].filter(Boolean).join(' '),
  }
}

export async function getCurrentLocationContext() {
  const position = await requestCurrentPosition()
  // 주변 게시글 조회에는 좌표만 필요하므로 지역명 서비스 오류가 추천을 막지 않게 합니다.
  const locationRegion = await findLocationRegion(position).catch(() => ({
    region: { sido: '', sigungu: '' },
    regionName: '현재 위치',
  }))

  return { position, ...locationRegion }
}
