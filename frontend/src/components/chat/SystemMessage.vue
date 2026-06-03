<template>
  <div class="system-message">
    {{ message }}
  </div>
</template>

<script setup>
defineProps({
  message: {
    type: String,
    required: true
  }
})
</script>

<style scoped>
.system-message {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  padding: var(--spacing-sm) var(--spacing-md);
  background: transparent;
  border-radius: var(--border-radius-md);
  margin: var(--spacing-md) 0;
  border: 1px solid var(--border-subtle);
}
</style>
