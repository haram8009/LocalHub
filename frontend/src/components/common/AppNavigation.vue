<script setup>
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

defineProps({
  mobile: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['navigate'])

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const handleRouteClick = () => {
  emit('navigate')
}

const handleLogout = async () => {
  await authStore.logout()
  emit('navigate')

  // 로그인이 필요한 화면에 머물러 있었다면 홈으로 보냅니다.
  if (route.meta.requiresAuth) {
    await router.push({ name: 'home' })
  }
}
</script>

<template>
  <nav class="app-navigation" :class="{ 'app-navigation--mobile': mobile }" aria-label="주요 메뉴">
    <RouterLink :to="{ name: 'home' }" class="app-navigation__link" @click="handleRouteClick">
      홈
    </RouterLink>

    <RouterLink :to="{ name: 'map' }" class="app-navigation__link" @click="handleRouteClick">
      지도
    </RouterLink>

    <RouterLink :to="{ name: 'post-list' }" class="app-navigation__link" @click="handleRouteClick">
      게시판
    </RouterLink>

    <span class="app-navigation__divider" aria-hidden="true"></span>

    <template v-if="authStore.isLoggedIn">
      <RouterLink :to="{ name: 'mypage' }" class="app-navigation__link" @click="handleRouteClick">
        {{ authStore.user.name }}님
      </RouterLink>

      <button type="button" class="app-navigation__link" @click="handleLogout">로그아웃</button>
    </template>

    <template v-else>
      <RouterLink :to="{ name: 'login' }" class="app-navigation__link" @click="handleRouteClick">
        로그인
      </RouterLink>

      <RouterLink
        :to="{ name: 'signup' }"
        class="app-navigation__link app-navigation__link--primary"
        @click="handleRouteClick"
      >
        회원가입
      </RouterLink>
    </template>
  </nav>
</template>

<style scoped>
.app-navigation {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.app-navigation__link {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2) var(--spacing-3);
  border: 0;
  border-radius: var(--radius-md);
  background-color: transparent;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  line-height: 1;
  text-decoration: none;
  cursor: pointer;
  transition:
    color var(--transition-normal),
    background-color var(--transition-normal);
}

.app-navigation__link:hover {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.app-navigation__link.router-link-exact-active {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.app-navigation__link--primary,
.app-navigation__link--primary.router-link-exact-active {
  color: var(--color-text-inverse);
  background-color: var(--color-primary);
}

.app-navigation__link--primary:hover {
  color: var(--color-text-inverse);
  background-color: var(--color-primary-hover);
}

.app-navigation__divider {
  width: 1px;
  height: 20px;
  margin: 0 var(--spacing-1);
  background-color: var(--color-border);
}

.app-navigation--mobile {
  width: 100%;
  flex-direction: column;
  align-items: stretch;
  gap: var(--spacing-1);
}

.app-navigation--mobile .app-navigation__link {
  width: 100%;
  min-height: 48px;
  justify-content: flex-start;
  padding: var(--spacing-3) var(--spacing-4);
  font-size: var(--font-size-md);
}

.app-navigation--mobile .app-navigation__divider {
  width: 100%;
  height: 1px;
  margin: var(--spacing-1) 0;
}
</style>
