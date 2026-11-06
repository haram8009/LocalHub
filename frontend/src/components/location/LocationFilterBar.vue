<script setup>
import { ref, useId, watch } from 'vue'

import ClassificationFilter from '@/components/location/ClassificationFilter.vue'
import RegionSelect from '@/components/location/RegionSelect.vue'
import { normalizeLocationFilters } from '@/services/locationFilters'
import { ALL_CATEGORY, LOCATION_CATEGORIES } from '@/services/tourApi'
import { USE_TOUR_DATABASE } from '@/services/tourSource'
import { getClassificationCodes } from '@/services/tourCodes'

const props = defineProps({
  // { keyword, category, region: { sido, sigungu }, classification: { lclsSystm1, lclsSystm2, lclsSystm3 } }
  filters: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['change', 'reset'])

const searchInputId = useId()
const keywordInput = ref(props.filters.keyword)
const MAIN_CATEGORIES = getClassificationCodes({ category: ALL_CATEGORY }).sort((left, right) =>
  categoryPriority(left.code) - categoryPriority(right.code),
)

function categoryPriority(code) {
  if (code === 'FD') return -1
  if (code === 'C01') return 1
  return 0
}

watch(
  () => props.filters.keyword,
  (newKeyword) => {
    keywordInput.value = newKeyword
  },
)

// 어떤 조건을 바꾸든 정리된 전체 조건을 한 번에 내보냅니다.
function change(changes) {
  emit('change', normalizeLocationFilters({ ...props.filters, ...changes }))
}

function submitSearch() {
  change({ keyword: keywordInput.value })
}

function handleSearchEnter(event) {
  // 게시글 작성 폼 안에서도 쓰이므로 Enter가 바깥 폼을 제출하지 않게 막습니다.
  event.preventDefault()

  // 한글 조합을 확정하는 Enter는 검색으로 치지 않습니다.
  if (event.isComposing) return

  submitSearch()
}

function selectMainCategory(code) {
  change({
    category: code === 'C01' ? '여행코스' : ALL_CATEGORY,
    classification: { lclsSystm1: code, lclsSystm2: '', lclsSystm3: '' },
  })
}

function resetFilters() {
  keywordInput.value = ''
  emit('reset')
}
</script>

<template>
  <section class="location-filter" aria-label="여행지 검색 및 지역·분류 필터">
    <div class="location-filter__search" role="search">
      <RegionSelect
        class="location-filter__region"
        :sido="filters.region.sido"
        :sigungu="filters.region.sigungu"
        :disabled="loading"
        @change="change({ region: $event })"
      />

      <label class="sr-only" :for="searchInputId"> 여행지 검색 </label>

      <input
        :id="searchInputId"
        v-model="keywordInput"
        class="location-filter__input"
        type="search"
        placeholder="여행지 이름 또는 지역을 검색하세요"
        autocomplete="off"
        :disabled="loading"
        @keydown.enter="handleSearchEnter"
      />

      <button
        class="location-filter__search-button"
        type="button"
        :disabled="loading"
        @click="submitSearch"
      >
        검색
      </button>

      <button
        class="location-filter__reset-button"
        type="button"
        :disabled="loading"
        @click="resetFilters"
      >
        필터 초기화
      </button>
    </div>

    <div v-if="USE_TOUR_DATABASE" class="location-filter__categories" role="group" aria-label="관광 유형">
      <button
        v-for="category in LOCATION_CATEGORIES"
        :key="category"
        class="location-filter__category"
        :class="{ 'location-filter__category--active': filters.category === category }"
        type="button"
        :aria-pressed="filters.category === category"
        :disabled="loading"
        @click="change({ category, classification: {} })"
      >{{ category }}</button>
    </div>
    <div v-else class="location-filter__categories" role="group" aria-label="여행지 대분류">
      <button
        v-for="mainCategory in MAIN_CATEGORIES"
        :key="mainCategory.code"
        class="location-filter__category"
        :class="{
          'location-filter__category--active': filters.classification.lclsSystm1 === mainCategory.code,
        }"
        type="button"
        :aria-pressed="filters.classification.lclsSystm1 === mainCategory.code"
        :disabled="loading"
        @click="selectMainCategory(mainCategory.code)"
      >
        {{ mainCategory.name }}
      </button>
    </div>

    <ClassificationFilter
      v-if="!USE_TOUR_DATABASE"
      :category="ALL_CATEGORY"
      :classification="filters.classification"
      :disabled="loading"
      :show-level1="false"
      @change="change({ classification: $event })"
    />
  </section>
</template>

<style scoped>
.location-filter {
  display: grid;
  gap: var(--spacing-4);
}

.location-filter__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: var(--spacing-2);
}

.location-filter__input {
  min-width: 0;
  height: 44px;
  padding: 0 var(--spacing-4);
  color: var(--color-text-primary);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
}

.location-filter__input:hover {
  border-color: var(--color-primary);
}

.location-filter__input:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px rgb(47 125 91 / 12%);
}

.location-filter__search-button,
.location-filter__reset-button {
  height: 44px;
  padding: 0 var(--spacing-5);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.location-filter__search-button {
  color: var(--color-text-inverse);
  background-color: var(--color-primary);
  border: 1px solid var(--color-primary);
}

.location-filter__search-button:hover:not(:disabled) {
  background-color: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.location-filter__reset-button {
  color: var(--color-text-secondary);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border-strong);
}

.location-filter__reset-button:hover:not(:disabled) {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
  border-color: var(--color-primary);
}

.location-filter__categories {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.location-filter__category {
  min-height: 38px;
  padding: 0 var(--spacing-4);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  background-color: var(--color-surface-muted);
  border: 1px solid transparent;
  border-radius: var(--radius-full);
  cursor: pointer;
}

.location-filter__category:hover:not(:disabled) {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.location-filter__category--active {
  color: var(--color-text-inverse);
  background-color: var(--color-primary);
}

.location-filter__category--active:hover:not(:disabled) {
  color: var(--color-text-inverse);
  background-color: var(--color-primary-hover);
}

@media (max-width: 640px) {
  .location-filter__search {
    grid-template-columns: 1fr 1fr;
  }

  .location-filter__region,
  .location-filter__input {
    grid-column: 1 / -1;
  }

  .location-filter__search-button,
  .location-filter__reset-button {
    width: 100%;
    padding-inline: var(--spacing-3);
  }

  .location-filter__categories {
    flex-wrap: nowrap;
    padding-bottom: var(--spacing-1);
    overflow-x: auto;
  }

  .location-filter__category {
    flex: 0 0 auto;
  }
}
</style>
