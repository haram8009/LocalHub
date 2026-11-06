<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import AppError from '@/components/common/AppError.vue'
import { getCurrentLocationContext } from '@/services/currentLocation'
import { getRecentPosts, getRecommendedPosts } from '@/services/home'
import { formatDistance } from '@/utils/geo'

const recentPosts = ref([])
const recommendedPosts = ref([])

const postsError = ref('')
const recommendationsError = ref('')
const locationPermissionDenied = ref(false)
const isLoadingRecommendations = ref(false)

const selectedRegion = ref('현재 위치')

const regionDescription = computed(() => {
  if (selectedRegion.value === '전국') {
    return '대한민국 곳곳의 여행지를 발견하고 나만의 여행 코스를 만들어보세요.'
  }

  return `${selectedRegion.value}의 여행지를 둘러보고 나만의 코스를 만들어보세요.`
})

function getPostId(post) {
  return post.id ?? post.post_id
}

function getPostCategory(post) {
  return post.categoryLabel ?? '여행 코스'
}

function getPostSummary(post) {
  return (
    post.content_summary ??
    post.summary ??
    post.content ??
    '작성자가 공유한 여행 코스를 확인해보세요.'
  )
}

function getPostAuthor(post) {
  return (
    post.authorName ?? post.author_name ?? post.author?.name ?? post.nickname ?? 'LocalHub 사용자'
  )
}

function formatDate(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(date)
}

async function loadRecentPosts() {
  postsError.value = ''

  try {
    recentPosts.value = await getRecentPosts(3)
  } catch (error) {
    postsError.value = error.message ?? '최근 게시글을 불러오지 못했습니다.'
  }
}

async function loadRecommendedPosts() {
  if (isLoadingRecommendations.value) return

  isLoadingRecommendations.value = true
  recommendationsError.value = ''
  locationPermissionDenied.value = false
  recommendedPosts.value = []

  try {
    const { position, regionName } = await getCurrentLocationContext()
    selectedRegion.value = regionName
    recommendedPosts.value = await getRecommendedPosts(3, position)
  } catch (error) {
    locationPermissionDenied.value = error.code === 'LOCATION_PERMISSION_DENIED'
    recommendationsError.value = error.message ?? '주변 장소가 포함된 게시글을 불러오지 못했습니다.'
  } finally {
    isLoadingRecommendations.value = false
  }
}

onMounted(() => {
  Promise.all([loadRecentPosts(), loadRecommendedPosts()])
})
</script>

