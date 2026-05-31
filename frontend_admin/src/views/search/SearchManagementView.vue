<script setup>
import { onMounted, reactive, ref } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  getAdminSearchHistory,
  getAdminSearchOverview,
  indexAllConversations,
  rebuildSearchIndexes,
} from '@/api/search'
import { formatDateTime } from '@/utils/format'
import { showSuccessMessage } from '@/utils/message'

const loading = ref(false)
const actionLoading = ref(false)
const historyItems = ref([])
const total = ref(0)
const overview = reactive({
  indexStatus: '-',
  totalHistoryCount: 0,
  todayHistoryCount: 0,
})
const filters = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

function formatIndexStatus(status) {
  if (!status) {
    return '-'
  }

  if (status.includes('已存在')) {
    return '索引已建立，可正常使用'
  }

  if (status.includes('不存在')) {
    return '索引未建立'
  }

  return status
}

async function fetchOverview() {
  const payload = await getAdminSearchOverview()
  overview.indexStatus = formatIndexStatus(payload?.indexStatus)
  overview.totalHistoryCount = payload?.totalHistoryCount || 0
  overview.todayHistoryCount = payload?.todayHistoryCount || 0
}

async function fetchHistory() {
  loading.value = true

  try {
    const payload = await getAdminSearchHistory({
      page: filters.page - 1,
      size: filters.size,
      keyword: filters.keyword || undefined,
    })

    historyItems.value = payload?.items || []
    total.value = payload?.total || 0
  } finally {
    loading.value = false
  }
}

async function refreshPageData() {
  await Promise.all([fetchOverview(), fetchHistory()])
}

async function handleRebuild() {
  actionLoading.value = true

  try {
    const payload = await rebuildSearchIndexes()
    showSuccessMessage(payload?.message || '索引重建完成')
    await refreshPageData()
  } finally {
    actionLoading.value = false
  }
}

async function handleIndexAll() {
  actionLoading.value = true

  try {
    const payload = await indexAllConversations()
    showSuccessMessage(payload?.message || '对话索引完成')
    await refreshPageData()
  } finally {
    actionLoading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  fetchHistory()
}

function handleReset() {
  filters.keyword = ''
  filters.page = 1
  fetchHistory()
}

onMounted(() => {
  refreshPageData()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="搜索管理" description="查看索引状态、全站搜索历史，并执行搜索运维操作。" />

    <div class="grid grid-cols-1 gap-6 md:grid-cols-3">
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">索引状态</p>
        <p class="mt-2 text-lg font-bold text-slate-900">{{ overview.indexStatus }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">历史总量</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ overview.totalHistoryCount }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">今日搜索量</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ overview.todayHistoryCount }}</p>
      </div>
    </div>

    <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div class="grid flex-1 grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索关键词内容"
            @keyup.enter="handleSearch"
          />
          <div class="flex gap-3">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </div>

        <div class="flex flex-wrap gap-3">
          <el-button :loading="actionLoading" @click="handleIndexAll">索引全部会话</el-button>
          <el-button type="primary" :loading="actionLoading" @click="handleRebuild">重建搜索索引</el-button>
        </div>
      </div>
    </div>

    <LoadingBlock v-if="loading" text="搜索历史加载中..." />

    <EmptyState
      v-else-if="!historyItems.length"
      title="暂无搜索历史"
      description="当前筛选条件下没有可展示的搜索记录。"
    />

    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
            <tr>
              <th class="px-6 py-4">搜索词</th>
              <th class="px-6 py-4">用户</th>
              <th class="px-6 py-4">类型</th>
              <th class="px-6 py-4">搜索时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in historyItems" :key="item.id" class="hover:bg-slate-50">
              <td class="px-6 py-4">
                <p class="text-sm font-semibold text-slate-900">{{ item.query }}</p>
                <p class="mt-1 text-xs text-slate-400">History ID: {{ item.id }}</p>
              </td>
              <td class="px-6 py-4">
                <p class="text-sm text-slate-700">{{ item.username }}</p>
                <p class="mt-1 text-xs text-slate-400">User ID: {{ item.userId }}</p>
              </td>
              <td class="px-6 py-4">
                <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-600">
                  {{ item.type || 'GENERAL' }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-slate-400">{{ formatDateTime(item.timestamp) }}</td>
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
          @current-change="fetchHistory"
          @size-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>
