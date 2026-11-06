import { getNearbyPosts, getPosts } from '@/services/posts'

export async function getRecentPosts(limit = 3) {
  const response = await getPosts({ page: 1, size: limit })

  return response.items
}

export async function getRecommendedPosts(limit = 3, position) {
  if (!position) {
    throw new Error('현재 위치를 확인할 수 없어 추천 코스를 불러오지 못했습니다.')
  }

  const { items } = await getNearbyPosts(position, { limit })

  return items
}
