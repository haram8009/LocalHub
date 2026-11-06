<script setup>
import { computed, ref, watch } from 'vue'

import defaultImage from '@/assets/images/default-location.svg'

const props = defineProps({
  src: {
    type: String,
    default: '',
  },
  // src를 불러오지 못했을 때 대신 시도할 주소입니다.
  fallbackSrc: {
    type: String,
    default: '',
  },
  alt: {
    type: String,
    default: '',
  },
})

// 앞에서부터 차례로 시도하고, 모두 실패하거나 주소가 없으면 기본 이미지를 보여줍니다.
const sources = computed(() => [...new Set([props.src, props.fallbackSrc].filter(Boolean))])
const failedCount = ref(0)

const isDefault = computed(() => failedCount.value >= sources.value.length)
const currentSrc = computed(() =>
  isDefault.value ? defaultImage : sources.value[failedCount.value],
)

watch(
  () => sources.value.join('\n'),
  () => {
    failedCount.value = 0
  },
)

function handleError() {
  if (!isDefault.value) {
    failedCount.value += 1
  }
}
</script>

<template>
  <img :src="currentSrc" :alt="isDefault ? '' : alt" @error="handleError" />
</template>
