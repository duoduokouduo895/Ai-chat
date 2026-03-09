<template>
  <div class="knowledge-selector-compact">
    <el-select
      v-model="selectedKnowledgeBaseIds"
      multiple
      placeholder="选择知识库"
      style="width: 200px"
      size="small"
      @change="handleSelectionChange"
    >
      <template #empty>
        <div style="padding: 20px; text-align: center; color: #999;">
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
import { ElMessage } from 'element-plus';
import { knowledgeApiService } from '@/services/knowledgeApi';

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
      isGuest: localStorage.getItem('isGuest') === 'true'
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
        const timestamp = Date.now();
        const response = await knowledgeApiService.getKnowledgeBases({ _t: timestamp });
        this.knowledgeBases = response.data || [];
      } catch (error) {
        console.error('Failed to load knowledge bases:', error);
        ElMessage.error('加载知识库列表失败');
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
  color: #999;
}
</style>
