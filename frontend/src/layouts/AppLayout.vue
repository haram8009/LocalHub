<script setup>
import { RouterView } from 'vue-router'

import AppHeader from '@/components/common/AppHeader.vue'
import AppLoading from '@/components/common/AppLoading.vue'
</script>

<template>
  <div class="app-layout">
    <AppHeader />

    <main class="app-main">
      <RouterView />
    </main>

    <footer class="app-footer">제작자: 박시온, 이하람</footer>

    <AppLoading />
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  padding-top: var(--header-height);
  background-color: var(--color-background);
}

.app-main {
  width: 100%;
  max-width: var(--content-max-width);
  min-height: calc(100vh - var(--header-height));
  margin: 0 auto;
  padding: var(--spacing-6) var(--content-padding-mobile);
}

.app-footer {
  padding: var(--spacing-4) var(--content-padding-mobile);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  text-align: center;
}

@media (min-width: 768px) {
  .app-main {
    padding: var(--spacing-8) var(--content-padding-tablet);
  }
}

@media (min-width: 1200px) {
  .app-main {
    padding: var(--spacing-10) var(--content-padding-desktop);
  }
}
</style>
