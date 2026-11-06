<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import AuthCard from '@/components/auth/AuthCard.vue'
import PasswordField from '@/components/auth/PasswordField.vue'
import { useAuthStore } from '@/stores/auth'
import { validatePassword, validatePasswordConfirm } from '@/utils/validators'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({ password: '', passwordConfirm: '', code: '' })
const errors = reactive({ password: '', passwordConfirm: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)

async function submit() {
  if (isSubmitting.value) return

  errorMessage.value = ''
  errors.password = validatePassword(form.password)
  errors.passwordConfirm = validatePasswordConfirm(form.password, form.passwordConfirm)
  if (errors.password || errors.passwordConfirm) return

  isSubmitting.value = true

  try {
    await authStore.resetPassword(form.password, form.code)
    await router.replace({ name: 'login', query: { reset: '1' } })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '비밀번호를 변경하지 못했습니다.'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthCard
    eyebrow="RESET PASSWORD"
    title="비밀번호 재설정"
    :description="`${authStore.resetTarget?.userId ?? ''} 계정의 새 비밀번호를 입력해 주세요.`"
  >
    <form class="auth-form" novalidate @submit.prevent="submit">
      <p v-if="authStore.resetTarget?.devResetCode" class="auth-hint">
        로컬 개발용 인증 코드: {{ authStore.resetTarget.devResetCode }}
      </p>
      <label class="auth-field"
        ><span>인증 코드</span
        ><input
          v-model="form.code"
          class="auth-control"
          inputmode="numeric"
          maxlength="6"
          autocomplete="one-time-code"
      /></label>
      <PasswordField
        v-model="form.password"
        label="새 비밀번호"
        autocomplete="new-password"
        show-rules
        :error="errors.password"
      />

      <PasswordField
        v-model="form.passwordConfirm"
        label="새 비밀번호 확인"
        autocomplete="new-password"
        :error="errors.passwordConfirm"
      />

      <p v-if="errorMessage" class="auth-alert" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        class="auth-button auth-button--primary auth-button--block"
        :disabled="isSubmitting"
      >
        비밀번호 변경
      </button>
    </form>

    <template #footer>
      <div class="auth-links">
        <RouterLink :to="{ name: 'login' }">로그인으로 돌아가기</RouterLink>
      </div>
    </template>
  </AuthCard>
</template>
