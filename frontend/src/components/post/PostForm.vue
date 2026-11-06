<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import LocationFilterBar from '@/components/location/LocationFilterBar.vue'
import CourseKakaoMap from '@/components/map/CourseKakaoMap.vue'
import { createLocationFilters, toLocationQuery } from '@/services/locationFilters'
import { DEFAULT_PAGE_SIZE, getLocationPage } from '@/services/locationService'

const props = defineProps({
  mode: { type: String, default: 'create', validator: (value) => ['create', 'edit'].includes(value) },
  initialPost: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['submit', 'cancel'])

const form = reactive({ category: 'COURSE', title: '', content: '' })
const locationFilters = ref(createLocationFilters())
const selectedLocations = ref([])
const availableLocations = ref([])
const locationPage = ref(1)
const locationTotalCount = ref(0)
const locationTotalPages = ref(0)
const locationsLoading = ref(false)
const locationsError = ref('')

let searchAbortController = null

const isEdit = computed(() => props.mode === 'edit')
const pageTitle = computed(() => (isEdit.value ? '여행 코스 수정하기' : '여행 코스 공유하기'))
const hasMoreLocations = computed(() => locationPage.value < locationTotalPages.value)

watch(() => props.initialPost, (post) => {
  form.category = post?.category ?? 'COURSE'
  form.title = post?.title ?? ''
  form.content = post?.content ?? ''
  selectedLocations.value = Array.isArray(post?.locations) ? post.locations.map((item) => ({ ...item })) : []
}, { immediate: true, deep: true })

// 지역·카테고리·분류체계·검색어 조건으로 여행지를 조회합니다. 2페이지부터는 목록 뒤에 이어 붙입니다.
async function loadLocations(page = 1) {
  searchAbortController?.abort()
  const controller = new AbortController()
  searchAbortController = controller
  locationsLoading.value = true
  locationsError.value = ''

  try {
    const query = toLocationQuery(locationFilters.value)
    const result = await getLocationPage({
      ...query,
      mode: query.keyword ? 'keyword' : 'area',
      page,
      size: DEFAULT_PAGE_SIZE,
      signal: controller.signal,
    })
    const loadedIds = new Set(availableLocations.value.map((item) => item.contentId))

    availableLocations.value =
      page === 1
        ? result.items
        : [...availableLocations.value, ...result.items.filter((item) => !loadedIds.has(item.contentId))]
    locationPage.value = page
    locationTotalCount.value = result.totalCount
    locationTotalPages.value = result.totalPages
  } catch (error) {
    if (error?.name !== 'AbortError') {
      if (page === 1) {
        availableLocations.value = []
        locationTotalCount.value = 0
        locationTotalPages.value = 0
      }
      locationsError.value = error instanceof Error ? error.message : '여행지를 검색하지 못했습니다.'
    }
  } finally {
    if (searchAbortController === controller) {
      locationsLoading.value = false
    }
  }
}

function applyLocationFilters(filters) {
  locationFilters.value = filters
  loadLocations()
}
function resetLocationFilters() { applyLocationFilters(createLocationFilters()) }

function isSelected(id) { return selectedLocations.value.some((item) => String(item.contentId) === String(id)) }
function addLocation(location) { if (!isSelected(location.contentId)) selectedLocations.value.push({ ...location }) }
function removeLocation(index) { selectedLocations.value.splice(index, 1) }
function move(index, offset) {
  const target = index + offset
  if (target < 0 || target >= selectedLocations.value.length) return
  const next = [...selectedLocations.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  selectedLocations.value = next
}
function validate() {
  if (!form.title.trim()) return window.alert('제목을 입력해 주세요.'), false
  if (!form.content.trim()) return window.alert('내용을 입력해 주세요.'), false
  return true
}
function submit() {
  if (props.loading || !validate()) return
  emit('submit', {
    category: form.category,
    title: form.title.trim(),
    content: form.content.trim(),
    locations: selectedLocations.value.map((item, index) => ({
      ...item,
      visitOrder: index + 1,
    })),
  })
}

loadLocations()

onBeforeUnmount(() => {
  searchAbortController?.abort()
})
</script>

<template>
  <main class="post-form-view">
    <div class="container">
      <header class="page-header">
        <p class="eyebrow">LOCAL COURSE</p>
        <h1>{{ pageTitle }}</h1>
        <p>{{ isEdit ? '게시글 내용과 코스를 수정하세요.' : '방문 순서에 맞게 여행지를 선택해 코스를 공유하세요.' }}</p>
      </header>

      <form class="post-form" @submit.prevent="submit">
        <section class="panel">
          <header class="panel__header"><h2>{{ isEdit ? '게시글 수정' : '게시글 작성' }}</h2></header>
          <div class="panel__content">
            <label class="field">카테고리
              <select v-model="form.category" class="control">
                <option value="COURSE">여행 코스</option><option value="RECOMMENDATION">여행지 추천</option>
                <option value="REVIEW">여행 후기</option><option value="TIP">여행 팁</option>
              </select>
            </label>
            <label class="field">제목 <span>*</span>
              <input v-model="form.title" class="control" maxlength="100" required placeholder="제목을 입력하세요." />
              <small>{{ form.title.length }}/100</small>
            </label>
            <label class="field">내용 <span>*</span>
              <textarea v-model="form.content" class="control textarea" maxlength="5000" required placeholder="코스 특징과 이동 방법을 작성하세요." />
              <small>{{ form.content.length }}/5000</small>
            </label>
          </div>
        </section>

        <section class="panel">
          <header class="panel__header panel__header--between"><div><h2>여행지 첨부</h2><p>선택 순서가 실제 코스 순서가 됩니다.</p></div><strong>{{ selectedLocations.length }}개 선택</strong></header>
          <div class="panel__content">
            <LocationFilterBar :filters="locationFilters" @change="applyLocationFilters" @reset="resetLocationFilters" />

            <div class="location-layout">
              <section>
                <h3>검색 결과 <small>{{ locationTotalCount.toLocaleString('ko-KR') }}개</small></h3>
                <p v-if="locationsLoading" class="location-state">여행지를 검색하고 있습니다.</p>
                <p v-else-if="locationsError" class="location-state location-state--error">{{ locationsError }}</p>
                <p v-else-if="availableLocations.length === 0" class="location-state">조건에 맞는 여행지가 없습니다.</p>
                <ul class="result-list">
                  <li v-for="location in availableLocations" :key="location.contentId">
                    <div><strong>{{ location.name }}</strong><span>{{ location.categoryLabel }} · {{ location.address }}</span></div>
                    <button type="button" :disabled="isSelected(location.contentId)" @click="addLocation(location)">{{ isSelected(location.contentId) ? '완료' : '추가' }}</button>
                  </li>
                </ul>
                <button v-if="hasMoreLocations" type="button" class="more-button" :disabled="locationsLoading" @click="loadLocations(locationPage + 1)">더 보기</button>
              </section>

              <section>
                <h3>선택한 여행지</h3>
                <ol class="selected-list">
                  <li v-for="(location, index) in selectedLocations" :key="location.contentId">
                    <b>{{ index + 1 }}</b><div><strong>{{ location.name }}</strong><span>{{ location.address }}</span></div>
                    <div class="row-actions"><button type="button" :disabled="index === 0" @click="move(index, -1)">↑</button><button type="button" :disabled="index === selectedLocations.length - 1" @click="move(index, 1)">↓</button><button type="button" @click="removeLocation(index)">×</button></div>
                  </li>
                </ol>
              </section>
            </div>

            <section class="preview">
              <h3>코스 지도 미리보기</h3>
              <p v-if="selectedLocations.length === 0" class="preview__empty">
                여행지를 추가하면 지도에 코스가 표시됩니다.
              </p>
              <CourseKakaoMap :locations="selectedLocations" height="440px" />
            </section>
          </div>
        </section>

        <footer class="actions"><button type="button" class="secondary" :disabled="loading" @click="emit('cancel')">취소</button><button type="submit" class="primary" :disabled="loading">{{ loading ? '저장 중...' : isEdit ? '변경사항 저장' : '게시글 등록' }}</button></footer>
      </form>
    </div>
  </main>
</template>

<style scoped>
.location-state {
  margin: 0 0 var(--spacing-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.location-state--error {
  color: var(--color-error);
}

.preview__empty {
  margin: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
}

.more-button {
  width: 100%;
  min-height: 40px;
  margin-top: var(--spacing-2);
  color: var(--color-text-secondary);
  font-weight: 600;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.more-button:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.post-form-view{min-height:calc(100vh - var(--header-height));padding:var(--spacing-8) 0 var(--spacing-16)}.page-header{margin-bottom:var(--spacing-6)}.page-header h1{margin:0;font-size:var(--font-size-3xl)}.page-header p{color:var(--color-text-secondary)}.eyebrow{margin:0!important;color:var(--color-primary)!important;font-size:var(--font-size-xs);font-weight:700;letter-spacing:.12em}.post-form{overflow:hidden;background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-lg);box-shadow:var(--shadow-card)}.panel+.panel{border-top:1px solid var(--color-border)}.panel__header{padding:var(--spacing-5);background:var(--color-surface-muted);border-bottom:1px solid var(--color-border-light)}.panel__header h2,.panel__header p{margin:0}.panel__header p{color:var(--color-text-secondary);font-size:var(--font-size-sm)}.panel__header--between{display:flex;justify-content:space-between;gap:var(--spacing-4)}.panel__content{display:grid;gap:var(--spacing-6);padding:var(--spacing-5)}.field{display:grid;gap:var(--spacing-2);font-size:var(--font-size-sm);font-weight:600}.field span{color:var(--color-error)}.field small{justify-self:end;color:var(--color-text-muted);font-weight:400}.control{min-height:46px;padding:0 var(--spacing-3);color:var(--color-text-primary);font:inherit;background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-md)}.control:focus{border-color:var(--color-primary);outline:none;box-shadow:0 0 0 3px rgb(47 125 91 / 12%)}.textarea{min-height:220px;padding-block:var(--spacing-3);resize:vertical}.location-layout{display:grid;grid-template-columns:1fr 1fr;gap:var(--spacing-5)}.location-layout h3,.preview h3{margin:0 0 var(--spacing-3)}.result-list,.selected-list{display:grid;gap:var(--spacing-2);margin:0;padding:0;list-style:none;max-height:360px;overflow:auto}.result-list li,.selected-list li{display:flex;align-items:center;gap:var(--spacing-3);padding:var(--spacing-3);border:1px solid var(--color-border-light);border-radius:var(--radius-md)}.result-list li>div,.selected-list li>div:not(.row-actions){display:grid;min-width:0;flex:1}.result-list span,.selected-list span{overflow:hidden;color:var(--color-text-secondary);font-size:var(--font-size-xs);white-space:nowrap;text-overflow:ellipsis}.result-list button,.row-actions button{min-width:36px;height:34px;border:1px solid var(--color-border);border-radius:var(--radius-sm);background:white;cursor:pointer}.selected-list b{display:grid;place-items:center;width:30px;height:30px;color:white;background:var(--color-primary);border-radius:50%}.row-actions{display:flex;gap:4px}.preview{display:grid;gap:var(--spacing-3)}.empty{display:grid;place-items:center;min-height:240px;color:var(--color-text-muted);background:var(--color-surface-muted);border:1px dashed var(--color-border-strong);border-radius:var(--radius-md)}.actions{display:flex;justify-content:flex-end;gap:var(--spacing-3);padding:var(--spacing-5);border-top:1px solid var(--color-border)}.actions button{min-width:120px;height:46px;border-radius:var(--radius-md);font-weight:600;cursor:pointer}.primary{color:white;background:var(--color-primary);border:1px solid var(--color-primary)}.secondary{color:var(--color-text-secondary);background:white;border:1px solid var(--color-border)}@media(max-width:800px){.location-layout{grid-template-columns:1fr}.actions button{flex:1}}
</style>
