<template>
  <div class="filters-section">
    <button @click="showAdvanced = !showAdvanced" class="toggle-advanced">
      {{ showAdvanced ? '隐藏' : '显示' }}高级筛选{{ showAdvanced ? ' ▲' : ' ▼' }}
    </button>

    <div v-if="showAdvanced" class="advanced-filters">
      <div class="filter-row">
        <div class="filter-group">
          <label>时间范围:</label>
          <input v-model="managedFilters.startTime" type="datetime-local" class="filter-input" />
          <span>至</span>
          <input v-model="managedFilters.endTime" type="datetime-local" class="filter-input" />
        </div>
      </div>

      <div class="filter-row">
        <div class="filter-group">
          <label>AI模型:</label>
          <select v-model="managedFilters.model" class="filter-select" :disabled="modelsLoading">
            <option value="">{{ modelsLoading ? '加载中...' : '全部模型' }}</option>
            <option v-for="model in availableModels" :key="model.value" :value="model.value">
              {{ model.label }}
            </option>
          </select>
          <span v-if="modelsError" class="error-text" :title="modelsError">!</span>
        </div>

        <div class="filter-group">
          <label>消息数量:</label>
          <input v-model="managedFilters.minMessageCount" type="number" placeholder="最小" class="filter-input-small" />
          <span>-</span>
          <input v-model="managedFilters.maxMessageCount" type="number" placeholder="最大" class="filter-input-small" />
        </div>
      </div>

      <div class="filter-actions">
        <button @click="applyFilters" class="apply-filters-btn">应用筛选</button>
        <button @click="clearFilters" class="clear-filters-btn">清空筛选</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import apiClient from '../../services/apiClient.js'

const props = defineProps({
  filters: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['apply-filters', 'clear-filters', 'update:filters'])

const showAdvanced = ref(false)
const availableModels = ref([])
const modelsLoading = ref(false)
const modelsError = ref('')

const managedFilters = computed({
  get() {
    return props.filters
  },
  set(value) {
    emit('update:filters', value)
  }
})

const applyFilters = () => {
  emit('apply-filters')
}

const clearFilters = () => {
  emit('update:filters', {
    ...props.filters,
    startTime: '',
    endTime: '',
    model: '',
    minMessageCount: '',
    maxMessageCount: ''
  })
}

const loadAvailableModels = async () => {
  modelsLoading.value = true
  modelsError.value = ''

  try {
    const response = await apiClient.get('/models')
    if (response.data && Array.isArray(response.data)) {
      availableModels.value = response.data
    } else {
      throw new Error('获取模型列表失败：返回数据格式错误')
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
    modelsError.value = error.message || '获取模型列表失败，请稍后重试'
    availableModels.value = [
      { value: 'zai-org/GLM-4.5-Air', label: 'GLM-4' },
      { value: 'Pro/deepseek-ai/DeepSeek-R1', label: 'DeepSeek R1' },
      { value: 'Qwen/Qwen3-Coder-30B-A3B-Instruct', label: 'Qwen3 Coder 30B' }
    ]
  } finally {
    modelsLoading.value = false
  }
}

onMounted(() => {
  loadAvailableModels()
})

defineExpose({
  showAdvanced
})
</script>

<style scoped>
.filters-section {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e9ecef;
}

.toggle-advanced {
  background: none;
  border: 1px solid #007bff;
  color: #007bff;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.toggle-advanced:hover,
.toggle-advanced:active {
  background: #007bff;
  color: white;
  transform: scale(0.95);
}

.advanced-filters {
  margin-top: 16px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.filter-row {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 200px;
}

.filter-group label {
  font-weight: 500;
  color: #333;
  white-space: nowrap;
}

.filter-input,
.filter-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  -webkit-appearance: none;
  appearance: none;
}

.filter-input-small {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  width: 80px;
  -webkit-appearance: none;
  appearance: none;
}

.filter-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.apply-filters-btn {
  padding: 10px 20px;
  background: #28a745;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.apply-filters-btn:hover,
.apply-filters-btn:active {
  background: #218838;
  transform: scale(0.95);
}

.clear-filters-btn {
  padding: 10px 20px;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.clear-filters-btn:hover,
.clear-filters-btn:active {
  background: #545b62;
  transform: scale(0.95);
}

.error-text {
  color: #dc3545;
  font-size: 16px;
  cursor: help;
  margin-left: 4px;
}

@media (max-width: 768px) {
  .filters-section {
    padding: 12px 16px;
  }

  .toggle-advanced {
    width: 100%;
    padding: 12px 16px;
    font-size: 15px;
  }

  .advanced-filters {
    padding: 12px;
  }

  .filter-row {
    flex-direction: column;
    gap: 12px;
  }

  .filter-group {
    min-width: auto;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .filter-group label {
    font-size: 14px;
  }

  .filter-input,
  .filter-select {
    width: 100%;
    padding: 10px 12px;
    font-size: 16px;
  }

  .filter-input-small {
    width: 100%;
    padding: 10px 12px;
    font-size: 16px;
  }

  .filter-actions {
    flex-direction: column;
    gap: 8px;
  }

  .apply-filters-btn,
  .clear-filters-btn {
    width: 100%;
    padding: 12px 20px;
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .filters-section {
    padding: 10px 12px;
  }

  .toggle-advanced {
    padding: 10px 14px;
    font-size: 14px;
  }

  .advanced-filters {
    padding: 10px;
  }

  .filter-group label {
    font-size: 13px;
  }

  .filter-input,
  .filter-select {
    padding: 8px 10px;
    font-size: 16px;
  }

  .filter-input-small {
    padding: 8px 10px;
    font-size: 16px;
  }

  .apply-filters-btn,
  .clear-filters-btn {
    padding: 10px 16px;
    font-size: 14px;
  }
}
</style>
