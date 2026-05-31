<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { deleteDocument, getAdminDocumentPage } from '@/api/knowledge'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const deletingId = ref(null)
const documents = ref([])
const total = ref(0)
const summary = reactive({
  documentCount: 0,
  uploaderCount: 0,
  totalFileSize: 0,
})
const pager = reactive({
  page: 1,
  pageSize: 10,
})
const filters = reactive({
  keyword: '',
  parseStatus: '',
  embeddingStatus: '',
})

const parseStatusOptions = ['PENDING', 'PARSING', 'PARSED', 'COMPLETED', 'FAILED']
const embeddingStatusOptions = ['PENDING', 'PROCESSING', 'EMBEDDED', 'COMPLETED', 'FAILED']

function handleSearch() {
  pager.page = 1
  fetchDocuments()
}

function handleReset() {
  filters.keyword = ''
  filters.parseStatus = ''
  filters.embeddingStatus = ''
  pager.page = 1
  fetchDocuments()
}

function formatFileSize(size) {
  const value = Number(size || 0)

  if (value < 1024) {
    return `${value} B`
  }

  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }

  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }

  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}

async function fetchDocuments() {
  loading.value = true

  try {
    const payload = await getAdminDocumentPage({
      page: pager.page - 1,
      size: pager.pageSize,
      keyword: filters.keyword || undefined,
      parseStatus: filters.parseStatus || undefined,
      embeddingStatus: filters.embeddingStatus || undefined,
    })

    documents.value = payload?.items || []
    total.value = payload?.total || 0
    summary.documentCount = payload?.summary?.documentCount || 0
    summary.uploaderCount = payload?.summary?.uploaderCount || 0
    summary.totalFileSize = payload?.summary?.totalFileSize || 0
  } finally {
    loading.value = false
  }
}

async function handleDelete(document) {
  await ElMessageBox.confirm(
    `确认删除文档“${document.fileName || document.title || document.id}”吗？`,
    '删除文档',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    },
  )

  deletingId.value = document.id

  try {
    await deleteDocument(document.id)
    documents.value = documents.value.filter((item) => item.id !== document.id)
    ElMessage.success('文档删除成功')
    await fetchDocuments()
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  fetchDocuments()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="文档管理" description="查看用户上传文档，并支持直接删除后台文档数据。" />

    <div class="grid grid-cols-1 gap-6 md:grid-cols-3">
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">文档总数</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.documentCount }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">上传用户数</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">
          {{ summary.uploaderCount }}
        </p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">文件总大小</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ formatFileSize(summary.totalFileSize) }}</p>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm lg:grid-cols-5">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索文档名、用户、知识库、ID"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select
        v-model="filters.parseStatus"
        placeholder="解析状态"
        clearable
        @change="handleSearch"
      >
        <el-option
          v-for="option in parseStatusOptions"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select>
      <el-select
        v-model="filters.embeddingStatus"
        placeholder="向量化状态"
        clearable
        @change="handleSearch"
      >
        <el-option
          v-for="option in embeddingStatusOptions"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select>
      <div class="flex items-center justify-end gap-3">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <div class="flex items-center justify-end text-sm text-slate-500">
        筛选结果：{{ total }} 条
      </div>
    </div>

    <LoadingBlock v-if="loading" text="文档列表加载中..." />

    <EmptyState
      v-else-if="!documents.length"
      title="暂无上传文档"
      description="当前还没有可展示的用户上传文档。"
    />

    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="border-b border-slate-100 p-6">
        <h3 class="text-lg font-bold text-slate-900">Uploaded Documents</h3>
        <p class="mt-1 text-sm text-slate-500">当前直接使用管理员接口展示全站上传文档数据。</p>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
            <tr>
              <th class="px-6 py-4">文档</th>
              <th class="px-6 py-4">上传用户</th>
              <th class="px-6 py-4">知识库</th>
              <th class="px-6 py-4">状态</th>
              <th class="px-6 py-4">大小</th>
              <th class="px-6 py-4">更新时间</th>
              <th class="px-6 py-4">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in documents" :key="item.id" class="hover:bg-slate-50">
              <td class="px-6 py-4">
                <div>
                  <p class="text-sm font-semibold text-slate-900">{{ item.fileName || item.title }}</p>
                  <p class="mt-1 text-xs text-slate-500">
                    {{ item.fileType || '-' }} · 文档ID {{ item.id }}
                  </p>
                </div>
              </td>
              <td class="px-6 py-4">
                <div>
                  <p class="text-sm font-semibold text-slate-900">{{ item.uploaderName || '未知用户' }}</p>
                  <p class="mt-1 text-xs text-slate-500">用户ID {{ item.uploaderId ?? '-' }}</p>
                </div>
              </td>
              <td class="px-6 py-4 text-sm text-slate-600">
                {{ item.knowledgeBaseName || `知识库 ${item.knowledgeBaseId}` }}
              </td>
              <td class="px-6 py-4">
                <div class="flex flex-col gap-1">
                  <span class="w-fit rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-700">
                    {{ item.parseStatus || 'UNKNOWN' }}
                  </span>
                  <span
                    class="w-fit rounded-full px-2.5 py-1 text-[10px] font-bold"
                    :class="item.isEmbedded ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'"
                  >
                    {{ item.isEmbedded ? 'EMBEDDED' : item.embeddingStatus || 'PENDING' }}
                  </span>
                </div>
              </td>
              <td class="px-6 py-4 text-sm text-slate-600">{{ formatFileSize(item.fileSize) }}</td>
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
          @current-change="fetchDocuments"
          @size-change="handleSearch"
        />
      </div>
    </div>
  </div>
</template>
