<template>
  <div class="model-pills" aria-label="AI 模型">
    <button
      v-for="model in visibleModels"
      :key="model.value"
      class="model-pill"
      :class="{ active: model.value === modelValue }"
      type="button"
      :aria-pressed="model.value === modelValue"
      @click="$emit('update:modelValue', model.value)"
    >
      <span class="model-dot" aria-hidden="true"></span>
      <span>{{ model.label }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: { type: String, required: true },
  models: { type: Array, default: () => [] }
});

defineEmits(['update:modelValue']);

const visibleModels = computed(() => {
  if (props.models.length > 0) {
    return props.models;
  }
  return [{
    value: props.modelValue,
    label: '默认模型'
  }];
});
</script>

<style scoped>
.model-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.model-pill {
  align-items: center;
  background: transparent;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  color: var(--text-secondary);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: var(--font-size-xs);
  gap: 6px;
  min-height: 30px;
  padding: 5px 10px;
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast);
}

.model-pill:hover,
.model-pill:focus-visible {
  background: var(--surface-3);
  border-color: var(--border-strong);
  color: var(--text-primary);
  outline: none;
}

.model-pill.active {
  background: var(--accent-soft);
  border-color: oklch(0.55 0.13 285);
  color: var(--text-accent);
}

.model-dot {
  background: currentColor;
  border-radius: 50%;
  height: 6px;
  opacity: 0.78;
  width: 6px;
}
</style>
