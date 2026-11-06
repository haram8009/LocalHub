const EARTH_RADIUS_KM = 6371
const KOREA_BOUNDS = {
  minLatitude: 32,
  maxLatitude: 40,
  minLongitude: 123,
  maxLongitude: 133,
}

export function isValidKoreanLocation(location) {
  const latitude = Number(location?.latitude)
  const longitude = Number(location?.longitude)

  return (
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= KOREA_BOUNDS.minLatitude &&
    latitude <= KOREA_BOUNDS.maxLatitude &&
    longitude >= KOREA_BOUNDS.minLongitude &&
    longitude <= KOREA_BOUNDS.maxLongitude
  )
}

function toRadians(degree) {
  return (degree * Math.PI) / 180
}

/**
 * 두 위도·경도 사이의 직선거리를 Haversine 공식으로 계산합니다.
 *
 * @param {{ latitude: number, longitude: number }} from
 * @param {{ latitude: number, longitude: number }} to
 * @returns {number} 거리(km)
 */
export function calculateDistanceKm(from, to) {
  const fromLatitude = Number(from.latitude)
  const fromLongitude = Number(from.longitude)
  const toLatitude = Number(to.latitude)
  const toLongitude = Number(to.longitude)

  if (
    !Number.isFinite(fromLatitude) ||
    !Number.isFinite(fromLongitude) ||
    !Number.isFinite(toLatitude) ||
    !Number.isFinite(toLongitude)
  ) {
    return Number.POSITIVE_INFINITY
  }

  const latitudeDifference = toRadians(toLatitude - fromLatitude)
  const longitudeDifference = toRadians(toLongitude - fromLongitude)

  const startLatitude = toRadians(fromLatitude)
  const endLatitude = toRadians(toLatitude)

  const haversineValue =
    Math.sin(latitudeDifference / 2) ** 2 +
    Math.cos(startLatitude) * Math.cos(endLatitude) * Math.sin(longitudeDifference / 2) ** 2

  const angularDistance = 2 * Math.atan2(Math.sqrt(haversineValue), Math.sqrt(1 - haversineValue))

  return EARTH_RADIUS_KM * angularDistance
}

/**
 * 거리를 사용자 표시 문자열로 변환합니다.
 *
 * @param {number} distanceKm
 * @returns {string}
 */
export function formatDistance(distanceKm) {
  if (!Number.isFinite(distanceKm)) {
    return '-'
  }

  if (distanceKm < 1) {
    return `${Math.round(distanceKm * 1000)}m`
  }

  return `${distanceKm.toFixed(1)}km`
}
