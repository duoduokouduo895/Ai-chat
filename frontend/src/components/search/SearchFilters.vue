<template>
  <div class="filters-section">
    <button @click="showAdvanced = !showAdvanced" class="toggle-advanced">
      {{ showAdvanced ? '隐藏' : '显示' }}高级筛选{{ showAdvanced ? ' ▲' : ' ▼' }}
    </button>

    <div v-if="showAdvanced" class="advanced-filters">
      <div class="filter-row">
        <div class="filter-group">
          <label for="search-start-time">时间范围:</label>
          <input id="search-start-time" :value="filters.startTime" type="datetime-local" class="filter-input" aria-label="开始时间" @input="updateFilter('startTime', $event.target.value)" />
          <span>至</span>
          <input id="search-end-time" :value="filters.endTime" type="datetime-local" class="filter-input" aria-label="结束时间" @input="updateFilter('endTime', $event.target.value)" />
        </div>
      </div>

      <div class="filter-row">
        <div class="filter-group">
          <label for="search-model">AI模型:</label>
          <select id="search-model" :value="filters.model" class="filter-select" :disabled="modelsLoading" @change="updateFilter('model', $event.target.value)">
            <option value="">{{ modelsLoading ? '加载中...' : '全部模型' }}</option>
            <option v-for="model in availableModels" :key="model.value" :value="model.value">
              {{ model.label }}
            </option>
          </select>
          <span v-if="modelsError" class="error-text" :title="modelsError">!</span>
        </div>

        <div class="filter-group">
          <label for="search-min-message-count">消息数量:</label>
          <input id="search-min-message-count" :value="filters.minMessageCount" type="number" placeholder="最小" class="filter-input-small" aria-label="最少消息数量" @input="updateFilter('minMessageCount', $event.target.value)" />
          <span>-</span>
          <input id="search-max-message-count" :value="filters.maxMessageCount" type="number" placeholder="最大" class="filter-input-small" aria-label="最多消息数量" @input="updateFilter('maxMessageCount', $event.target.value)" />
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
import { onMounted, ref } from 'vue'
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

const updateFilter = (key, value) => {
  emit('update:filters', { ...props.filters, [key]: value })
}

const applyFilters = () => {
  emit('apply-filters')
}

const clearFilters = () => {
  emit('clear-filters')
}

const loadAvailableModels = async () => {
  modelsLoading.value = true
  modelsError.value = ''

  try {
    const response = await apiClient.get('/ai/models')
    if (response.data && Array.isArray(response.data)) {
      availableModels.value = response.data
    } else {
      throw new Error('获取模型列表失败：返回数据格式错误')
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
    modelsError.value = error.message || '获取模型列表失败，请稍后重试'
    availableModels.value = []
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