<template>
  <div class="home-view">
    <!-- 소개 배너 -->
    <section class="hero-section">
      <div class="container hero-container">
        <div class="hero-content">
          <div class="region-badge">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
              <circle cx="12" cy="10" r="2.5" />
            </svg>

            <span>for</span>
            <strong>{{ selectedRegion }}</strong>
          </div>

          <h1 class="hero-title">
            여행지를 연결해<br />
            나만의 코스를 공유하세요
          </h1>

          <p class="hero-description">
            {{ regionDescription }}
            마음에 드는 장소를 지도에서 찾고 다른 사용자와 여행 경험을 나눌 수 있습니다.
          </p>

          <div class="hero-actions">
            <RouterLink to="/map" class="hero-button hero-button--primary">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m9 18-6 3V6l6-3 6 3 6-3v15l-6 3-6-3Z" />
                <path d="M9 3v15M15 6v15" />
              </svg>

              여행지 둘러보기
            </RouterLink>

            <RouterLink to="/posts/create" class="hero-button hero-button--secondary">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z" />
              </svg>

              코스 공유하기
            </RouterLink>
          </div>
        </div>

        <!-- 지도 형태의 장식 영역 -->
        <div class="hero-map" aria-hidden="true">
          <div class="map-grid"></div>

          <div class="map-road map-road--first"></div>
          <div class="map-road map-road--second"></div>

          <span class="map-pin map-pin--first">
            <span></span>
          </span>

          <span class="map-pin map-pin--second">
            <span></span>
          </span>

          <span class="map-pin map-pin--third">
            <span></span>
          </span>

          <div class="map-course">
            <span>나만의 여행 코스</span>
            <strong>3개의 여행지를 연결했어요</strong>
          </div>
        </div>
      </div>
    </section>

    <!-- 최근 게시글 -->
    <section class="home-section">
      <div class="container">
        <div class="section-header">
          <div>
            <p class="section-eyebrow">RECENT COURSES</p>
            <h2 class="section-title">최근 여행 코스</h2>
            <p class="section-description">다른 사용자가 새롭게 공유한 여행 코스를 확인해보세요.</p>
          </div>

          <RouterLink to="/posts" class="section-more-link">
            전체 보기

            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </RouterLink>
        </div>

        <AppError v-if="postsError" compact :message="postsError" @retry="loadRecentPosts" />

        <div v-else-if="recentPosts.length > 0" class="post-grid">
          <RouterLink
            v-for="post in recentPosts"
            :key="getPostId(post)"
            :to="`/posts/${getPostId(post)}`"
            class="post-card"
          >
            <div class="post-card__header">
              <span class="post-category">
                {{ getPostCategory(post) }}
              </span>

              <span class="post-date">
                {{ formatDate(post.created_at ?? post.createdAt) }}
              </span>
            </div>

            <h3 class="post-title">
              {{ post.title }}
            </h3>

            <p class="post-summary text-clamp-2">
              {{ getPostSummary(post) }}
            </p>

            <div class="post-footer">
              <div class="post-author">
                <span class="post-author__avatar">
                  {{ getPostAuthor(post).charAt(0) }}
                </span>

                <span>{{ getPostAuthor(post) }}</span>
              </div>

              <span class="post-detail-link">
                코스 보기

                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m9 18 6-6-6-6" />
                </svg>
              </span>
            </div>
          </RouterLink>
        </div>

        <div v-else class="empty-state">
          <div class="empty-state__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M4 19.5V5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14.5" />
              <path d="M4 17h16M8 7h8M8 11h5" />
            </svg>
          </div>

          <strong>아직 공유된 여행 코스가 없습니다.</strong>

          <p>첫 번째 여행 코스를 작성하고 다른 사용자와 공유해보세요.</p>

          <RouterLink to="/posts/create"> 코스 작성하기 </RouterLink>
        </div>
      </div>
    </section>

    <!-- 현재 위치와 가까운 장소가 포함된 게시글 -->
    <section class="home-section location-section">
      <div class="container">
        <div class="section-header">
          <div>
            <p class="section-eyebrow">RECOMMENDED COURSES NEAR YOU</p>
            <h2 class="section-title">현재 위치 추천 코스</h2>
            <p class="section-description">반경 5km 안의 장소가 포함된 게시글을 가까운 순서로 최대 3개 보여드려요.</p>
          </div>

          <RouterLink to="/posts" class="section-more-link">
            게시글 전체 보기

            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m9 18 6-6-6-6" />
            </svg>
          </RouterLink>
        </div>

        <section v-if="locationPermissionDenied" class="location-permission" role="status">
          <strong>주변 추천을 보려면 위치 권한이 필요합니다.</strong>
          <ol>
            <li>주소창 왼쪽의 사이트 설정에서 이 사이트의 위치 권한을 허용해 주세요.</li>
            <li>계속 차단되면 Windows 설정 → 개인정보 및 보안 → 위치에서 위치 서비스를 확인해 주세요.</li>
          </ol>
          <p>설정을 변경한 뒤 아래 버튼을 눌러 주세요.</p>
          <button type="button" class="location-permission__retry" @click="loadRecommendedPosts">
            위치 허용 후 다시 확인
          </button>
        </section>

        <AppError
          v-else-if="recommendationsError"
          compact
          title="현재 위치 추천 코스를 불러오지 못했습니다."
          :message="recommendationsError"
          retry-text="현재 위치 다시 확인"
          @retry="loadRecommendedPosts"
        />

        <p v-else-if="isLoadingRecommendations" class="location-loading" role="status">
          현재 위치를 확인하고 추천 코스를 불러오는 중입니다.
        </p>

        <div v-else-if="recommendedPosts.length > 0" class="post-grid nearby-post-grid">
          <RouterLink
            v-for="post in recommendedPosts"
            :key="getPostId(post)"
            :to="`/posts/${getPostId(post)}`"
            class="post-card nearby-post-card"
          >
            <div class="post-card__header">
              <span class="post-category">{{ getPostCategory(post) }}</span>
              <span class="post-date">{{ formatDate(post.createdAt) }}</span>
            </div>

            <h3 class="post-title">{{ post.title }}</h3>
            <p class="post-summary text-clamp-2">{{ getPostSummary(post) }}</p>

            <p class="nearby-place">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
                <circle cx="12" cy="10" r="2.5" />
              </svg>
              <span class="text-ellipsis">{{ post.nearestLocation.name }}</span>
              <strong>{{ formatDistance(post.distanceKm) }}</strong>
            </p>

            <div class="post-footer">
              <div class="post-author">
                <span class="post-author__avatar">{{ getPostAuthor(post).charAt(0) }}</span>
                <span>{{ getPostAuthor(post) }}</span>
              </div>
              <span class="post-detail-link">게시글 보기</span>
            </div>
          </RouterLink>
        </div>

        <div v-else class="map-preview">
          <div class="map-preview__content">
            <span class="map-preview__badge"> 여행지 지도 </span>

            <h3>주변 장소가 포함된 게시글이 아직 없습니다.</h3>

            <p>반경 5km 안의 여행지를 코스에 담아 첫 게시글을 공유해보세요.</p>

            <RouterLink to="/map">
              지도 열기

              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m9 18 6-6-6-6" />
              </svg>
            </RouterLink>
          </div>

          <div class="map-preview__graphic" aria-hidden="true">
            <span class="preview-pin preview-pin--one"></span>
            <span class="preview-pin preview-pin--two"></span>
            <span class="preview-pin preview-pin--three"></span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-view {
  min-height: calc(100dvh - var(--header-height));
  overflow: hidden;
}

