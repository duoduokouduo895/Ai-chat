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
  font-style: italic;
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--border-radius-md);
  margin: var(--spacing-md) 0;
  border: 1px solid var(--border-light);
}
</style>