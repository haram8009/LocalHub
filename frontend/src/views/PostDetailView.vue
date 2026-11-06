<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppImage from '@/components/common/AppImage.vue'
import CourseKakaoMap from '@/components/map/CourseKakaoMap.vue'
import { findLocationByName, getLocation, getLocationDetail } from '@/services/locationService'
import { deletePost as deletePostRequest, getPost, getSimilarPosts } from '@/services/posts'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const mapRef = ref(null)
const post = ref(null)
const selectedPlaceId = ref(null)
const resolvedPlace = ref(null)
const selectedPlaceImages = ref([])
const placeInfoLoading = ref(false)
const placeInfoError = ref('')
const isLoading = ref(true)
const isDeleteModalOpen = ref(false)
const isDeleting = ref(false)
const deleteErrorMessage = ref('')
const similarPosts = ref([])
const similarLoading = ref(false)
const similarError = ref('')
let similarController
let postRequestId = 0

async function loadSimilarPosts() {
  similarController?.abort()
  const controller = new AbortController()
  similarController = controller
  similarLoading.value = true
  similarError.value = ''
  similarPosts.value = []
  try {
    const result = await getSimilarPosts(route.params.postId, { signal: controller.signal })
    if (!controller.signal.aborted) similarPosts.value = result.items
  } catch (error) {
    if (!controller.signal.aborted)
      similarError.value = error.message || '비슷한 코스를 불러오지 못했습니다.'
  } finally {
    if (!controller.signal.aborted) similarLoading.value = false
  }
}

const isAuthor = computed(
  () => Boolean(post.value?.authorId) && post.value.authorId === authStore.user?.id,
)

const storedSelectedPlace = computed(
  () =>
    post.value?.locations.find(
      (item) => String(item.contentId) === String(selectedPlaceId.value),
    ) ??
    post.value?.locations[0] ??
    null,
)
const selectedPlace = computed(() => {
  if (
    resolvedPlace.value &&
    String(resolvedPlace.value.sourceContentId) === String(storedSelectedPlace.value?.contentId)
  ) {
    return resolvedPlace.value.location
  }

  return storedSelectedPlace.value
})
const formattedCreatedAt = computed(() => {
  if (!post.value?.createdAt) return ''
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(post.value.createdAt))
    .replaceAll(' ', '')
})

function openLocationOnMap(location) {
  router.push({
    path: '/map',
    query: {
      placeId: location.contentId,
      placeName: location.name,
      placeAddress: location.address || undefined,
      postPlaceId: storedSelectedPlace.value?.contentId || undefined,
    },
  })
}

async function fetchPost() {
  const requestId = ++postRequestId
  similarController?.abort()
  similarPosts.value = []
  similarError.value = ''
  isLoading.value = true
  try {
    const result = await getPost(route.params.postId)
    if (requestId !== postRequestId) return
    post.value = result
    selectedPlaceId.value = post.value.locations[0]?.contentId ?? null
    loadSimilarPosts()
  } catch (error) {
    if (requestId !== postRequestId) return
    window.alert(
      error instanceof Error ? error.message : '게시글을 불러오는 중 오류가 발생했습니다.',
    )
    await router.replace('/posts')
  } finally {
    if (requestId === postRequestId) isLoading.value = false
  }
}

async function selectPlace(location) {
  const contentId = typeof location === 'object' ? location.contentId : location
  selectedPlaceId.value = contentId
  await nextTick()
  mapRef.value?.focusLocation(selectedPlace.value)
}

let placeInfoRequestId = 0

