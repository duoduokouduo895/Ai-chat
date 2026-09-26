<template>
  <main class="auth-shell">
    <section class="auth-panel" :aria-labelledby="titleId">
      <header class="auth-brand">
        <span class="brand-wordmark" aria-hidden="true">dduo</span>
        <div>
          <p>Dduo Workspace</p>
          <h1 :id="titleId">{{ title }}</h1>
        </div>
      </header>

      <p v-if="description" class="auth-description">{{ description }}</p>
      <slot />
    </section>

  </main>
</template>

<script setup>
defineProps({
  titleId: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, default: '' }
});
</script>

<style scoped>
.auth-shell {
  background: var(--canvas);
  color: var(--text-primary);
  display: grid;
  font-family: var(--font-family);
  place-items: center;
  min-height: 100dvh;
}

.auth-panel {
  align-self: center;
  padding: 40px 24px;
  width: min(100%, 408px);
}

.auth-brand {
  align-items: center;
  display: flex;
  gap: 14px;
  margin-bottom: 26px;
}

.brand-wordmark {
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.auth-brand p,
.auth-brand h1 {
  margin: 0;
}

.auth-brand p {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.auth-brand h1 {
  font-size: 25px;
  letter-spacing: -0.025em;
  margin-top: 3px;
}

.auth-description {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  margin: 20px 0 26px;
}

</style>
