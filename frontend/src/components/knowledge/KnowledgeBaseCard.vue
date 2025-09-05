<template>
  <div class="kb-card card" :class="{ selected: isSelected }">
    <div class="card-header-custom">
      <div class="kb-info">
        <h3 class="kb-name">{{ knowledgeBase.name }}</h3>
        <p class="kb-description">{{ knowledgeBase.description || '暂无描述' }}</p>
      </div>
      <div class="kb-actions">
        <button 
          class="btn-base btn-sm btn-icon"
          @click.stop="$emit('edit', knowledgeBase)"
          title="编辑知识库"
        >
          ✏️
        </button>
        <button 
          class="btn-base btn-sm btn-icon"
          @click.stop="$emit('delete', knowledgeBase)"
          title="删除知识库"
        >
          🗑️
        </button>
      </div>
    </div>
    
    <div class="card-body">
      <div class="kb-stats">
        <div class="stat-item">
          <span class="stat-label">文档数量:</span>
          <span class="stat-value">{{ knowledgeBase.documentCount || 0 }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">创建时间:</span>
          <span class="stat-value">{{ formatDate(knowledgeBase.createdAt) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">权限:</span>
          <span class="tag" :class="knowledgeBase.isPublic ? 'success' : 'primary'">
            {{ knowledgeBase.isPublic ? '公开' : '私有' }}
          </span>
        </div>
      </div>
    </div>
    
    <div class="card-footer">
      <button 
        class="btn-base btn-outline btn-sm"
        @click="$emit('select', knowledgeBase)"
      >
        查看详情
      </button>
      <button 
        class="btn-base btn-success btn-sm"
        @click="$emit('uploadDocument', knowledgeBase.id)"
      >
        📤 上传文档
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// Props
const props = defineProps({
  knowledgeBase: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['select', 'edit', 'delete', 'uploadDocument'])

// 计算属性
const isSelected = computed(() => props.selected)

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '未知'
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  } catch {
    return '未知'
  }
}
</script>

<style scoped>
.kb-card {
  cursor: pointer;
  transition: var(--transition-base);
  border: 2px solid transparent;
}

.kb-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-color);
}

.kb-card.selected {
  border-color: var(--primary-color);
  background: rgba(24, 144, 255, 0.05);
}

.card-header-custom {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: var(--spacing-lg) var(--spacing-lg) 0;
  margin-bottom: var(--spacing-md);
}

.kb-info {
  flex: 1;
}

.kb-name {
  margin: 0 0 var(--spacing-sm) 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.kb-description {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.4;
}

.kb-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.kb-stats {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.stat-value {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
}

.card-footer {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: space-between;
}

.card-footer .btn-base {
  flex: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .card-header-custom {
    flex-direction: column;
    gap: var(--spacing-md);
  }
  
  .kb-actions {
    align-self: stretch;
    justify-content: flex-end;
  }
  
  .card-footer {
    flex-direction: column;
  }
}
</style>