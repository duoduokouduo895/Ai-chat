<template>
  <div class="search-results">
    <!-- 错误提示 -->
    <div v-if="error" class="error-message">
      <div class="error-icon">⚠️</div>
      <span>{{ error }}</span>
    </div>
    
    <div class="results-header">
      <span class="results-count">
        找到 {{ totalResults }} 条结果 ({{ searchTime }}ms)
      </span>
      <div class="sort-options">
        <label>排序:</label>
        <select v-model="localSortBy" @change="handleSortChange" class="sort-select">
          <option value="relevance">相关性</option>
          <option value="time_desc">时间(新到旧)</option>
          <option value="time_asc">时间(旧到新)</option>
          <option value="message_count">消息数量</option>
        </select>
      </div>
    </div>
    
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <span>搜索中...</span>
    </div>
    
    <div v-else-if="results.length === 0" class="no-results">
      <div class="no-results-icon">🔍</div>
      <p>未找到相关结果</p>
      <p class="no-results-tip">尝试使用不同的关键词或调整筛选条件</p>
    </div>
    
    <div v-else class="results-list">
      <div
        v-for="result in results"
        :key="result.id"
        @click="openConversation(result)"
        class="result-item"
      >
        <div class="result-header">
          <h4 class="result-title" v-html="highlightText(result.title, searchQuery)"></h4>
          <span class="result-time">{{ formatTime(result.createdAt) }}</span>
        </div>
        
        <div class="result-content" v-html="highlightText(result.content, searchQuery)"></div>
        
        <div class="result-meta">
          <span class="result-messages">💬 {{ result.messageCount }} 条消息</span>
          <span v-if="result.models && result.models.length > 0" class="result-models">
            🤖 {{ result.models.join(', ') }}
          </span>
          <span v-if="result.participants && result.participants.length > 0" class="result-participants">
            👥 {{ result.participants.join(', ') }}
          </span>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button
        @click="changePage(currentPage - 1)"
        :disabled="currentPage <= 1"
        class="page-btn"
      >
        ← 上一页
      </button>
      
      <span class="page-info">
        第 {{ currentPage }} 页 / 共 {{ totalPages }} 页
      </span>
      
      <button
        @click="changePage(currentPage + 1)"
        :disabled="currentPage >= totalPages"
        class="page-btn"
      >
        下一页 →
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

// Props
const props = defineProps({
  results: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  totalResults: {
    type: Number,
    default: 0
  },
  totalPages: {
    type: Number,
    default: 0
  },
  currentPage: {
    type: Number,
    default: 1
  },
  searchTime: {
    type: Number,
    default: 0
  },
  searchQuery: {
    type: String,
    default: ''
  },
  sortBy: {
    type: String,
    default: 'relevance'
  }
})

// Emits
const emit = defineEmits(['open-conversation', 'page-change', 'sort-change'])

// 响应式数据
const localSortBy = ref(props.sortBy)

// 监听外部排序变化
watch(() => props.sortBy, (newSortBy) => {
  localSortBy.value = newSortBy
})

// 打开对话
const openConversation = (conversation) => {
  emit('open-conversation', conversation)
}

// 换页
const changePage = (page) => {
  if (page < 1 || page > props.totalPages) return
  emit('page-change', page)
}

// 处理排序变化
const handleSortChange = () => {
  emit('sort-change', localSortBy.value)
}

// 高亮搜索关键词
const highlightText = (text, keyword) => {
  if (!text || !keyword) return text
  
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}

