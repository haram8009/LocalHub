<script setup>
import { computed, ref, watch } from 'vue'

import AppImage from '@/components/common/AppImage.vue'
import { formatDistance } from '@/utils/geo'

const props = defineProps({
  location: {
    type: Object,
    required: true,
  },
  relatedPosts: {
    type: Array,
    default: () => [],
  },
  nearbyLocations: {
    type: Array,
    default: () => [],
  },
  postsLoading: {
    type: Boolean,
    default: false,
  },
  detail: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['back', 'create-course', 'select-nearby', 'open-post', 'open-all-posts'])

const description = computed(() => props.detail?.overview || props.location.description || '')
// 불러오지 못한 사진은 갤러리에서 뺍니다.
const brokenImages = ref([])
const galleryImages = computed(() =>
  (props.detail?.images ?? []).filter((image) => !brokenImages.value.includes(image)),
)
const descriptionElement = ref(null)
const descriptionExpanded = ref(false)
const descriptionClamped = ref(false)

// 소개 문구가 바뀌면 접힌 상태로 되돌리고, 실제로 잘렸을 때만 더보기 버튼을 노출합니다.
watch(
  description,
  () => {
    descriptionExpanded.value = false
  },
  { flush: 'sync' },
)

watch(
  [description, descriptionElement],
  () => {
    const element = descriptionElement.value

    descriptionClamped.value = element ? element.scrollHeight > element.clientHeight + 1 : false
  },
  { flush: 'post', immediate: true },
)
</script>

<template>
  <article class="location-detail">
    <header class="location-detail__navigation">
      <button class="location-detail__back-button" type="button" @click="emit('back')">
        <span aria-hidden="true">←</span>
        여행지 목록
      </button>
    </header>

    <div class="location-detail__image-wrapper">
      <!-- 썸네일은 150×100이라 상단 이미지에는 원본을 먼저 씁니다. -->
      <AppImage
        class="location-detail__image"
        :src="location.originalImageUrl"
        :fallback-src="location.thumbnailImageUrl"
        :alt="`${location.name} 이미지`"
      />
    </div>

    <section class="location-detail__summary">
      <h2 class="location-detail__title">
        {{ location.name }}
      </h2>

      <p class="location-detail__category">
        {{ location.category }}
        <template v-if="location.region"> · {{ location.region }} </template>
      </p>

      <p class="location-detail__address">
        {{ location.address || '주소 정보가 없습니다.' }}
      </p>

      <button
        class="location-detail__course-button"
        type="button"
        @click="emit('create-course', location)"
      >
        이 여행지로 코스 작성하기
      </button>
    </section>

    <section v-if="description" class="location-detail__section">
      <div class="location-detail__section-heading">
        <h3>소개</h3>
      </div>

      <p
        ref="descriptionElement"
        class="location-detail__description"
        :class="{ 'location-detail__description--expanded': descriptionExpanded }"
      >
        {{ description }}
      </p>

      <button
        v-if="descriptionClamped"
        class="location-detail__text-button location-detail__more-button"
        type="button"
        :aria-expanded="descriptionExpanded"
        @click="descriptionExpanded = !descriptionExpanded"
      >
        {{ descriptionExpanded ? '접기' : '더보기' }}
      </button>
    </section>

    <section class="location-detail__section">
      <div class="location-detail__section-heading">
        <h3>이용 정보</h3>
      </div>

      <p v-if="postsLoading" class="location-detail__empty">이용 정보를 불러오는 중입니다.</p>

      <dl
        v-else-if="detail && (detail.intro.length > 0 || detail.infos.length > 0)"
        class="location-detail__info-list"
      >
        <div v-for="item in detail.intro" :key="item.field" class="location-detail__info-row">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>

        <div
          v-for="(info, index) in detail.infos"
          :key="`info-${index}`"
          class="location-detail__info-row"
        >
          <dt>{{ info.name }}</dt>
          <dd>{{ info.text }}</dd>
        </div>
      </dl>

      <p v-else class="location-detail__empty">등록된 이용 정보가 없습니다.</p>
    </section>

    <section v-if="galleryImages.length" class="location-detail__section">
      <div class="location-detail__section-heading">
        <h3>사진</h3>
      </div>

      <ul class="location-detail__gallery">
        <li v-for="(image, index) in galleryImages" :key="image">
          <a :href="image" target="_blank" rel="noopener noreferrer">
            <img
              :src="image"
              :alt="`${location.name} 사진 ${index + 1}`"
              loading="lazy"
              @error="brokenImages.push(image)"
            />
          </a>
        </li>
      </ul>
    </section>

    <section class="location-detail__section">
      <div class="location-detail__section-heading">
        <h3>관련 게시글</h3>

        <button
          v-if="relatedPosts.length > 0"
          class="location-detail__text-button"
          type="button"
          @click="emit('open-all-posts', location)"
        >
          전체 보기
        </button>
      </div>

      <p v-if="postsLoading" class="location-detail__empty">게시글을 불러오는 중입니다.</p>

      <ul v-else-if="relatedPosts.length > 0" class="location-detail__list">
        <li v-for="post in relatedPosts" :key="post.id">
          <button
            class="location-detail__list-button"
            type="button"
            @click="emit('open-post', post)"
          >
            <span class="location-detail__bullet">·</span>
            <span class="location-detail__list-title">
              {{ post.title }}
            </span>
            <span aria-hidden="true">›</span>
          </button>
        </li>
      </ul>

      <p v-else class="location-detail__empty">이 여행지가 포함된 게시글이 없습니다.</p>
    </section>

    <section class="location-detail__section">
      <div class="location-detail__section-heading">
        <h3>근처 여행지</h3>
      </div>

      <ul v-if="nearbyLocations.length > 0" class="location-detail__list">
        <li v-for="nearbyLocation in nearbyLocations" :key="nearbyLocation.id">
          <button
            class="location-detail__list-button"
            type="button"
            @click="emit('select-nearby', nearbyLocation)"
          >
            <span class="location-detail__bullet">·</span>

            <span class="location-detail__list-title">
              {{ nearbyLocation.name }}
            </span>

            <span class="location-detail__distance">
              {{ formatDistance(nearbyLocation.distanceKm) }}
            </span>
          </button>
        </li>
      </ul>

      <p v-else class="location-detail__empty">근처 여행지가 없습니다.</p>

      <p class="location-detail__distance-note">※ 직선거리 기준</p>
    </section>
  </article>
</template>

<style scoped>
.location-detail {
  height: 100%;
  overflow-y: auto;
  background-color: var(--color-surface);
}

.location-detail__navigation {
  position: sticky;
  top: 0;
  z-index: var(--z-index-sticky);
  padding: var(--spacing-3) var(--spacing-5);
  background-color: rgb(255 255 255 / 94%);
  border-bottom: 1px solid var(--color-border-light);
  backdrop-filter: blur(8px);
}

.location-detail__back-button {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.location-detail__back-button:hover {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.location-detail__image-wrapper {
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background-color: var(--color-surface-muted);
}

.location-detail__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.location-detail__summary {
  padding: var(--spacing-6);
}

.location-detail__title {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-2xl);
  line-height: 1.35;
}

.location-detail__category {
  margin: var(--spacing-2) 0 0;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.location-detail__address {
  margin: var(--spacing-5) 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.location-detail__course-button {
  width: 100%;
  min-height: 46px;
  padding: var(--spacing-3) var(--spacing-4);
  color: var(--color-text-inverse);
  font-weight: var(--font-weight-semibold);
  background-color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.location-detail__course-button:hover {
  background-color: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.location-detail__section {
  padding: var(--spacing-6);
  border-top: 1px solid var(--color-border-light);
}

.location-detail__section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-3);
}

.location-detail__section-heading h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.location-detail__text-button {
  flex: 0 0 auto;
  padding: var(--spacing-1) var(--spacing-2);
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.location-detail__text-button:hover {
  background-color: var(--color-primary-light);
}

.location-detail__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.location-detail__list-button {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--spacing-2);
  width: 100%;
  min-height: 42px;
  padding: var(--spacing-2);
  color: var(--color-text-primary);
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.location-detail__list-button:hover {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.location-detail__bullet {
  color: var(--color-primary);
  font-size: var(--font-size-lg);
}

.location-detail__list-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.location-detail__distance {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
}

.location-detail__empty {
  margin: 0;
  padding: var(--spacing-4);
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  text-align: center;
  background-color: var(--color-surface-muted);
  border-radius: var(--radius-md);
}

.location-detail__description {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  white-space: pre-line;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
}

.location-detail__description--expanded {
  display: block;
  overflow: visible;
  -webkit-line-clamp: unset;
}

.location-detail__more-button {
  margin: var(--spacing-2) 0 0 calc(var(--spacing-2) * -1);
}

.location-detail__info-list {
  display: grid;
  gap: var(--spacing-3);
  margin: 0;
}

.location-detail__info-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--spacing-3);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.location-detail__info-row dt {
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-semibold);
}

.location-detail__info-row dd {
  margin: 0;
  color: var(--color-text-primary);
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.location-detail__gallery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.location-detail__gallery a {
  display: block;
  aspect-ratio: 1;
  overflow: hidden;
  background-color: var(--color-surface-muted);
  border-radius: var(--radius-sm);
}

.location-detail__gallery img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.location-detail__distance-note {
  margin: var(--spacing-3) 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  text-align: right;
}
</style>