/* Hero */

.hero-section {
  padding: var(--spacing-10) 0 var(--spacing-12);
  background:
    radial-gradient(circle at 85% 20%, rgb(91 141 239 / 12%), transparent 28%),
    linear-gradient(135deg, var(--color-primary-light), var(--color-background) 62%);
}

.hero-container {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 0.8fr);
  align-items: center;
  gap: var(--spacing-12);
}

.hero-content {
  max-width: 650px;
}

.region-badge {
  display: inline-flex;
  align-items: center;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid rgb(47 125 91 / 18%);
  border-radius: var(--radius-full);
  background-color: rgb(255 255 255 / 70%);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  gap: var(--spacing-2);
}

.region-badge svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: var(--color-primary);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.region-badge strong {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.hero-title {
  margin: var(--spacing-5) 0 var(--spacing-4);
  color: var(--color-text-primary);
  font-size: clamp(2.25rem, 5vw, 3.75rem);
  line-height: 1.17;
  letter-spacing: -0.04em;
}

.hero-description {
  max-width: 590px;
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-lg);
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  margin-top: var(--spacing-8);
  gap: var(--spacing-3);
}

.hero-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 50px;
  padding: 0 var(--spacing-5);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
  gap: var(--spacing-2);
}

.hero-button svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.hero-button--primary {
  background-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
  color: var(--color-text-inverse);
}

.hero-button--primary:hover {
  background-color: var(--color-primary-hover);
  color: var(--color-text-inverse);
  transform: translateY(-1px);
}

.hero-button--secondary {
  border-color: var(--color-border);
  background-color: var(--color-surface);
  color: var(--color-text-primary);
}

.hero-button--secondary:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-1px);
}

/* Hero map decoration */

.hero-map {
  position: relative;
  min-height: 360px;
  overflow: hidden;
  border: 8px solid rgb(255 255 255 / 75%);
  border-radius: var(--radius-xl);
  background-color: #e8eee9;
  box-shadow: var(--shadow-lg);
}

.map-grid {
  position: absolute;
  opacity: 0.35;
  background-image:
    linear-gradient(var(--color-border) 1px, transparent 1px),
    linear-gradient(90deg, var(--color-border) 1px, transparent 1px);
  background-size: 42px 42px;
  inset: 0;
}

