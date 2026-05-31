<template>
  <div class="state-block" :class="type" role="status" aria-live="polite">
    <div class="state-icon">{{ icon }}</div>
    <div class="state-content">
      <div class="state-title">{{ title }}</div>
      <div v-if="message" class="state-message">{{ message }}</div>
      <button v-if="actionText" class="state-action" type="button" @click="$emit('action')">
        {{ actionText }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'empty'
  },
  title: {
    type: String,
    required: true
  },
  message: {
    type: String,
    default: ''
  },
  actionText: {
    type: String,
    default: ''
  }
})

defineEmits(['action'])

const icon = computed(() => {
  if (props.type === 'loading') return '…'
  if (props.type === 'error') return '!'
  return '·'
})
</script>

<style scoped>
.state-block {
  align-items: center;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-lg);
  color: var(--text-primary);
  display: flex;
  gap: var(--spacing-md);
  margin: var(--spacing-md) auto;
  max-width: 560px;
  padding: var(--spacing-lg);
  text-align: left;
}

.state-block.loading .state-icon {
  animation: pulse 1.2s ease-in-out infinite;
}

.state-block.error {
  background: var(--danger-bg);
  border-color: rgba(220, 53, 69, 0.22);
}

.state-icon {
  align-items: center;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--border-radius-full);
  color: var(--text-secondary);
  display: inline-flex;
  flex: 0 0 auto;
  font-size: var(--font-size-lg);
  font-weight: 700;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.state-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  margin-bottom: var(--spacing-xs);
}

.state-message {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  white-space: pre-line;
}

.state-action {
  background: var(--primary-color);
  border: none;
  border-radius: var(--border-radius-md);
  color: var(--text-white);
  cursor: pointer;
  font-size: var(--font-size-sm);
  margin-top: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
}

.state-action:hover {
  background: var(--primary-hover);
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.45;
  }
  50% {
    opacity: 1;
  }
}
</style>
