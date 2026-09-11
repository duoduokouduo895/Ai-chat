<script setup>
import CleanupTasks from '@/components/common/CleanupTasks.vue'
import { onMounted, reactive, ref } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getAdminKnowledgeBasePage } from '@/api/knowledge'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const knowledgeBases = ref([])
const total = ref(0)
const summary = reactive({
  knowledgeBaseCount: 0,
  documentCount: 0,
  publicKnowledgeBaseCount: 0,
})
const filters = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

async function fetchKnowledgeBases() {
  loading.value = true

  try {
    const payload = await getAdminKnowledgeBasePage({
      page: filters.page - 1,
      size: filters.size,
      keyword: filters.keyword || undefined,
    })

    knowledgeBases.value = payload?.items || []
    total.value = payload?.total || 0
    summary.knowledgeBaseCount = payload?.summary?.knowledgeBaseCount || 0
    summary.documentCount = payload?.summary?.documentCount || 0
    summary.publicKnowledgeBaseCount = payload?.summary?.publicKnowledgeBaseCount || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  fetchKnowledgeBases()
}

function handleReset() {
  filters.keyword = ''
  filters.page = 1
  fetchKnowledgeBases()
}

onMounted(() => {
  fetchKnowledgeBases()
})
</script>

<template>
  <div class="space-y-6">
    <CleanupTasks />
    <PageHeader title="知识库管理" description="当前已接入知识库列表接口，用于展示基础资源概览。" />

    <div class="grid grid-cols-1 gap-6 md:grid-cols-3">
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">知识库总数</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.knowledgeBaseCount }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">文档总量</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.documentCount }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">公开知识库</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">
          {{ summary.publicKnowledgeBaseCount }}
        </p>
      </div>
    </div>

    <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div class="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索知识库名称或描述"
          @keyup.enter="handleSearch"
        />
        <div class="flex gap-3">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </div>

    <LoadingBlock v-if="loading" text="知识库列表加载中..." />

    <EmptyState
      v-else-if="!knowledgeBases.length"
      title="暂无知识库"
      description="当前全站还没有可展示的知识库数据。"
    />

    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-900">Knowledge Bases</h3>
        <p class="mt-1 text-sm text-slate-500">展示知识库的基础信息、文档数与状态。</p>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
            <tr>
              <th class="px-6 py-4">名称</th>
              <th class="px-6 py-4">状态</th>
              <th class="px-6 py-4">文档数</th>
              <th class="px-6 py-4">Embedding Model</th>
              <th class="px-6 py-4">更新时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in knowledgeBases" :key="item.id" class="hover:bg-slate-50">
              <td class="px-6 py-4">
                <div>
                  <p class="text-sm font-semibold text-slate-900">{{ item.name }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ item.description || '暂无描述' }}</p>
                </div>
              </td>
              <td class="px-6 py-4">
                <span
                  class="rounded-full px-2.5 py-1 text-[10px] font-bold"
                  :class="
                    item.status === 'ACTIVE'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-slate-100 text-slate-600'
                  "
                >
                  {{ item.status || 'UNKNOWN' }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-slate-600">{{ item.documentCount ?? 0 }}</td>
              <td class="px-6 py-4 text-sm text-slate-600">{{ item.embeddingModel || '-' }}</td>
              <td class="px-6 py-4 text-sm text-slate-400">{{ formatDateTime(item.updatedAt || item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex justify-end border-t border-slate-100 px-6 py-4">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="fetchKnowledgeBases"
          @size-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>