watch(
  storedSelectedPlace,
  async (location) => {
    const requestId = ++placeInfoRequestId
    resolvedPlace.value = null
    selectedPlaceImages.value = []
    placeInfoError.value = ''

    if (!location) return

    placeInfoLoading.value = true

    try {
      let tourismLocation

      try {
        tourismLocation = await getLocation(location.contentId)
      } catch {
        tourismLocation = await findLocationByName(location.name, location.address)
      }

      const detail = await getLocationDetail(tourismLocation)

      if (requestId !== placeInfoRequestId) return

      resolvedPlace.value = {
        sourceContentId: location.contentId,
        location: {
          ...location,
          ...tourismLocation,
          description: detail.overview || tourismLocation.description || location.description,
        },
      }
      selectedPlaceImages.value = [
        tourismLocation.thumbnailImageUrl,
        tourismLocation.originalImageUrl,
        ...detail.images,
      ].filter((image, index, images) => image && images.indexOf(image) === index)
    } catch (error) {
      if (requestId !== placeInfoRequestId) return

      placeInfoError.value =
        error instanceof Error ? error.message : '여행지 사진을 불러오지 못했습니다.'
    } finally {
      if (requestId === placeInfoRequestId) {
        placeInfoLoading.value = false
      }
    }
  },
  { immediate: true },
)

function openDeleteModal() {
  deleteErrorMessage.value = ''
  isDeleteModalOpen.value = true
}
function closeDeleteModal() {
  if (isDeleting.value) return
  isDeleteModalOpen.value = false
}
async function deletePost() {
  isDeleting.value = true
  deleteErrorMessage.value = ''
  try {
    await deletePostRequest(route.params.postId)
    window.alert('게시글이 삭제되었습니다.')
    await router.push('/posts')
  } catch (error) {
    deleteErrorMessage.value =
      error instanceof Error ? error.message : '게시글을 삭제하지 못했습니다.'
  } finally {
    isDeleting.value = false
  }
}

watch(() => route.params.postId, fetchPost, { immediate: true })
onBeforeUnmount(() => {
  similarController?.abort()
  postRequestId++
  placeInfoRequestId++
})
</script>

