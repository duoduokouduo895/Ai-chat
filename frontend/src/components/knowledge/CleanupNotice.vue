<template>
  <div class="cleanup-notice" role="status" aria-live="polite">
    <span>{{ message }}</span>
    <button v-if="operation.operationId && current.status !== 'COMPLETED'" type="button"
      class="btn-base btn-outline btn-sm" :disabled="loading" @click="refresh">
      {{ loading ? '正在查询…' : '查看清理进度' }}
    </button>
    <span v-if="error">暂时无法查询进度，请稍后重试。</span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { knowledgeApiService } from '@/services/knowledgeApi.js';
const props = defineProps({ operation: { type: Object, required: true } });
const current = ref(props.operation);
const loading = ref(false);
const error = ref(false);
watch(() => props.operation, value => { current.value = value; error.value = false; });
const message = computed(() => current.value.status === 'COMPLETED'
  ? '已移除，后台清理已完成。'
  : current.value.status === 'NEEDS_ATTENTION'
    ? '已移除，后台清理暂未完成，管理员可重试。'
    : '已移除，后台清理中。');
async function refresh() {
  const id = props.operation.operationId;
  loading.value = true;
  error.value = false;
  try {
    const response = await knowledgeApiService.getCleanupOperation(id);
    if (props.operation.operationId === id) current.value = response.data;
  } catch {
    if (props.operation.operationId === id) error.value = true;
  } finally { loading.value = false; }
}
</script>

<style scoped>
.cleanup-notice {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  color: var(--text-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--border-subtle);
}
</style>
