<script setup>
defineProps({
  eyebrow: { type: String, default: 'MEMBER' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<template>
  <main class="auth-view">
    <section class="auth-card">
      <header class="auth-card__header">
        <p class="auth-card__eyebrow">{{ eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p v-if="description" class="auth-card__description">{{ description }}</p>
      </header>

      <slot />

      <footer v-if="$slots.footer" class="auth-card__footer">
        <slot name="footer" />
      </footer>
    </section>
  </main>
</template>

<style scoped>
.auth-view {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  place-items: start center;
  padding: var(--spacing-10) var(--content-padding-mobile) var(--spacing-16);
}

.auth-card {
  display: grid;
  gap: var(--spacing-6);
  width: min(100%, 440px);
  padding: var(--spacing-8) var(--spacing-6);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.auth-card__header h1 {
  margin: 0;
  font-size: var(--font-size-2xl);
}

.auth-card__eyebrow {
  margin: 0 0 var(--spacing-2);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.12em;
}

.auth-card__description {
  margin: var(--spacing-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.auth-card__footer {
  padding-top: var(--spacing-5);
  border-top: 1px solid var(--color-border-light);
}
</style>
