<script setup>
import { computed, ref } from 'vue'

import { getPasswordChecks, PASSWORD_MAX_LENGTH } from '@/utils/validators'

const model = defineModel({ type: String, default: '' })

defineProps({
  label: { type: String, default: '비밀번호' },
  autocomplete: { type: String, default: 'current-password' },
  placeholder: { type: String, default: '' },
  error: { type: String, default: '' },
  showRules: { type: Boolean, default: false },
})

const isVisible = ref(false)
const checks = computed(() => getPasswordChecks(model.value))
</script>

<template>
  <label class="auth-field">
    <span>{{ label }} <span class="auth-field__required">*</span></span>

    <span class="password-field">
      <input
        v-model="model"
        class="auth-control"
        :class="{ 'auth-control--invalid': error }"
        :type="isVisible ? 'text' : 'password'"
        :autocomplete="autocomplete"
        :placeholder="placeholder"
        :maxlength="PASSWORD_MAX_LENGTH"
      />
      <button
        type="button"
        class="password-field__toggle"
        :aria-pressed="isVisible"
        @click="isVisible = !isVisible"
      >
        {{ isVisible ? '숨기기' : '보기' }}
      </button>
    </span>

    <ul v-if="showRules" class="password-rules" aria-label="비밀번호 조건">
      <li
        v-for="check in checks"
        :key="check.key"
        :class="{ 'password-rules__item--passed': check.passed }"
      >
        <span aria-hidden="true">{{ check.passed ? '✓' : '·' }}</span>
        {{ check.label }}
        <span class="sr-only">{{ check.passed ? '충족' : '미충족' }}</span>
      </li>
    </ul>

    <p v-if="error" class="auth-error">{{ error }}</p>
  </label>
</template>

<style scoped>
.password-field {
  position: relative;
  display: block;
}

.password-field .auth-control {
  padding-right: 72px;
}

.password-field__toggle {
  position: absolute;
  top: 50%;
  right: var(--spacing-2);
  min-height: 32px;
  padding: 0 var(--spacing-2);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transform: translateY(-50%);
}

.password-field__toggle:hover {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.password-rules {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-1) var(--spacing-3);
  margin: 0;
  padding: 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-regular);
  list-style: none;
}

.password-rules__item--passed {
  color: var(--color-success);
}
</style>
