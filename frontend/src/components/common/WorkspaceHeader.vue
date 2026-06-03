<template>
  <header class="workspace-header">
    <div>
      <span class="workspace-kicker">{{ kicker }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="workspace-actions">
      <slot />
    </div>
  </header>
</template>

<script setup>
defineProps({
  kicker: { type: String, default: 'Workspace' },
  title: { type: String, required: true },
  description: { type: String, default: '' }
});
</script>

<style scoped>
.workspace-header {
  align-items: center;
  background: var(--surface-1);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  gap: 18px;
  justify-content: space-between;
  min-height: 72px;
  padding: 13px 20px;
}

.workspace-kicker {
  color: var(--text-muted);
  display: block;
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.14em;
  margin-bottom: 3px;
  text-transform: uppercase;
}

h1,
p {
  margin: 0;
}

h1 {
  font-size: var(--font-size-lg);
  letter-spacing: -0.02em;
}

p {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  margin-top: 4px;
}

.workspace-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

@media (max-width: 768px) {
  .workspace-header {
    min-height: 62px;
    padding: 10px 12px;
  }

  p {
    display: none;
  }
}
</style>
