<template>
  <div class="model-selector-container">
    <select 
      :value="modelValue" 
      @change="updateModel"
      class="input-base model-selector"
    >
      <option 
        v-for="model in models" 
        :key="model.value" 
        :value="model.value"
      >
        {{ model.label }}
        <span v-if="model.speed === 'fast'" class="speed-indicator">⚡</span>
        <span v-else-if="model.speed === 'medium'" class="speed-indicator">🔥</span>
        <span v-else-if="model.speed === 'slow'" class="speed-indicator">🐌</span>
        - {{ model.description }}
      </option>
    </select>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    required: true
  },
  models: {
    type: Array,
    default: () => []
  }
})

// Emits
const emit = defineEmits(['update:modelValue'])

// 更新模型选择
const updateModel = (event) => {
  emit('update:modelValue', event.target.value)
}
</script>

<style scoped>
.model-selector-container {
  display: flex;
  align-items: center;
}

.model-selector {
  min-width: 200px;
  width: fit-content;
  cursor: pointer;
}

.speed-indicator {
  margin-left: var(--spacing-xs);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .model-selector {
    width: 100%;
    min-width: auto;
  }
}
</style>