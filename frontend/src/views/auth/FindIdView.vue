<script setup>
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'

import AuthCard from '@/components/auth/AuthCard.vue'
import { findUserId } from '@/services/auth'
import { validateEmail, validateName } from '@/utils/validators'

const form = reactive({ name: '', email: '' })
const errorMessage = ref('')
const result = ref(null)
const isSubmitting = ref(false)

function formatDate(value) {
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' }).format(
    new Date(value),
  )
}

async function submit() {
  if (isSubmitting.value) return

  result.value = null
  errorMessage.value = validateName(form.name) || validateEmail(form.email)
  if (errorMessage.value) return

  isSubmitting.value = true

  try {
    result.value = await findUserId(form)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '아이디를 찾지 못했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthCard
    eyebrow="FIND ID"
    title="아이디 찾기"
    description="가입할 때 입력한 이름과 이메일을 입력해 주세요."
  >
    <div v-if="result" class="auth-form">
      <p class="auth-alert auth-alert--success">
        회원님의 아이디는 <strong>{{ result.maskedUserId }}</strong> 입니다.<br />
        <small>가입일 {{ formatDate(result.createdAt) }}</small>
      </p>
      <RouterLink :to="{ name: 'login' }" class="auth-button auth-button--primary auth-button--block">
        로그인하러 가기
      </RouterLink>
    </div>

    <form v-else class="auth-form" novalidate @submit.prevent="submit">
      <label class="auth-field">
        <span>이름 <span class="auth-field__required">*</span></span>
        <input
          v-model="form.name"
          class="auth-control"
          type="text"
          autocomplete="name"
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
      </label>

      <p v-if="errorMessage" class="auth-alert" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        class="auth-button auth-button--primary auth-button--block"
        :disabled="isSubmitting"
      >
        아이디 찾기
      </button>
    </form>

    <template #footer>
      <div class="auth-links">
        <RouterLink :to="{ name: 'login' }">로그인</RouterLink>
        <RouterLink :to="{ name: 'find-password' }">비밀번호 찾기</RouterLink>
        <RouterLink :to="{ name: 'signup' }">회원가입</RouterLink>
      </div>
    </template>
  </AuthCard>
</template>
