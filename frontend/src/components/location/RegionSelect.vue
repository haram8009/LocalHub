<script setup>
import { ref, useId, watch } from 'vue'

import { getSidoCodes, getSigunguCodes } from '@/services/tourCodes'

const props = defineProps({
  sido: {
    type: String,
    default: '',
  },
  sigungu: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['change'])

const sidoId = useId()
const sigunguId = useId()

const sidoOptions = ref([])
const sigunguOptions = ref([])

let sigunguRequestId = 0

async function loadSidoOptions() {
  try {
    sidoOptions.value = await getSidoCodes()
  } catch (error) {
    console.error('시도 목록 조회 실패:', error)
  }
}

async function loadSigunguOptions(sido) {
  const requestId = ++sigunguRequestId
  sigunguOptions.value = []

  if (!sido) return

  try {
    const options = await getSigunguCodes(sido)

    if (requestId === sigunguRequestId) {
      sigunguOptions.value = options
    }
  } catch (error) {
    console.error('구군 목록 조회 실패:', error)
  }
}

function changeSido(sido) {
  emit('change', { sido, sigungu: '' })
}

function changeSigungu(sigungu) {
  emit('change', { sido: props.sido, sigungu })
}

loadSidoOptions()

watch(() => props.sido, loadSigunguOptions, { immediate: true })
</script>

<template>
  <div class="region-select" role="group" aria-label="지역 선택">
    <label class="sr-only" :for="sidoId"> 시도 선택 </label>

    <select
      :id="sidoId"
      class="region-select__control"
      :disabled="disabled"
      @change="changeSido($event.target.value)"
    >
      <option value="" :selected="!sido">전국</option>

      <option
        v-for="option in sidoOptions"
        :key="option.code"
        :value="option.code"
        :selected="option.code === sido"
      >
        {{ option.name }}
      </option>
    </select>

    <label class="sr-only" :for="sigunguId"> 구군 선택 </label>

    <select
      :id="sigunguId"
      class="region-select__control"
      :disabled="disabled || sigunguOptions.length === 0"
      @change="changeSigungu($event.target.value)"
    >
      <option value="" :selected="!sigungu">구·군 전체</option>

      <option
        v-for="option in sigunguOptions"
        :key="option.code"
        :value="option.code"
        :selected="option.code === sigungu"
      >
        {{ option.name }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.region-select {
  display: flex;
  gap: var(--spacing-2);
  min-width: 0;
}

.region-select__control {
  min-width: 0;
  max-width: 180px;
  height: 44px;
  padding: 0 var(--spacing-3);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  background-color: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  cursor: pointer;
}

.region-select__control:hover:not(:disabled) {
  border-color: var(--color-primary);
}

.region-select__control:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 3px rgb(47 125 91 / 12%);
}

.region-select__control:disabled {
  color: var(--color-text-muted);
  cursor: default;
}

@media (max-width: 640px) {
  .region-select__control {
    flex: 1;
    max-width: none;
  }
}
</style>
