<template>
  <div class="knowledge-selector-compact">
    <el-select
      v-model="selectedKnowledgeBaseIds"
      multiple
      placeholder="选择知识库"
      aria-label="选择知识库"
      class="knowledge-select"
      popper-class="knowledge-select-popper"
      size="small"
      @change="handleSelectionChange"
    >
      <template #empty>
        <div class="knowledge-empty">
          {{ isGuest ? '登录后可用' : '暂无数据' }}
        </div>
      </template>
      <el-option
        v-for="kb in knowledgeBases"
        :key="kb.id"
        :label="kb.name"
        :value="kb.id"
      >
        <div class="knowledge-option">
          <span class="kb-name">{{ kb.name }}</span>
          <span class="kb-info">{{ kb.documentCount || 0 }} 个文档</span>
        </div>
      </el-option>
    </el-select>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus/es/components/message/index';
import { knowledgeApiService } from '@/services/knowledgeApi';
import { getApiErrorMessage } from '@/services/apiErrorService';
import { isGuest } from '@/services/sessionStore.mjs';

export default {
  name: 'KnowledgeSelector',
  props: {
    modelValue: {
      type: Array,
      default: () => []
    }
  },
  emits: ['update:modelValue', 'mode-change'],
  data() {
    return {
      selectedKnowledgeBaseIds: [],
      knowledgeBases: [],
      loading: false,
      isGuest: isGuest()
    };
  },
  watch: {
    modelValue: {
      handler(newVal) {
        this.selectedKnowledgeBaseIds = newVal || [];
      },
      immediate: true
    }
  },
  async mounted() {
    await this.loadKnowledgeBases();
  },
  methods: {
    async loadKnowledgeBases() {
      try {
        this.loading = true;
        const response = await knowledgeApiService.getKnowledgeBases();
        this.knowledgeBases = response.data || [];
      } catch (error) {
        ElMessage.error(getApiErrorMessage(error, '加载知识库列表失败'));
      } finally {
        this.loading = false;
      }
    },

    handleModeChange() {
      const mode = this.selectedKnowledgeBaseIds.length > 0 ? 'rag' : 'normal';
      this.$emit('mode-change', mode);
    },

    handleSelectionChange() {
      this.emitChange();
      this.handleModeChange();
    },

    removeKnowledgeBase(kbId) {
      this.selectedKnowledgeBaseIds = this.selectedKnowledgeBaseIds.filter(id => id !== kbId);
      this.emitChange();
      this.handleModeChange();
    },

    emitChange() {
      this.$emit('update:modelValue', this.selectedKnowledgeBaseIds);
    },

    getKnowledgeBaseName(kbId) {
      const kb = this.knowledgeBases.find(item => item.id === kbId);
      return kb ? kb.name : `知识库 ${kbId}`;
    }
  }
};
</script>

<style scoped>
.knowledge-selector-compact {
  display: flex;
  align-items: center;
}

.knowledge-select {
  width: 174px;
}

.knowledge-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.kb-name {
  font-weight: 500;
}

.kb-info {
  font-size: 12px;
  color: var(--text-muted);
}

.knowledge-empty {
  color: var(--text-muted);
  padding: 20px;
  text-align: center;
}

:deep(.el-select__wrapper) {
  background: oklch(0.185 0.014 285);
  border-radius: var(--border-radius-md);
  box-shadow: 0 0 0 1px var(--border-subtle) inset;
  min-height: 34px;
  padding: 3px 8px;
  transition: background var(--transition-fast), box-shadow var(--transition-fast);
}

:deep(.el-select__wrapper:hover),
:deep(.el-select__wrapper.is-focused) {
  background: var(--surface-3);
  box-shadow: 0 0 0 1px var(--accent-color) inset;
}

:deep(.el-select__placeholder),
:deep(.el-select__selected-item) {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

:deep(.el-select__input) {
  color: var(--text-primary);
  font-size: var(--font-size-xs);
}

:deep(.el-select__caret) {
  color: var(--text-muted);
}

:deep(.el-select__tags) {
  gap: 4px;
}

:deep(.el-tag) {
  align-items: center;
  background: oklch(0.28 0.045 285);
  border: 1px solid oklch(0.43 0.055 285);
  border-radius: 6px;
  color: var(--text-primary);
  display: inline-flex;
  height: 22px;
  margin: 0;
  max-width: 112px;
  padding: 0 6px;
}

:deep(.el-tag__content) {
  color: var(--text-primary);
  font-size: var(--font-size-xs);
  max-width: 78px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.el-tag__close) {
  color: var(--text-muted);
}

:deep(.el-tag__close:hover) {
  background: oklch(0.38 0.07 285);
  color: var(--text-white);
}

:global(.knowledge-select-popper.el-popper) {
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

:global(.knowledge-select-popper .el-popper__arrow::before) {
  background: var(--surface-2);
  border-color: var(--border-subtle);
}

:global(.knowledge-select-popper .el-select-dropdown) {
  background: transparent;
}

:global(.knowledge-select-popper .el-select-dropdown__list) {
  padding: 6px;
}

:global(.knowledge-select-popper .el-select-dropdown__item) {
  border-radius: var(--border-radius-md);
  color: var(--text-secondary);
  height: 36px;
  padding: 0 10px;
}

:global(.knowledge-select-popper .el-select-dropdown__item.is-hovering),
:global(.knowledge-select-popper .el-select-dropdown__item:hover) {
  background: var(--surface-3);
  color: var(--text-primary);
}

:global(.knowledge-select-popper .el-select-dropdown__item.is-selected) {
  background: oklch(0.28 0.055 285);
  color: var(--text-primary);
  font-weight: 600;
}

:global(.knowledge-select-popper .kb-name) {
  color: var(--text-primary);
}

:global(.knowledge-select-popper .kb-info) {
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .knowledge-selector-compact {
    width: 100%;
  }

  .knowledge-select {
    width: 100%;
  }
}
</style>
