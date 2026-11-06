import { backendRequest, queryString } from '@/services/backend'
import { normalizeLocationResponse } from '@/services/locationService'

function normalizePostLocation(location, index) {
  return {
    ...normalizeLocationResponse(location),
    visitOrder: Number(location.visitOrder ?? index + 1),
  }
}

export function normalizePost(post) {
  return {
    id: Number(post.id),
    category: post.category ?? '',
    categoryLabel: post.categoryLabel ?? '여행 코스',
    title: post.title ?? '',
    content: post.content ?? '',
    authorId: post.authorId ?? '',
    authorName: post.authorName ?? '',
    viewCount: Number(post.viewCount ?? 0),
    createdAt: post.createdAt ?? '',
    updatedAt: post.updatedAt ?? '',
    ...(post.relevanceScore !== undefined ? { relevanceScore: Number(post.relevanceScore) } : {}),
    locationCount: Number(post.locations?.length ?? 0),
    locations: Array.isArray(post.locations)
      ? post.locations.map(normalizePostLocation).sort((a, b) => a.visitOrder - b.visitOrder)
      : [],
  }
}

function payload(post) {
  return {
    category: post.category || 'COURSE',
    title: post.title,
    content: post.content,
    locations: (post.locations || []).map((location, index) => ({
      ...normalizeLocationResponse(location),
      visitOrder: index + 1,
    })),
  }
}
export async function getPosts(params = {}) {
  const path =
    typeof params.query === 'string' && params.query.trim() ? '/posts/search?' : '/posts?'
  const result = await backendRequest(path + queryString(params))
  return { ...result, items: result.items.map(normalizePost) }
}
export async function getPost(id) {
  return normalizePost(await backendRequest('/posts/' + encodeURIComponent(id)))
}
export async function createPost(post) {
  return normalizePost(await backendRequest('/posts', { method: 'POST', body: payload(post) }))
}
export async function updatePost(id, post) {
  return normalizePost(
    await backendRequest('/posts/' + encodeURIComponent(id), {
      method: 'PUT',
      body: payload(post),
    }),
  )
}
export const deletePost = (id) =>
  backendRequest('/posts/' + encodeURIComponent(id), { method: 'DELETE' })

export async function getNearbyPosts({ latitude, longitude }, { limit = 3 } = {}) {
  const result = await backendRequest(
    '/posts/nearby?' + queryString({ latitude, longitude, limit }),
    { showLoading: false },
  )
  return {
    ...result,
    items: result.items.map((item) => ({
      ...normalizePost(item),
      locationCount: Number(item.locationCount ?? 0),
      distanceKm: Number(item.distanceKm),
      nearestLocation: normalizeLocationResponse(item.nearestLocation),
    })),
  }
}

export async function getSimilarPosts(id, { limit = 3, signal } = {}) {
  const result = await backendRequest(
    '/posts/' + encodeURIComponent(id) + '/similar?' + queryString({ limit }),
    { signal, showLoading: false },
  )
  return {
    ...result,
    items: result.items.map((item) => ({
      ...item,
      ...normalizePost(item),
      locationCount: item.locationCount ?? 0,
    })),
  }
}
