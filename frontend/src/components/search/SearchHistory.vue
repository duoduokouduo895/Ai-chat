<template>
  <div class="search-history">
    <div class="history-header">
      <h3 class="history-title">搜索历史</h3>
      <div class="header-actions">
        <button 
          v-if="historyList.length > 0" 
          @click="clearAllHistory" 
          class="clear-all-btn"
          title="清空所有搜索历史"
        >
          清空全部
        </button>
      </div>
    </div>

    <StateBlock
      v-if="historyError"
      type="error"
      title="搜索历史加载失败"
      :message="historyError"
      actionText="重新加载"
      @action="loadHistory"
    />
    
    <StateBlock
      v-else-if="loading"
      type="loading"
      title="正在加载搜索历史"
      message="请稍候，正在同步你的搜索记录。"
    />
    
    <StateBlock
      v-else-if="historyList.length === 0"
      type="empty"
      title="暂无搜索历史"
      message="开始搜索后，历史记录将显示在这里。"
    />
    
    <div v-else class="history-list">
      <div
        v-for="(item, index) in historyList"
        :key="item.id || index"
        class="history-item"
      >
        <button class="history-select" type="button" @click="selectHistory(item)">
          <div class="history-content">
            <div class="history-query">{{ item.query }}</div>
            <div class="history-meta">
              <span class="history-time">{{ formatTime(item.timestamp) }}</span>
              <span v-if="item.resultCount" class="history-results">
                {{ item.resultCount }} 条结果
              </span>
            </div>
          </div>
        </button>
        <button 
          @click="removeHistory(index)"
          class="remove-btn"
          title="删除此记录"
          :aria-label="`删除搜索记录 ${item.query}`"
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
import StateBlock from '../common/StateBlock.vue'

const props = defineProps({
  maxHistory: {
    type: Number,
    default: 20
  }
})

const emit = defineEmits(['history-selected', 'history-updated'])

const historyList = ref([])
const loading = ref(false)
const historyError = ref('')

onMounted(() => {
  loadHistory()
})

// 从后端API加载搜索历史
const loadHistory = async () => {
  loading.value = true
  historyError.value = ''
  try {
    const response = await SearchApiService.getSearchHistory(props.maxHistory)
    
    // 将后端返回的ConversationDocument转换为前端需要的格式
    if (Array.isArray(response)) {
      // 前端再次去重，防止后端去重失败
      const uniqueHistories = new Map()
      
      response.forEach(conversation => {
        const query = conversation.title || '未命名对话'
        const timestamp = conversation.updatedAt || conversation.createdAt || conversation.timestamp
        
        // 如果已存在相同查询，比较时间戳，保留更新的
        if (uniqueHistories.has(query)) {
          const existing = uniqueHistories.get(query)
          const existingTime = new Date(existing.timestamp)
          const currentTime = new Date(timestamp)
          if (currentTime > existingTime) {
            uniqueHistories.set(query, {
              id: conversation.id,
              query: query,
              timestamp: timestamp,
              resultCount: conversation.messageCount || 0
            })
          }
        } else {
          uniqueHistories.set(query, {
            id: conversation.id,
            query: query,
            timestamp: timestamp,
            resultCount: conversation.messageCount || 0
          })
        }
      })
      
      // 转换为数组并按时间排序
      historyList.value = Array.from(uniqueHistories.values())
        .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
      
    } else {
      historyList.value = []
    }
  } catch (error) {
    // 如果后端加载失败，尝试从localStorage加载作为备用
    loadHistoryFromLocalStorage()
    if (historyList.value.length === 0) {
      historyError.value = error.response?.data?.message || error.message || '搜索历史加载失败，请稍后重试'
    }
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
    }
  } catch (error) {
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
  emit('history-selected', item.query)
}

const removeHistory = async (index) => {
  // 注意：后端目前没有提供删除单个搜索历史的API
  // 这里暂时只从前端列表中移除，实际删除需要后端支持
  historyList.value.splice(index, 1)
  emit('history-updated', historyList.value)
}

const clearAllHistory = async () => {
  if (confirm('确定要清空所有搜索历史吗？')) {
    try {
      historyError.value = ''
      // 调用后端清空搜索历史API
      await SearchApiService.clearSearchHistory()
      historyList.value = []
      emit('history-updated', historyList.value)
    } catch (error) {
      historyError.value = error.response?.data?.message || error.message || '清空搜索历史失败，请稍后重试'
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
