<template>
  <div class="search-history">
    <div class="history-header">
      <h3 class="history-title">搜索历史</h3>
      <button 
        v-if="historyList.length > 0" 
        @click="clearAllHistory" 
        class="clear-all-btn"
      >
        清空全部
      </button>
    </div>
    
    <div v-if="loading" class="loading-history">
      <div class="loading-spinner"></div>
      <p>正在加载搜索历史...</p>
    </div>
    
    <div v-else-if="historyList.length === 0" class="no-history">
      <div class="no-history-icon">📝</div>
      <p>暂无搜索历史</p>
      <p class="no-history-tip">开始搜索后，历史记录将显示在这里</p>
    </div>
    
    <div v-else class="history-list">
      <div
        v-for="(item, index) in historyList"
        :key="item.id || index"
        @click="selectHistory(item)"
        class="history-item"
      >
        <div class="history-content">
          <div class="history-query">{{ item.query }}</div>
          <div class="history-meta">
            <span class="history-time">{{ formatTime(item.timestamp) }}</span>
            <span v-if="item.resultCount" class="history-results">
              {{ item.resultCount }} 条结果
            </span>
          </div>
        </div>
        <button 
          @click.stop="removeHistory(index)"
          class="remove-btn"
          title="删除此记录"
        >
          ✕
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { SearchApiService } from '../../services/searchApi.js'

const props = defineProps({
  maxHistory: {
    type: Number,
    default: 20
  }
})

const emit = defineEmits(['history-selected', 'history-updated'])

const historyList = ref([])
const loading = ref(false)

onMounted(() => {
  loadHistory()
})

// 从后端API加载搜索历史
const loadHistory = async () => {
  loading.value = true
  try {
    console.log('正在从后端加载搜索历史...')
    const response = await SearchApiService.getSearchHistory(props.maxHistory)
    console.log('后端返回的搜索历史数据:', response)
    
    // 将后端返回的ConversationDocument转换为前端需要的格式
    if (Array.isArray(response)) {
      historyList.value = response.map(conversation => ({
        id: conversation.id,
        query: conversation.title || '未命名对话', // 使用对话标题作为查询内容
        timestamp: conversation.createdAt || conversation.timestamp,
        resultCount: conversation.messageCount || 0
      }))
    } else {
      console.warn('后端返回的搜索历史格式不正确:', response)
      historyList.value = []
    }
  } catch (error) {
    console.error('从后端加载搜索历史失败:', error)
    // 如果后端加载失败，尝试从localStorage加载作为备用
    loadHistoryFromLocalStorage()
  } finally {
    loading.value = false
  }
}

// 从localStorage加载搜索历史（备用方案）
const loadHistoryFromLocalStorage = () => {
  try {
    const STORAGE_KEY = 'ai_agent_search_history'
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      historyList.value = JSON.parse(stored)
      console.log('从localStorage加载搜索历史作为备用')
    }
  } catch (error) {
    console.error('从localStorage加载搜索历史失败:', error)
    historyList.value = []
  }
}

// 刷新搜索历史（供外部组件调用）
const refresh = async () => {
  await loadHistory()
  emit('history-updated', historyList.value)
}

// 添加搜索历史（现在由后端管理，这里只是触发刷新）
const addHistory = async (query, resultCount = 0) => {
  if (!query || !query.trim()) return
  
  // 搜索历史现在由后端在recordSearchHistory时自动管理
  // 这里只需要刷新显示
  await refresh()
}

const selectHistory = (item) => {
  console.log('点击搜索历史项:', item.query)
  emit('history-selected', item.query)
}

const removeHistory = async (index) => {
  // 注意：后端目前没有提供删除单个搜索历史的API
  // 这里暂时只从前端列表中移除，实际删除需要后端支持
  historyList.value.splice(index, 1)
  emit('history-updated', historyList.value)
  console.warn('注意：单个搜索历史删除功能需要后端API支持')
}

