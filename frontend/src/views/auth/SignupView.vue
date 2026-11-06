<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import AuthCard from '@/components/auth/AuthCard.vue'
import PasswordField from '@/components/auth/PasswordField.vue'
import { signUp } from '@/services/auth'
import {
  validateEmail,
  validateName,
  validatePassword,
  validatePasswordConfirm,
  validateUserId,
} from '@/utils/validators'

const router = useRouter()

const form = reactive({ userId: '', password: '', passwordConfirm: '', name: '', email: '' })
const errors = reactive({ userId: '', password: '', passwordConfirm: '', name: '', email: '' })
const errorMessage = ref('')
const isSubmitting = ref(false)

function validate() {
  errors.userId = validateUserId(form.userId)
  errors.password = validatePassword(form.password)
  errors.passwordConfirm = validatePasswordConfirm(form.password, form.passwordConfirm)
  errors.name = validateName(form.name)
  errors.email = validateEmail(form.email)

  return Object.values(errors).every((message) => !message)
}

async function submit() {
  if (isSubmitting.value) return

  errorMessage.value = ''
  if (!validate()) return

  isSubmitting.value = true

  try {
    await signUp({
      userId: form.userId,
      password: form.password,
      name: form.name,
      email: form.email,
    })
    await router.push({ name: 'login', query: { signedUp: '1' } })
  } catch (error) {
    const field = error?.data?.field

    if (field in errors) {
      errors[field] = error.message
    } else {
      errorMessage.value = error instanceof Error ? error.message : '회원가입하지 못했습니다.'
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthCard
    eyebrow="SIGN UP"
    title="회원가입"
    description="가입하면 여행 코스를 작성하고 관리할 수 있습니다."
  >
    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="auth-field">
        <span>아이디 <span class="auth-field__required">*</span></span>
        <input
          v-model.trim="form.userId"
          class="auth-control"
          :class="{ 'auth-control--invalid': errors.userId }"
          type="text"
          autocomplete="username"
          maxlength="20"
          placeholder="영문 소문자, 숫자 4~20자"
        />
        <p v-if="errors.userId" class="auth-error">{{ errors.userId }}</p>
      </label>

      <PasswordField
        v-model="form.password"
        autocomplete="new-password"
        show-rules
        :error="errors.password"
      />

      <PasswordField
        v-model="form.passwordConfirm"
        label="비밀번호 확인"
        autocomplete="new-password"
        :error="errors.passwordConfirm"
      />

      <label class="auth-field">
        <span>이름 <span class="auth-field__required">*</span></span>
        <input
          v-model="form.name"
          class="auth-control"
          :class="{ 'auth-control--invalid': errors.name }"
          type="text"
          autocomplete="name"
          maxlength="20"
        />
        <p v-if="errors.name" class="auth-error">{{ errors.name }}</p>
      </label>

      <label class="auth-field">
        <span>이메일 <span class="auth-field__required">*</span></span>
        <input
          v-model="form.email"
          class="auth-control"
          :class="{ 'auth-control--invalid': errors.email }"
          type="email"
          autocomplete="email"
          maxlength="100"
          placeholder="example@email.com"
        />
        <span class="auth-hint">아이디·비밀번호를 찾을 때 사용됩니다.</span>
        <p v-if="errors.email" class="auth-error">{{ errors.email }}</p>
      </label>

      <p v-if="errorMessage" class="auth-alert" role="alert">{{ errorMessage }}</p>

      <button
        type="submit"
        class="auth-button auth-button--primary auth-button--block"
        :disabled="isSubmitting"
      >
        {{ isSubmitting ? '가입 중...' : '가입하기' }}
      </button>
    </form>

    <template #footer>
      <div class="auth-links">
        <span>이미 계정이 있나요?</span>
        <strong><RouterLink :to="{ name: 'login' }">로그인</RouterLink></strong>
      </div>
    </template>
  </AuthCard>
</template>
