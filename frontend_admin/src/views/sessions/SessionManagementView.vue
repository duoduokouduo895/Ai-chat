<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { deleteConversationAsAdmin, getAdminConversationPage } from '@/api/conversation'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const deletingId = ref(null)
const sessions = ref([])
const total = ref(0)
const pager = reactive({
  page: 1,
  pageSize: 10,
})
const filters = reactive({
  keyword: '',
})

function handleSearch() {
  pager.page = 1
  fetchSessions()
}

function handleReset() {
  filters.keyword = ''
  pager.page = 1
  fetchSessions()
}

async function fetchSessions() {
  loading.value = true

  try {
    const payload = await getAdminConversationPage({
      page: pager.page - 1,
      size: pager.pageSize,
      keyword: filters.keyword || undefined,
    })
    sessions.value = payload?.items || []
    total.value = payload?.total || 0
  } finally {
    loading.value = false
  }
}

async function handleDelete(session) {
  await ElMessageBox.confirm(
    `确认删除会话 ID ${session.id} 吗？该操作会删除该会话及其关联消息。`,
    '删除会话',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    },
  )

  deletingId.value = session.id

  try {
    await deleteConversationAsAdmin(session.id)
    ElMessage.success('会话删除成功')
    await fetchSessions()
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  fetchSessions()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="会话管理" description="聚焦会话 ID、绑定用户 ID 和删除操作，便于后台清理数据。" />

    <div class="grid grid-cols-1 gap-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:grid-cols-3">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索会话ID、用户ID或标题"
        clearable
        @keyup.enter="handleSearch"
      />
      <div class="flex items-center text-sm text-slate-500">
        当前会话总数：{{ total }} 条
      </div>
      <div class="flex items-center justify-end gap-3">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <LoadingBlock v-if="loading" text="会话列表加载中..." />

    <EmptyState
      v-else-if="!sessions.length"
      title="暂无可管理会话"
      description="当前接口没有返回可展示的会话数据。"
    />

    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-900">Conversation Records</h3>
        <p class="mt-1 text-sm text-slate-500">
          当前直接使用管理员接口展示全站会话记录，重点保留 userId 和 conversationId。
        </p>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
            <tr>
              <th class="px-6 py-4">会话ID</th>
              <th class="px-6 py-4">用户ID</th>
              <th class="px-6 py-4">标题</th>
              <th class="px-6 py-4">最后预览</th>
              <th class="px-6 py-4">更新时间</th>
              <th class="px-6 py-4">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in sessions" :key="item.id" class="hover:bg-slate-50">
              <td class="px-6 py-4 text-sm font-semibold text-slate-900">{{ item.id }}</td>
              <td class="px-6 py-4 text-sm text-slate-600">{{ item.userId }}</td>
              <td class="px-6 py-4 text-sm text-slate-900">{{ item.title || '-' }}</td>
              <td class="px-6 py-4 text-sm text-slate-600">
                {{ item.lastMessage || item.firstUserMessage || '暂无预览消息' }}
              </td>
              <td class="px-6 py-4 text-sm text-slate-400">
                {{ formatDateTime(item.updatedAt || item.createdAt) }}
              </td>
              <td class="px-6 py-4">
                <button
                  type="button"
                  class="rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-600 transition-colors hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="deletingId === item.id"
                  @click="handleDelete(item)"
                >
                  {{ deletingId === item.id ? '删除中...' : '删除' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex justify-end border-t border-slate-100 p-4">
        <el-pagination
          v-model:current-page="pager.page"
          v-model:page-size="pager.pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @current-change="fetchSessions"
          @size-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>
