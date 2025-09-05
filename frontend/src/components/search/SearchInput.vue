<template>
  <div class="search-input-section">
    <div class="search-box">
      <input
        v-model="query"
        @keyup.enter="handleSearch"
        @input="handleInput"
        type="text"
        placeholder="搜索对话内容、标题..."
        class="search-input"
        ref="searchInput"
      />
      <button @click="handleSearch" :disabled="!query.trim()" class="search-btn">
        🔍
      </button>
    </div>
    
    <!-- 搜索建议 -->
    <div v-if="showSuggestions" class="suggestions">
      <div v-if="suggestionLoading" class="suggestion-item loading">
        <div class="loading-spinner"></div>
        正在获取建议...
      </div>
      <div
        v-else-if="suggestions.length > 0"
        v-for="suggestion in suggestions"
        :key="suggestion"
        @click="selectSuggestion(suggestion)"
        class="suggestion-item"
      >
        {{ typeof suggestion === 'string' ? suggestion : suggestion.query || suggestion }}
      </div>
      <div v-else class="suggestion-item no-suggestions">
        暂无搜索建议
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { SearchApiService } from '../../services/searchApi.js'

// Props
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

// Emits
const emit = defineEmits(['update:modelValue', 'search', 'suggestion-selected'])

// 响应式数据
const query = ref(props.modelValue)
const suggestions = ref([])
const showSuggestions = ref(false)
const suggestionLoading = ref(false)
const searchInput = ref(null)

// 组件挂载时聚焦搜索框
onMounted(() => {
  nextTick(() => {
    if (searchInput.value) {
      searchInput.value.focus()
    }
  })
})

// 监听输入变化
watch(query, (newQuery) => {
  emit('update:modelValue', newQuery)
  
  if (newQuery.length >= 2) {
    getSuggestions(newQuery)
    showSuggestions.value = true
  } else {
    suggestions.value = []
    showSuggestions.value = false
  }
})

// 监听外部值变化
watch(() => props.modelValue, (newValue) => {
  query.value = newValue
})

// 获取搜索建议
const getSuggestions = async (searchQuery) => {
  if (!searchQuery || searchQuery.length < 2) {
    suggestions.value = []
    showSuggestions.value = false
    return
  }
  
  suggestionLoading.value = true
  
  try {
    const response = await SearchApiService.getSearchSuggestions(searchQuery, 5)
    suggestions.value = response || []
    showSuggestions.value = suggestions.value.length > 0
  } catch (error) {
    console.error('获取搜索建议失败:', error)
    suggestions.value = []
    showSuggestions.value = false
  } finally {
    suggestionLoading.value = false
  }
}

// 处理输入
const handleInput = () => {
  // 实时搜索建议已在watch中处理
}

// 处理搜索
const handleSearch = async () => {
  const searchTerm = query.value.trim()
  if (!searchTerm) return

  showSuggestions.value = false
  emit('search', searchTerm)

  // --- 关键修复：在这里补上对“记录搜索历史”API的调用 ---
  try {
    // 我们在后台悄悄地调用这个函数，不影响主搜索流程
    // 即使用户网络不好导致记录失败，try...catch也能确保主搜索不受影响
    await SearchApiService.recordSearchHistory(searchTerm, 'fulltext')
    console.log(`成功记录搜索历史: "${searchTerm}"`)
  } catch (error) {
    // 即便记录历史失败，我们也不打断用户的主流程，只在控制台打印一个错误
    console.error('后台记录搜索历史失败，但这不影响本次搜索:', error)
  }
}

// 选择建议
const selectSuggestion = (suggestion) => {
  const selectedQuery = typeof suggestion === 'string' ? suggestion : suggestion.query || suggestion
  query.value = selectedQuery
  showSuggestions.value = false
  emit('suggestion-selected', selectedQuery)
}
</script>

<style scoped>
.search-input-section {
  padding: 20px;
  background: #fff;
  border-bottom: 1px solid #e9ecef;
  position: relative;
}

.search-box {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.search-input {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e9ecef;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
  /* 移动端优化 */
  -webkit-appearance: none;
  appearance: none;
}

.search-input:focus {
  outline: none;
  border-color: #007bff;
}

.search-btn {
  padding: 12px 20px;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.search-btn:hover:not(:disabled),
.search-btn:active:not(:disabled) {
  background: #0056b3;
  transform: scale(0.95);
}

.search-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.suggestions {
  position: absolute;
  top: 100%;
  left: 20px;
  right: 20px;
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  z-index: 1000;
  max-height: 200px;
  overflow-y: auto;
}

.suggestion-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.suggestion-item:hover,
.suggestion-item:active {
  background: #f8f9fa;
  transform: scale(0.98);
}

.suggestion-item:last-child {
  border-bottom: none;
}

.suggestion-item.loading {
  cursor: default;
  color: #666;
}

.suggestion-item.no-suggestions {
  cursor: default;
  color: #999;
  font-style: italic;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #f3f3f3;
  border-top: 2px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 移动端响应式设计 */
@media (max-width: 768px) {
  .search-input-section {
    padding: 16px;
  }
  
  .search-box {
    gap: 6px;
  }
  
  .search-input {
    padding: 10px 14px;
    font-size: 16px; /* 防止iOS缩放 */
  }
  
  .search-btn {
    padding: 10px 16px;
    font-size: 16px;
  }
  
  .suggestions {
    left: 16px;
    right: 16px;
  }
  
  .suggestion-item {
    padding: 14px 16px;
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .search-input-section {
    padding: 12px;
  }
  
  .search-box {
    gap: 4px;
  }
  
  .search-input {
    padding: 8px 12px;
    font-size: 16px;
  }
  
  .search-btn {
    padding: 8px 12px;
    font-size: 14px;
    min-width: 40px;
  }
  
  .suggestions {
    left: 12px;
    right: 12px;
    max-height: 150px;
  }
  
  .suggestion-item {
    padding: 12px 14px;
    font-size: 14px;
    min-height: 40px;
  }
}
</style>