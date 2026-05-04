<template>
  <div class="search-panel">
    <!-- 搜索头部 -->
    <div class="search-header">
      <h2>🔍 对话搜索</h2>
      <button class="close-search" @click="$emit('close')" title="关闭搜索">✕</button>
    </div>

    <!-- 搜索输入组件 -->
    <SearchInput
      v-model="searchQuery"
      @search="executeSearch"
      @suggestion-selected="handleSuggestionSelected"
    />

    <!-- 搜索筛选器组件 -->
    <SearchFilters
      ref="filtersRef"
      v-model:filters="filters"
      @apply-filters="executeSearch"
      @clear-filters="clearFilters"
    />

    <!-- 搜索历史 -->
    <SearchHistory
      v-if="!hasSearched"
      ref="historyRef"
      :show-stats="true"
      @history-selected="handleHistorySelected"
    />

    <!-- 搜索结果组件 -->
    <SearchResults
      v-if="hasSearched"
      :results="searchResults"
      :loading="loading"
      :error="searchError"
      :total-results="totalResults"
      :total-pages="totalPages"
      :current-page="currentPage"
      :search-time="searchTime"
      :search-query="searchQuery"
      :sort-by="sortBy"
      @open-conversation="openConversation"
      @page-change="changePage"
      @sort-change="sortResults"
    />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { SearchApiService } from '../../services/searchApi.js'
import SearchInput from './SearchInput.vue'
import SearchFilters from './SearchFilters.vue'
import SearchResults from './SearchResults.vue'
import SearchHistory from './SearchHistory.vue'

// Props and Emits
const emit = defineEmits(['close', 'openConversation'])

// 组件引用
const filtersRef = ref(null)
const historyRef = ref(null)

// 响应式数据
const searchQuery = ref('')
const searchResults = ref([])
const loading = ref(false)
const hasSearched = ref(false)
const searchError = ref('')

// 搜索统计
const totalResults = ref(0)
const totalPages = ref(0)
const currentPage = ref(1)
const searchTime = ref(0)
const sortBy = ref('relevance')

// 筛选器
const filters = reactive({
  startTime: '',
  endTime: '',
  model: '',
  minMessageCount: '',
  maxMessageCount: ''
})

// 事件处理器
const handleSuggestionSelected = (query) => {
  searchQuery.value = query;
  executeSearch();
};

const handleHistorySelected = (query) => {
  console.log('处理搜索历史选择:', query)
  searchQuery.value = query;
  executeSearch();
};

// 执行搜索
const executeSearch = async () => {
  if (!searchQuery.value.trim()) return;

  loading.value = true;
  hasSearched.value = true;
  searchError.value = '';
  const startTime = Date.now();

  try {
    const searchRequest = {
      query: searchQuery.value,
      startDate: filters.startTime,
      endDate: filters.endTime,
      models: filters.model ? [filters.model] : [],
      minMessages: filters.minMessageCount,
      maxMessages: filters.maxMessageCount,
    };

    console.log('--- FINAL PAYLOAD CHECK ---');
    console.log('即将发送的搜索参数 (searchRequest):', JSON.parse(JSON.stringify(searchRequest)));
    console.log('--------------------------');

    const response = await SearchApiService.search(
      searchRequest,
      currentPage.value - 1,
      10
    );
    console.log('[DEBUG] 搜索API调用完成, 收到原始响应:', response);

    // 增加对响应类型的详细判断
    if (response && Array.isArray(response)) {
      console.log('[DEBUG] 响应是数组，直接赋值。数组长度:', response.length);
      searchResults.value = response;
      totalResults.value = response.length; // 注意：对于非分页数组，这可能不是总结果数
      totalPages.value = Math.ceil(response.length / 10) || 1;
    } else if (response && response.content && Array.isArray(response.content)) {
      console.log('[DEBUG] 响应是分页对象，赋值 content。总元素:', response.totalElements);
      searchResults.value = response.content;
      totalResults.value = response.totalElements || response.content.length;
      totalPages.value = response.totalPages || 0;
    } else {
      console.warn('[DEBUG] 响应格式不符合预期，设置为空结果。响应:', response);
      searchResults.value = [];
      totalResults.value = 0;
      totalPages.value = 0;
    }

    searchTime.value = Date.now() - startTime;

    // 记录搜索历史，实现去重更新
    try {
      await SearchApiService.recordSearchHistory(searchQuery.value, 'unified');
      console.log(`搜索历史已记录/更新: "${searchQuery.value}"`);
      
      // 刷新搜索历史显示
      if (historyRef.value) {
        await historyRef.value.refresh();
        console.log('搜索历史显示已刷新');
      }
    } catch (historyError) {
      console.error('更新搜索历史失败:', historyError);
      // 历史记录错误不影响搜索结果显示
    }
  } catch (error) {
    console.error('搜索失败:', error);
    searchError.value = `搜索失败: ${error.response?.data?.message || error.message || '请稍后重试'}`;
    searchResults.value = [];
    totalResults.value = 0;
    totalPages.value = 0;
  } finally {
    loading.value = false;
  }
};

// 清空筛选条件
const clearFilters = () => {
  filters.startTime = ''
  filters.endTime = ''
  filters.model = ''
  filters.minMessageCount = ''
  filters.maxMessageCount = ''
}

// 排序结果
const sortResults = (newSortBy) => {
  sortBy.value = newSortBy;
  executeSearch();
};

// 换页
const changePage = (page) => {
  if (page < 1 || page > totalPages.value) return;
  currentPage.value = page;
  executeSearch();
};

// 打开对话
const openConversation = (conversation) => {
  emit('openConversation', conversation)
}
</script>

<style scoped>
.search-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
  border-radius: 8px;
  overflow: hidden;
}

.search-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e9ecef;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.search-header h2 {
  margin: 0;
  color: #333;
  font-size: 18px;
}

.close-search {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: #666;
  padding: 8px;
  border-radius: 4px;
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

.close-search:hover,
.close-search:active {
  background: #f0f0f0;
  color: #333;
  transform: scale(0.95);
}

/* 移动端响应式设计 */
@media (max-width: 768px) {
  .search-header {
    padding: 12px 16px;
  }
  
  .search-header h2 {
    font-size: 16px;
  }
  
  .close-search {
    font-size: 20px;
    padding: 10px;
  }
}

@media (max-width: 480px) {
  .search-header {
    padding: 10px 12px;
  }
  
  .search-header h2 {
    font-size: 15px;
  }
  
  .close-search {
    font-size: 18px;
    padding: 8px;
  }
}
</style>
