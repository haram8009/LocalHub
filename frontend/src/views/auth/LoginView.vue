<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import AuthCard from '@/components/auth/AuthCard.vue'
import PasswordField from '@/components/auth/PasswordField.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ userId: '', password: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)

// 외부 주소로 튕기지 않도록 앱 내부 경로만 허용합니다.
function getRedirectPath() {
  const redirect = route.query.redirect

  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : '/'
}

async function submit() {
  if (isSubmitting.value) return

  if (!form.userId.trim() || !form.password) {
    errorMessage.value = '아이디와 비밀번호를 입력해 주세요.'
    return
  }

  isSubmitting.value = true
  errorMessage.value = ''

  try {
    await authStore.login({ userId: form.userId.trim(), password: form.password })
    await router.replace(getRedirectPath())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '로그인하지 못했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthCard eyebrow="LOGIN" title="로그인" description="LocalHub 계정으로 로그인하세요.">
    <form class="auth-form" novalidate @submit.prevent="submit">
      <p v-if="route.query.signedUp" class="auth-alert auth-alert--success">
        회원가입이 완료되었습니다. 로그인해 주세요.
      </p>
      <p v-else-if="route.query.reset" class="auth-alert auth-alert--success">
        비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.
      </p>

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

      <PasswordField v-model="form.password" />

      <p v-if="errorMessage" class="auth-alert" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        class="auth-button auth-button--primary auth-button--block"
        :disabled="isSubmitting"
      >
        {{ isSubmitting ? '로그인 중...' : '로그인' }}
      </button>
    </form>

    <template #footer>
      <div class="auth-links">
        <RouterLink :to="{ name: 'find-id' }">아이디 찾기</RouterLink>
        <RouterLink :to="{ name: 'find-password' }">비밀번호 찾기</RouterLink>
        <strong><RouterLink :to="{ name: 'signup' }">회원가입</RouterLink></strong>
      </div>
    </template>
  </AuthCard>
</template>
