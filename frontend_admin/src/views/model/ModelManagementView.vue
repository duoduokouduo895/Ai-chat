<script setup>
import { onMounted, ref } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getModelList } from '@/api/model'

const loading = ref(false)
const modelList = ref([])

async function fetchModelList() {
  loading.value = true

  try {
    modelList.value = await getModelList()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchModelList()
})
</script>

<template>
  <div>
    <PageHeader title="模型管理" description="当前接入统一模型元数据接口 /api/ai/models，展示可用模型列表。" />

    <LoadingBlock v-if="loading" text="模型列表加载中..." />

    <EmptyState
      v-else-if="!modelList.length"
      title="暂无模型数据"
      description="后端当前没有返回可用模型。"
    />

    <div v-else class="grid grid-cols-1 gap-6 xl:grid-cols-2">
      <article
        v-for="model in modelList"
        :key="model.value"
        class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--color-primary)]">
              {{ model.speed || 'unknown' }}
            </p>
            <h3 class="mt-2 text-xl font-bold text-slate-900">{{ model.label }}</h3>
            <p class="mt-3 text-sm leading-6 text-slate-500">{{ model.description }}</p>
          </div>
          <div class="rounded-xl bg-[var(--color-primary)]/10 p-3 text-[var(--color-primary)]">
            <span class="material-symbols-outlined">deployed_code</span>
          </div>
        </div>

        <div class="mt-6 rounded-xl bg-slate-50 p-4">
          <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Model ID</p>
          <p class="mt-2 break-all text-sm font-medium text-slate-700">{{ model.value }}</p>
        </div>
      </article>
    </div>
  </div>
</template>