.map-road {
  position: absolute;
  height: 22px;
  border: 5px solid var(--color-surface);
  border-radius: var(--radius-full);
  background-color: var(--color-secondary-light);
  box-shadow: var(--shadow-sm);
}

.map-road--first {
  top: 120px;
  right: -40px;
  width: 390px;
  transform: rotate(-25deg);
}

.map-road--second {
  bottom: 95px;
  left: -60px;
  width: 360px;
  transform: rotate(18deg);
}

.map-pin {
  position: absolute;
  display: grid;
  width: 42px;
  height: 42px;
  border: 5px solid var(--color-surface);
  border-radius: 50% 50% 50% 0;
  background-color: var(--color-primary);
  box-shadow: var(--shadow-md);
  transform: rotate(-45deg);
  place-items: center;
}

.map-pin span {
  width: 9px;
  height: 9px;
  border-radius: var(--radius-full);
  background-color: var(--color-surface);
}

.map-pin--first {
  top: 56px;
  left: 28%;
}

.map-pin--second {
  top: 44%;
  right: 20%;
  background-color: var(--color-secondary);
}

.map-pin--third {
  bottom: 64px;
  left: 35%;
  background-color: var(--color-accent);
}

.map-course {
  position: absolute;
  right: var(--spacing-5);
  bottom: var(--spacing-5);
  display: flex;
  padding: var(--spacing-4);
  border: 1px solid rgb(255 255 255 / 70%);
  border-radius: var(--radius-lg);
  background-color: rgb(255 255 255 / 92%);
  box-shadow: var(--shadow-md);
  flex-direction: column;
  gap: var(--spacing-1);
}

.map-course span {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.map-course strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

/* Sections */

.home-section {
  padding: var(--spacing-16) 0;
}

.location-section {
  background-color: var(--color-surface-muted);
}

.section-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--spacing-8);
  gap: var(--spacing-5);
}

.section-eyebrow {
  margin: 0 0 var(--spacing-2);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.12em;
}

.section-title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  letter-spacing: -0.02em;
}

