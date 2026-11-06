<script setup>
import { computed } from 'vue'

import { ALL_CATEGORY } from '@/services/tourApi'
import { getClassificationCodes, normalizeClassification } from '@/services/tourCodes'

const props = defineProps({
  category: {
    type: String,
    default: ALL_CATEGORY,
  },
  classification: {
    type: Object,
    default: () => ({ lclsSystm1: '', lclsSystm2: '', lclsSystm3: '' }),
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  showLevel1: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['change'])

// 카테고리에 없는 코드가 넘어와도 선택된 것으로 다루지 않습니다.
const selected = computed(() => normalizeClassification(props.category, props.classification))

const level1Options = computed(() => getClassificationCodes({ category: props.category }))

// 대분류가 하나뿐인 카테고리(쇼핑, 음식점 등)는 대분류 단계를 건너뛰고 바로 중분류를 보여줍니다.
const hasSingleLevel1 = computed(() => level1Options.value.length === 1)
const activeLevel1 = computed(
  () => selected.value.lclsSystm1 || (hasSingleLevel1.value ? level1Options.value[0].code : ''),
)

const level2Options = computed(() =>
  activeLevel1.value
    ? getClassificationCodes({ category: props.category, lclsSystm1: activeLevel1.value })
    : [],
)
const level3Options = computed(() =>
  selected.value.lclsSystm2
    ? getClassificationCodes({ category: props.category, ...selected.value })
    : [],
)

const levels = computed(() =>
  [
    {
      key: 'lclsSystm1',
      label: '대분류',
      options: props.showLevel1 && !hasSingleLevel1.value ? level1Options.value : [],
      selected: selected.value.lclsSystm1,
    },
    {
      key: 'lclsSystm2',
      label: '중분류',
      options: level2Options.value,
      selected: selected.value.lclsSystm2,
    },
    {
      key: 'lclsSystm3',
      label: '소분류',
      // 소분류가 하나뿐이면(호텔 > 호텔) 중분류와 같은 조건이라 보여주지 않습니다.
      options: level3Options.value.length > 1 ? level3Options.value : [],
      selected: selected.value.lclsSystm3,
    },
  ].filter((level) => level.options.length > 0),
)

function select(key, code) {
  if (key === 'lclsSystm1') {
    emit('change', { lclsSystm1: code, lclsSystm2: '', lclsSystm3: '' })
    return
  }

  if (key === 'lclsSystm2') {
    // 중분류를 해제하면 자동으로 채운 대분류도 함께 비웁니다.
    const lclsSystm1 = code || !hasSingleLevel1.value ? activeLevel1.value : ''

    emit('change', { lclsSystm1, lclsSystm2: code, lclsSystm3: '' })
    return
  }

  emit('change', { ...selected.value, lclsSystm3: code })
}
</script>

<template>
  <div v-if="levels.length > 0" class="classification-filter">
    <div
      v-for="level in levels"
      :key="level.key"
      class="classification-filter__row"
      role="group"
      :aria-label="`분류체계 ${level.label}`"
    >
      <span class="classification-filter__label">{{ level.label }}</span>

      <div class="classification-filter__options">
        <button
          class="classification-filter__option"
          :class="{ 'classification-filter__option--active': !level.selected }"
          type="button"
          :aria-pressed="!level.selected"
          :disabled="disabled"
          @click="select(level.key, '')"
        >
          전체
        </button>

        <button
          v-for="option in level.options"
          :key="option.code"
          class="classification-filter__option"
          :class="{ 'classification-filter__option--active': level.selected === option.code }"
          type="button"
          :aria-pressed="level.selected === option.code"
          :disabled="disabled"
          @click="select(level.key, option.code)"
        >
          {{ option.name }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.classification-filter {
  display: grid;
  gap: var(--spacing-3);
  padding-top: var(--spacing-4);
  border-top: 1px solid var(--color-border-light);
}

.classification-filter__row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: start;
  gap: var(--spacing-3);
}

.classification-filter__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  line-height: 32px;
}

.classification-filter__options {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.classification-filter__option {
  min-height: 32px;
  padding: 0 var(--spacing-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  cursor: pointer;
}

.classification-filter__option:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.classification-filter__option--active {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  background-color: var(--color-primary-light);
  border-color: var(--color-primary);
}

@media (max-width: 640px) {
  .classification-filter__options {
    flex-wrap: nowrap;
    padding-bottom: var(--spacing-1);
    overflow-x: auto;
  }

  .classification-filter__option {
    flex: 0 0 auto;
  }
}
</style>
