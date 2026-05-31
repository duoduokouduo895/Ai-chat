<template>
  <div class="search-input-section">
    <div class="search-box">
      <input
        v-model="query"
        @keyup.enter="handleSearch"
        @input="handleInput"
        type="text"
        placeholder="搜索对话内容、标题..."
        aria-label="搜索对话内容或标题"
        class="search-input"
        ref="searchInput"
      />
      <button
        type="button"
        @click="handleSearch"
        :disabled="!query.trim()"
        class="search-btn"
        aria-label="搜索"
      >
        <font-awesome-icon icon="magnifying-glass" aria-hidden="true" />
      </button>
    </div>
    
    <!-- 搜索建议 -->
    <div v-if="showSuggestions" class="suggestions">
      <div v-if="suggestionLoading" class="suggestion-item suggestion-loading">
        <div class="loading-spinner"></div>
        正在获取建议...
      </div>
      <button
        v-else-if="suggestions.length > 0"
        v-for="suggestion in suggestions"
        :key="suggestion"
        type="button"
        @click="selectSuggestion(suggestion)"
        class="suggestion-item"
      >
        {{ typeof suggestion === 'string' ? suggestion : suggestion.query || suggestion }}
      </button>
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
  } catch (error) {
    // 即便记录历史失败，我们也不打断用户的主流程，只在控制台打印一个错误
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