// 格式化时间
const formatTime = (timeString) => {
  const date = new Date(timeString)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.search-results {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 8px;
  color: #c33;
  margin-bottom: 20px;
}

.error-icon {
  font-size: 20px;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e9ecef;
}

.results-count {
  color: #666;
  font-size: 14px;
}

.sort-options {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-options label {
  font-size: 14px;
  color: #666;
}

.sort-select {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  min-height: 44px;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #666;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #f3f3f3;
  border-top: 2px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.no-results {
  text-align: center;
  padding: 60px 20px;
  color: #666;
}

.no-results-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.no-results-tip {
  font-size: 14px;
  color: #999;
}

.results-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px;
}

.result-item {
  padding: 16px;
  margin: 12px 0;
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 80px;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.result-item:hover,
.result-item:active {
  border-color: #007bff;
  box-shadow: 0 2px 8px rgba(0,123,255,0.1);
  transform: translateY(-1px);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.result-title {
  margin: 0;
  font-size: 16px;
  color: #333;
  flex: 1;
}

.result-time {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
  margin-left: 12px;
}

.result-content {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 12px;
  max-height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.result-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #999;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-top: 1px solid #e9ecef;
}

.page-btn {
  padding: 10px 18px;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
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

.page-btn:hover:not(:disabled),
.page-btn:active:not(:disabled) {
  background: #0056b3;
  transform: scale(0.95);
}

.page-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  transform: none;
}

.page-info {
  color: #666;
  font-size: 14px;
}

/* 高亮样式 */
:deep(mark) {
  background: #fff3cd;
  color: #856404;
  padding: 1px 2px;
  border-radius: 2px;
}

/* 移动端响应式设计 */
@media (max-width: 768px) {
  .results-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
    padding: 12px 16px;
  }
  
  .results-count {
    font-size: 13px;
  }
  
  .sort-options {
    width: 100%;
    justify-content: space-between;
  }
  
  .sort-select {
    padding: 6px 10px;
    font-size: 13px;
    min-height: 40px;
  }
  
  .no-results {
    padding: 40px 16px;
  }
  
  .no-results-icon {
    font-size: 40px;
    margin-bottom: 12px;
  }
  
  .results-list {
    padding: 0 16px;
  }
  
  .result-item {
    padding: 12px;
    margin: 10px 0;
    border-radius: 6px;
    min-height: 70px;
  }
  
  .result-header {
    flex-direction: column;
    gap: 6px;
    margin-bottom: 6px;
  }
  
  .result-title {
    font-size: 15px;
  }
  
  .result-time {
    margin-left: 0;
    font-size: 11px;
  }
  
  .result-content {
    font-size: 13px;
    max-height: 50px;
    -webkit-line-clamp: 2;
    margin-bottom: 10px;
  }
  
  .result-meta {
    font-size: 11px;
    gap: 12px;
  }
  
  .pagination {
    padding: 16px;
    gap: 12px;
  }
  
  .page-btn {
    padding: 8px 14px;
    font-size: 13px;
    min-width: 40px;
    min-height: 40px;
  }
  
  .page-info {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .results-header {
    padding: 10px 12px;
    gap: 10px;
  }
  
  .results-count {
    font-size: 12px;
  }
  
  .sort-options label {
    font-size: 12px;
  }
  
  .sort-select {
    padding: 4px 8px;
    font-size: 12px;
    min-height: 36px;
  }
  
  .loading {
    padding: 30px;
  }
  
  .no-results {
    padding: 30px 12px;
  }
  
  .no-results-icon {
    font-size: 36px;
    margin-bottom: 10px;
  }
  
  .no-results p {
    font-size: 14px;
  }
  
  .no-results-tip {
    font-size: 12px;
  }
  
  .results-list {
    padding: 0 12px;
  }
  
  .result-item {
    padding: 10px;
    margin: 8px 0;
    border-radius: 4px;
    min-height: 60px;
  }
  
  .result-header {
    gap: 4px;
    margin-bottom: 4px;
  }
  
  .result-title {
    font-size: 14px;
  }
  
  .result-time {
    font-size: 10px;
  }
  
  .result-content {
    font-size: 12px;
    max-height: 40px;
    -webkit-line-clamp: 2;
    margin-bottom: 8px;
  }
  
  .result-meta {
    font-size: 10px;
    gap: 10px;
  }
  
  .pagination {
    padding: 12px;
    gap: 10px;
  }
  
  .page-btn {
    padding: 6px 12px;
    font-size: 12px;
    min-width: 36px;
    min-height: 36px;
  }
  
  .page-info {
    font-size: 12px;
  }
}
</style>