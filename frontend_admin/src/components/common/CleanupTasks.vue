<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFailedCleanupOperations, retryCleanupOperation } from '@/api/knowledge'
const tasks = ref([])
const loading = ref(false)
const retrying = ref(null)
const error = ref('')
async function refresh() {
  loading.value = true
  error.value = ''
  try { tasks.value = await getFailedCleanupOperations() || [] }
  catch { error.value = '清理任务暂时无法加载，请稍后重试。' }
  finally { loading.value = false }
}
async function retry(task) {
  if (retrying.value) return
  retrying.value = task.operationId
  try {
    const operation = await retryCleanupOperation(task.operationId)
    ElMessage.success(operation.status === 'COMPLETED' ? '清理已完成' : '已受理重试，资源继续保持隐藏')
    await refresh()
  } catch { ElMessage.error('重试未受理，请稍后再试') }
  finally { retrying.value = null }
}
onMounted(refresh)
</script>

<template>
  <section class="rounded-2xl border border-slate-200 bg-white p-5" aria-labelledby="cleanup-tasks-title">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 id="cleanup-tasks-title" class="font-semibold text-slate-900">后台清理</h2>
        <p class="mt-1 text-sm text-slate-500">已移除资源保持隐藏；以下任务需要人工重试。</p>
      </div>
      <el-button :loading="loading" @click="refresh">刷新任务</el-button>
    </div>
    <p v-if="error" class="mt-3 text-sm text-red-700" role="alert">{{ error }}</p>
    <p v-else-if="!loading && !tasks.length" class="mt-3 text-sm text-slate-500" role="status">暂无需要处理的清理任务。</p>
    <ul v-else class="mt-3 divide-y divide-slate-100">
      <li v-for="task in tasks" :key="task.operationId" class="flex flex-wrap items-center justify-between gap-3 py-3">
        <div class="min-w-0">
          <p class="text-sm font-medium text-slate-800">清理暂未完成</p>
          <p class="break-all text-xs text-slate-500">操作编号：{{ task.operationId }}</p>
          <p class="text-xs text-slate-500">已尝试 {{ task.attempt }} 次 · {{ task.errorCode }}</p>
        </div>
        <el-button :loading="retrying === task.operationId" :disabled="!!retrying && retrying !== task.operationId" @click="retry(task)">重试清理</el-button>
      </li>
    </ul>
  </section>
</template>