<template>
  <main class="detail-view">
    <div class="container">
      <div v-if="isLoading" class="page-state">게시글을 불러오고 있습니다.</div>

      <article v-else-if="post" class="post-detail">
        <header class="post-header">
          <div>
            <p class="eyebrow">LOCAL COURSE</p>
            <h1>{{ post.title }}</h1>
            <div class="meta">
              <span>{{ post.categoryLabel }}</span
              ><i>·</i><time :datetime="post.createdAt">{{ formattedCreatedAt }}</time>
              <template v-if="post.authorName"
                ><i>·</i><span class="author">{{ post.authorName }}</span></template
              >
            </div>
          </div>
          <div v-if="isAuthor" class="header-actions">
            <button type="button" @click="router.push(`/posts/${route.params.postId}/edit`)">
              수정</button
            ><button type="button" class="danger" @click="openDeleteModal">삭제</button>
          </div>
        </header>

        <section class="section content">
          <h2 class="sr-only">게시글 내용</h2>
          <p>{{ post.content }}</p>
        </section>

        <section class="section">
          <header class="section-header">
            <div>
              <p class="eyebrow">COURSE MAP</p>
              <h2>여행 코스 지도</h2>
            </div>
            <p>마커나 코스 목록을 선택하면 해당 장소로 이동합니다.</p>
          </header>
          <CourseKakaoMap
            ref="mapRef"
            :locations="post.locations"
            :selected-location-id="selectedPlaceId"
            height="520px"
            @select-location="selectPlace"
          />
        </section>

        <section class="section">
          <header class="section-header">
            <div>
              <p class="eyebrow">ITINERARY</p>
              <h2>코스 순서</h2>
            </div>
            <strong>총 {{ post.locations.length }}곳</strong>
          </header>
          <ol class="course-list">
            <li
              v-for="(location, index) in post.locations"
              :key="location.contentId"
              :class="{ selected: String(location.contentId) === String(selectedPlaceId) }"
            >
              <button type="button" @click="selectPlace(location)">
                <b>{{ index + 1 }}</b
                ><span
                  ><strong>{{ location.name }}</strong
                  ><small>{{ location.address }}</small></span
                ><i>›</i>
              </button>
            </li>
          </ol>
        </section>

        <section v-if="selectedPlace" class="section">
          <header class="section-header">
            <div>
              <p class="eyebrow">SELECTED PLACE</p>
              <h2>선택 여행지 정보</h2>
            </div>
          </header>
          <div class="place-card">
            <div class="place-gallery">
              <div class="place-icon">
                <AppImage
                  class="place-thumbnail"
                  :src="selectedPlaceImages[0] || selectedPlace.thumbnailImageUrl"
                  :fallback-src="selectedPlace.originalImageUrl"
                  :alt="`${selectedPlace.name} 이미지`"
                />
              </div>
            </div>
            <div>
              <span class="category">{{ selectedPlace.categoryLabel }}</span>
              <h3>{{ selectedPlace.name }}</h3>
              <p>{{ selectedPlace.address }}</p>
              <p class="place-description">
                {{ selectedPlace.description }}
              </p>
              <p v-if="placeInfoLoading" class="place-info-status" role="status">
                관광지 사진을 불러오는 중입니다.
              </p>
              <p
                v-else-if="placeInfoError"
                class="place-info-status place-info-status--error"
                role="status"
              >
                {{ placeInfoError }}
              </p>
              <div class="place-actions">
                <button type="button" class="primary" @click="openLocationOnMap(selectedPlace)">
                  지도에서 보기</button
                ><button
                  type="button"
                  @click="
                    router.push({ path: '/posts', query: { placeId: selectedPlace.contentId } })
                  "
                >
                  관련 게시글 보기
                </button>
              </div>
            </div>
          </div>
        </section>

        <section
          class="section similar-courses"
          aria-labelledby="similar-courses-title"
          :aria-busy="similarLoading"
        >
          <header class="section-header">
            <div>
              <p class="eyebrow">SIMILAR COURSES</p>
              <h2 id="similar-courses-title">비슷한 여행 코스</h2>
            </div>
            <p>함께 방문하는 장소와 방문 순서를 비교했어요.</p>
          </header>
          <p v-if="similarLoading" role="status">비슷한 코스를 찾고 있습니다.</p>
          <div v-else-if="similarError" class="similar-error" role="alert">
            <p>{{ similarError }}</p>
            <button type="button" @click="loadSimilarPosts">다시 시도</button>
          </div>
          <ul v-else-if="similarPosts.length" class="similar-list">
            <li v-for="item in similarPosts" :key="item.id">
              <RouterLink :to="`/posts/${item.id}`" class="similar-card">
                <div class="similar-card-heading">
                  <strong>{{ item.title }}</strong>
                  <span class="similar-score">유사도 {{ Math.round(item.score * 100) }}%</span>
                </div>
                <p>
                  공통 관광지 {{ item.commonPlaceCount }}곳 · 같은 방문 순서 {{ item.lcsLength }}곳
                </p>
                <small>{{ item.authorName }} · 총 {{ item.locationCount }}곳</small>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="similar-empty">아직 공통 관광지가 있는 다른 코스가 없습니다.</p>
        </section>

        <footer class="footer">
          <button type="button" @click="router.push('/posts')">목록으로</button>
        </footer>
      </article>
    </div>

    <Teleport to="body">
      <div v-if="isDeleteModalOpen" class="overlay" @click.self="closeDeleteModal">
        <section class="modal" role="dialog" aria-modal="true" aria-labelledby="delete-title">
          <header>
            <div>
              <p class="eyebrow">DELETE POST</p>
              <h2 id="delete-title">게시글 삭제</h2>
            </div>
            <button type="button" :disabled="isDeleting" @click="closeDeleteModal">×</button>
          </header>
          <p>이 게시글을 삭제할까요? 삭제한 게시글은 복구할 수 없습니다.</p>
          <form @submit.prevent="deletePost">
            <small v-if="deleteErrorMessage">{{ deleteErrorMessage }}</small>
            <div>
              <button type="button" :disabled="isDeleting" @click="closeDeleteModal">취소</button
              ><button type="submit" class="danger-fill" :disabled="isDeleting">
                {{ isDeleting ? '삭제 중...' : '삭제하기' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
.similar-list {
  display: grid;
  gap: var(--spacing-3);
  margin: 0;
  padding: 0;
  list-style: none;
}
.similar-card {
  display: block;
  padding: var(--spacing-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  text-decoration: none;
}
.similar-card:hover {
  border-color: var(--color-primary);
}
.similar-card-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}
.similar-score {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}
.similar-card p {
  margin: var(--spacing-2) 0;
}
.similar-card small,
.similar-empty {
  color: var(--color-text-secondary);
}
.similar-error button {
  cursor: pointer;
}
.detail-view {
  min-height: calc(100vh - var(--header-height));
  padding: var(--spacing-8) 0 var(--spacing-16);
}
.page-state {
  display: grid;
  place-items: center;
  min-height: 480px;
  color: var(--color-text-secondary);
}
.post-detail {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}
.post-header {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-5);
  padding: var(--spacing-6);
  background: var(--color-surface-muted);
  border-bottom: 1px solid var(--color-border);
}
.post-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
}
.eyebrow {
  margin: 0 0 var(--spacing-2);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.12em;
}
.meta {
  display: flex;
  gap: var(--spacing-2);
  margin-top: var(--spacing-3);
  color: var(--color-text-secondary);
}
.meta span {
  color: var(--color-primary);
  font-weight: 600;
}
.meta .author {
  color: var(--color-text-secondary);
}
.header-actions,
.place-actions {
  display: flex;
  gap: var(--spacing-2);
}
.header-actions button {
  max-height: 40px;
  margin-top: auto;
}
button {
  min-height: 40px;
  padding: 0 var(--spacing-4);
  color: var(--color-text-secondary);
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-weight: 600;
  cursor: pointer;
}
.danger {
  color: var(--color-error);
  border-color: rgb(220 90 90 / 35%);
}
.section {
  padding: var(--spacing-6);
  border-bottom: 1px solid var(--color-border-light);
}
.content p {
  margin: 0;
  line-height: 1.9;
  white-space: pre-wrap;
}
.section-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);
}
.section-header h2,
.section-header p {
  margin: 0;
}
.section-header > p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
.course-list {
  display: grid;
  gap: var(--spacing-2);
  margin: 0;
  padding: 0;
  list-style: none;
}
.course-list li {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}
.course-list li.selected {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}
.course-list button {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--spacing-3);
  width: 100%;
  height: auto;
  padding: var(--spacing-3);
  text-align: left;
  background: transparent;
  border: 0;
}
.course-list b {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: white;
  background: var(--color-primary);
  border-radius: 50%;
}
.course-list span {
  display: grid;
  gap: 3px;
}
.course-list small {
  color: var(--color-text-secondary);
}
.place-card {
  display: grid;
  grid-template-columns: minmax(160px, 240px) 1fr;
  gap: var(--spacing-5);
  padding: var(--spacing-5);
  background: var(--color-surface-muted);
  border-radius: var(--radius-md);
}
.place-gallery {
  display: grid;
  align-content: start;
  gap: var(--spacing-2);
}
.place-icon {
  display: grid;
  place-items: center;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  background: var(--color-primary-light);
  border-radius: var(--radius-md);
}
.place-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.place-info-status {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
.place-info-status--error {
  color: var(--color-error);
}
.place-description {
  margin: var(--spacing-2) 0 0;
  overflow: hidden;
  line-height: 1.7;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
.place-card h3 {
  margin: var(--spacing-1) 0;
  font-size: var(--font-size-xl);
}
.place-card p {
  color: var(--color-text-secondary);
}
.category {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}
.primary {
  color: white;
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.footer {
  padding: var(--spacing-5);
  text-align: right;
}
.overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-index-modal);
  display: grid;
  place-items: center;
  padding: var(--spacing-4);
  background: var(--color-overlay);
}
.modal {
  width: min(100%, 460px);
  padding: var(--spacing-6);
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}
.modal header {
  display: flex;
  justify-content: space-between;
}
.modal h2 {
  margin: 0;
}
.modal form {
  display: grid;
  gap: var(--spacing-2);
}
.modal small {
  color: var(--color-error);
}
.modal form > div {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-2);
  margin-top: var(--spacing-3);
}
.danger-fill {
  color: white;
  background: var(--color-error);
  border-color: var(--color-error);
}
@media (max-width: 700px) {
  .post-header,
  .section-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .place-card {
    grid-template-columns: 1fr;
  }
  .place-gallery {
    grid-template-columns: 1fr;
  }
  .header-actions,
  .place-actions {
    width: 100%;
  }
  .header-actions button,
  .place-actions button {
    flex: 1;
  }
}
</style>
