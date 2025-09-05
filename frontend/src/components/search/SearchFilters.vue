<template>
  <div class="filters-section">
    <button @click="showAdvanced = !showAdvanced" class="toggle-advanced">
      {{ showAdvanced ? '隐藏' : '显示' }}高级筛选 {{ showAdvanced ? '▲' : '▼' }}
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
          <span v-if="modelsError" class="error-text" :title="modelsError">⚠️</span>
        </div>
        
        <div class="filter-group">
          <label>消息数量:</label>
          <input v-model="managedFilters.minMessageCount" type="number" placeholder="最少" class="filter-input-small" />
          <span>-</span>
          <input v-model="managedFilters.maxMessageCount" type="number" placeholder="最多" class="filter-input-small" />
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
import { ref, computed, onMounted } from 'vue'
import apiClient from '../../services/apiClient.js'

// Props
const props = defineProps({
  filters: {
    type: Object,
    required: true
  }
})

// Emits
const emit = defineEmits(['apply-filters', 'clear-filters', 'update:filters'])

// 响应式数据
const showAdvanced = ref(false)
const availableModels = ref([])
const modelsLoading = ref(false)
const modelsError = ref('')

// 使用计算属性来代理props，这是v-model在组件上使用的标准做法
const managedFilters = computed({
  get() {
    return props.filters;
  },
  set(value) {
    emit('update:filters', value);
  }
});

// 应用筛选
const applyFilters = () => {
  // managedFilters已经是最新状态，直接触发父组件的搜索
  emit('apply-filters')
}

// 清空筛选条件
const clearFilters = () => {
  // 创建一个空的对象来更新，而不是直接修改
  const clearedFilters = {
    ...props.filters, // 保留其他可能存在的字段
    startTime: '',
    endTime: '',
    model: '',
    minMessageCount: '',
    maxMessageCount: ''
  };
  emit('update:filters', clearedFilters);
  // 也可以选择立即触发一次搜索
  // emit('apply-filters'); 
}

// 获取可用模型列表
const loadAvailableModels = async () => {
  modelsLoading.value = true
  modelsError.value = ''
  
  try {
    const response = await apiClient.get('/models');
    if (response.data && Array.isArray(response.data)) {
      availableModels.value = response.data;
    } else {
      throw new Error('获取模型列表失败：数据格式错误');
    }
  } catch (error) {
    console.error('获取模型列表失败:', error);
    modelsError.value = error.message || '获取模型列表失败，请稍后重试';
    // 设置默认模型列表作为备选
    availableModels.value = [
      { value: 'zai-org/GLM-4.5-Air', label: 'GLM-4' },
      { value: 'deepseek-ai/DeepSeek-V3', label: 'DeepSeek V3' },
      { value: 'Tongyi-Zhiwen/QwenLong-L1-32B', label: '通义千问-32B' }
    ];
  } finally {
    modelsLoading.value = false;
  }
}

// 组件挂载时获取模型列表
onMounted(() => {
  loadAvailableModels()
})

// 暴露方法
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
  /* 触摸优化 */
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

.filter-input, .filter-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  /* 移动端优化 */
  -webkit-appearance: none;
  appearance: none;
}

.filter-input-small {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  width: 80px;
  /* 移动端优化 */
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
  /* 触摸优化 */
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
  /* 触摸优化 */
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

/* 响应式设计 */
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
  
  .filter-input, .filter-select {
    width: 100%;
    padding: 10px 12px;
    font-size: 16px; /* 防止iOS缩放 */
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
  
  .filter-input, .filter-select {
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