const clearAllHistory = async () => {
  if (confirm('确定要清空所有搜索历史吗？')) {
    try {
      // 调用后端清空搜索历史API
      await SearchApiService.clearSearchHistory()
      historyList.value = []
      emit('history-updated', historyList.value)
      console.log('成功清空所有搜索历史')
    } catch (error) {
      console.error('清空搜索历史失败:', error)
      alert('清空搜索历史失败，请稍后重试')
    }
  }
}

const formatTime = (timeString) => {
  const date = new Date(timeString)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) {
    return '刚刚'
  }
  
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  }
  
  if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  }
  
  if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  }
  
  return date.toLocaleDateString('zh-CN')
}

defineExpose({
  addHistory,
  clearAllHistory,
  refresh,
  historyList
})
</script>

<style scoped>
.search-history {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e9ecef;
}

.history-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.clear-all-btn {
  padding: 8px 14px;
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
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

.clear-all-btn:hover,
.clear-all-btn:active {
  background: #c82333;
  transform: scale(0.95);
}

.loading-history {
  text-align: center;
  padding: 40px 20px;
  color: #666;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.no-history {
  text-align: center;
  padding: 40px 20px;
  color: #666;
}

.no-history-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.no-history p {
  margin: 8px 0;
}

.no-history-tip {
  font-size: 14px;
  color: #999;
}

.history-list {
  /* 移除滚动条，显示所有搜索历史 */
  overflow: visible;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 1px solid #f0f0f0;
  min-height: 60px;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.history-item:hover,
.history-item:active {
  background: #f8f9fa;
  transform: translateX(2px);
}

.history-item:last-child {
  border-bottom: none;
}

.history-content {
  flex: 1;
  min-width: 0;
}

.history-query {
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  word-break: break-word;
}

.history-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #666;
}

.history-time {
  color: #999;
}

.history-results {
  color: #007bff;
}

.remove-btn {
  padding: 6px 10px;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
  font-size: 14px;
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

.remove-btn:hover,
.remove-btn:active {
  background: #fee;
  color: #dc3545;
  transform: scale(0.95);
}
/* 移动端响应式设计 */
@media (max-width: 768px) {
  .search-history {
    padding: 16px;
    border-radius: 6px;
  }
  
  .history-header {
    margin-bottom: 12px;
    padding-bottom: 10px;
  }
  
  .history-title {
    font-size: 15px;
  }
  
  .clear-all-btn {
    padding: 6px 12px;
    font-size: 11px;
    min-width: 40px;
    min-height: 40px;
  }
  
  .no-history {
    padding: 30px 16px;
  }
  
  .no-history-icon {
    font-size: 40px;
    margin-bottom: 12px;
  }
  
  .history-list {
    /* 移动端也移除滚动条 */
    overflow: visible;
  }
  
  .history-item {
    padding: 10px;
    min-height: 56px;
  }
  
  .history-query {
    font-size: 14px;
  }
  
  .history-meta {
    font-size: 11px;
    gap: 10px;
  }
  
  .remove-btn {
    padding: 4px 8px;
    font-size: 12px;
    min-width: 40px;
    min-height: 40px;
  }
}

@media (max-width: 480px) {
  .search-history {
    padding: 12px;
    border-radius: 4px;
  }
  
  .history-header {
    margin-bottom: 10px;
    padding-bottom: 8px;
  }
  
  .history-title {
    font-size: 14px;
  }
  
  .clear-all-btn {
    padding: 4px 10px;
    font-size: 10px;
    min-width: 36px;
    min-height: 36px;
  }
  
  .no-history {
    padding: 24px 12px;
  }
  
  .no-history-icon {
    font-size: 36px;
    margin-bottom: 10px;
  }
  
  .no-history p {
    font-size: 14px;
  }
  
  .no-history-tip {
    font-size: 12px;
  }
  
  .history-list {
    /* 移动端也移除滚动条 */
    overflow: visible;
  }
  
  .history-item {
    padding: 8px;
    min-height: 52px;
  }
  
  .history-query {
    font-size: 13px;
  }
  
  .history-meta {
    font-size: 10px;
    gap: 8px;
  }
  
  .remove-btn {
    padding: 2px 6px;
    font-size: 11px;
    min-width: 36px;
    min-height: 36px;
  }
}
</style>