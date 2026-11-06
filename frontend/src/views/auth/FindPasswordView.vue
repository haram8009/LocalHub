<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import AuthCard from '@/components/auth/AuthCard.vue'
import { useAuthStore } from '@/stores/auth'
import { validateEmail } from '@/utils/validators'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ userId: '', email: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)

async function submit() {
  if (isSubmitting.value) return

  errorMessage.value = form.userId.trim() ? validateEmail(form.email) : '아이디를 입력해 주세요.'
  if (errorMessage.value) return

  isSubmitting.value = true

  try {
    await authStore.verifyResetTarget(form)
    await router.push({ name: 'reset-password' })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '회원 정보를 확인하지 못했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthCard
    eyebrow="FIND PASSWORD"
    title="비밀번호 찾기"
    description="아이디와 가입한 이메일을 입력해 재설정 코드를 발급받으세요."
  >
    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="auth-field">
        <span>아이디 <span class="auth-field__required">*</span></span>
        <input
          v-model="form.userId"
          class="auth-control"
          type="text"
          autocomplete="username"
          maxlength="20"
          autofocus
        />
      </label>

      <label class="auth-field">
        <span>이메일 <span class="auth-field__required">*</span></span>
        <input
          v-model="form.email"
          class="auth-control"
          type="email"
          autocomplete="email"
          maxlength="100"
        />
        <span class="auth-hint">로컬 개발 모드에서는 다음 화면에 인증 코드를 표시합니다.</span>
      </label>

      <p v-if="errorMessage" class="auth-alert" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        class="auth-button auth-button--primary auth-button--block"
        :disabled="isSubmitting"
      >
        본인 확인
      </button>
    </form>

    <template #footer>
      <div class="auth-links">
        <RouterLink :to="{ name: 'login' }">로그인</RouterLink>
        <RouterLink :to="{ name: 'find-id' }">아이디 찾기</RouterLink>
        <RouterLink :to="{ name: 'signup' }">회원가입</RouterLink>
      </div>
    </template>
  </AuthCard>
</template>
