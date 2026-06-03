<template>
  <div class="search-results">
    <StateBlock
      v-if="error"
      type="error"
      title="搜索失败"
      :message="error"
      actionText="重试"
      @action="$emit('retry-search')"
    />

    <StateBlock
      v-else-if="loading"
      type="loading"
      title="正在搜索"
      message="正在检索相关对话，请稍候。"
    />

    <StateBlock
      v-else-if="results.length === 0"
      type="empty"
      title="未找到相关结果"
      message="尝试使用不同的关键词，或调整筛选条件后再试。"
    />

    <div v-else class="results-header">
      <span class="results-count">
        找到 {{ totalResults }} 条结果 ({{ searchTime }}ms)
      </span>
      <div class="sort-options">
        <label for="search-result-sort">排序:</label>
        <select id="search-result-sort" v-model="localSortBy" @change="handleSortChange" class="sort-select">
          <option value="relevance">相关性</option>
          <option value="time_desc">时间(新到旧)</option>
          <option value="time_asc">时间(旧到新)</option>
          <option value="message_count">消息数量</option>
        </select>
      </div>
    </div>

    <div v-if="!loading && !error && results.length > 0" class="results-list">
      <button
        v-for="result in results"
        :key="result.id"
        type="button"
        @click="openConversation(result)"
        class="result-item"
      >
        <div class="result-header">
          <h4 class="result-title">
            <template
              v-for="(part, index) in highlightText(result.title, searchQuery)"
              :key="`title-${result.id}-${index}`"
            >
              <mark v-if="part.highlight">{{ part.text }}</mark>
              <span v-else>{{ part.text }}</span>
            </template>
          </h4>
          <span class="result-time">{{ formatTime(result.createdAt) }}</span>
        </div>
        
        <div class="result-content">
          <template
            v-for="(part, index) in highlightText(result.content, searchQuery)"
            :key="`content-${result.id}-${index}`"
          >
            <mark v-if="part.highlight">{{ part.text }}</mark>
            <span v-else>{{ part.text }}</span>
          </template>
        </div>
        
        <div class="result-meta">
          <span class="result-messages">{{ result.messageCount }} 条消息</span>
          <span v-if="result.models && result.models.length > 0" class="result-models">
            {{ result.models.join(', ') }}
          </span>
          <span v-if="result.participants && result.participants.length > 0" class="result-participants">
            {{ result.participants.join(', ') }}
          </span>
        </div>
      </button>
    </div>
    
    <!-- 分页 -->
    <div v-if="!loading && !error && totalPages > 1" class="pagination">
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
import StateBlock from '../common/StateBlock.vue'
import { splitHighlightText } from '@/utils/searchHighlight.mjs'

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
const emit = defineEmits(['open-conversation', 'page-change', 'sort-change', 'retry-search'])

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
const highlightText = splitHighlightText

// 格式化时间
const formatTime = (timeString) => {
  const date = new Date(timeString)
  return date.toLocaleString('zh-CN')
}
</script>