.section-description {
  margin: var(--spacing-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.location-loading {
  margin: 0 0 var(--spacing-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.location-permission {
  padding: var(--spacing-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background-color: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.8;
}

.location-permission strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.location-permission ol {
  padding-left: var(--spacing-5);
  margin-block: var(--spacing-3);
}

.location-permission p {
  margin-bottom: var(--spacing-3);
}

.location-permission__retry {
  padding: var(--spacing-2) var(--spacing-4);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  background-color: var(--color-primary);
  color: var(--color-text-inverse);
  font: inherit;
  cursor: pointer;
}

.section-more-link {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
  gap: var(--spacing-1);
}

.section-more-link:hover {
  color: var(--color-primary);
}

.section-more-link svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

/* Post cards */

.post-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-5);
}

.post-card {
  display: flex;
  min-height: 260px;
  padding: var(--spacing-6);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-card);
  color: inherit;
  flex-direction: column;
  text-decoration: none;
}

.post-card:hover {
  border-color: rgb(47 125 91 / 28%);
  box-shadow: var(--shadow-md);
  color: inherit;
  transform: translateY(-4px);
}

.post-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.post-category {
  padding: var(--spacing-1) var(--spacing-2);
  border-radius: var(--radius-full);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.post-date {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
}

.post-title {
  margin: var(--spacing-5) 0 var(--spacing-3);
  color: var(--color-text-primary);
  font-size: var(--font-size-xl);
  line-height: 1.4;
}

.post-summary {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.post-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: var(--spacing-6);
  gap: var(--spacing-3);
}

.post-author {
  display: flex;
  min-width: 0;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  gap: var(--spacing-2);
}

.post-author__avatar {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  background-color: var(--color-secondary-light);
  color: var(--color-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  place-items: center;
}

.post-detail-link {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.post-detail-link svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

/* Nearby place in a post */

.nearby-place {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  margin: var(--spacing-4) 0 0;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
}

.nearby-place svg {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentcolor;
  stroke-width: 1.8;
}

.nearby-place strong {
  flex: 0 0 auto;
  margin-left: auto;
}

/* Empty states */

.empty-state {
  display: flex;
  align-items: center;
  padding: var(--spacing-12) var(--spacing-6);
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-lg);
  background-color: var(--color-surface);
  text-align: center;
  flex-direction: column;
}

.empty-state__icon {
  display: grid;
  width: 56px;
  height: 56px;
  margin-bottom: var(--spacing-4);
  border-radius: var(--radius-full);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  place-items: center;
}

.empty-state__icon svg {
  width: 28px;
  height: 28px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.empty-state strong {
  color: var(--color-text-primary);
}

.empty-state p {
  margin: var(--spacing-2) 0 var(--spacing-5);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.empty-state a {
  padding: var(--spacing-2) var(--spacing-4);
  border-radius: var(--radius-md);
  background-color: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
}

.empty-state a:hover {
  background-color: var(--color-primary-hover);
  color: var(--color-text-inverse);
}

/* Map fallback preview */

.map-preview {
  display: grid;
  min-height: 300px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  background-color: var(--color-surface);
  box-shadow: var(--shadow-card);
  grid-template-columns: minmax(0, 0.8fr) minmax(360px, 1.2fr);
}

.map-preview__content {
  display: flex;
  justify-content: center;
  padding: var(--spacing-8);
  flex-direction: column;
}

.map-preview__badge {
  align-self: flex-start;
  padding: var(--spacing-1) var(--spacing-3);
  border-radius: var(--radius-full);
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.map-preview__content h3 {
  margin: var(--spacing-4) 0 var(--spacing-3);
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
}

.map-preview__content p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.map-preview__content a {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  margin-top: var(--spacing-5);
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
}

.map-preview__content a svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.map-preview__graphic {
  position: relative;
  min-height: 300px;
  background:
    linear-gradient(
      40deg,
      transparent 42%,
      rgb(255 255 255 / 80%) 42%,
      rgb(255 255 255 / 80%) 47%,
      transparent 47%
    ),
    linear-gradient(
      -35deg,
      transparent 51%,
      rgb(255 255 255 / 85%) 51%,
      rgb(255 255 255 / 85%) 56%,
      transparent 56%
    ),
    var(--color-primary-light);
}

.preview-pin {
  position: absolute;
  width: 30px;
  height: 30px;
  border: 5px solid var(--color-surface);
  border-radius: var(--radius-full);
  background-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

.preview-pin--one {
  top: 22%;
  left: 24%;
}

.preview-pin--two {
  top: 46%;
  right: 24%;
  background-color: var(--color-secondary);
}

.preview-pin--three {
  bottom: 18%;
  left: 42%;
  background-color: var(--color-accent);
}

/* Responsive */

@media (max-width: 1023px) {
  .hero-container {
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: var(--spacing-8);
  }

  .hero-map {
    min-height: 320px;
  }

  .post-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .post-card:last-child:not(.nearby-post-card) {
    display: none;
  }
}

@media (max-width: 767px) {
  .hero-section {
    padding: var(--spacing-8) 0 var(--spacing-10);
  }

  .hero-container {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-title {
    font-size: 2.35rem;
  }

  .hero-description {
    font-size: var(--font-size-md);
  }

  .hero-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hero-button {
    width: 100%;
  }

  .hero-map {
    min-height: 250px;
  }

  .home-section {
    padding: var(--spacing-12) 0;
  }

  .section-header {
    align-items: flex-start;
    margin-bottom: var(--spacing-6);
    flex-direction: column;
  }

  .section-more-link {
    align-self: flex-end;
  }

  .post-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .post-card:last-child:not(.nearby-post-card) {
    display: flex;
  }

  .post-card {
    min-height: 235px;
    padding: var(--spacing-5);
  }

  .map-preview {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-preview__content {
    padding: var(--spacing-6);
  }

  .map-preview__graphic {
    min-height: 220px;
  }
}

@media (max-width: 420px) {
  .hero-title {
    font-size: 2rem;
  }

  .post-card__header,
  .post-footer {
    align-items: flex-start;
  }

  .post-footer {
    flex-direction: column;
  }
}
</style>
