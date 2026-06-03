<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getKnowledgeDocumentList } from '@/api/knowledge'
import { deleteAdminUser, getAdminUserConversations, getAdminUserKnowledgeBases, getAdminUsers } from '@/api/user'
import { formatDateTime } from '@/utils/format'
import { showSuccessMessage } from '@/utils/message'

const loading = ref(false)
const users = ref([])
const total = ref(0)
const summary = reactive({
  totalUsers: 0,
  registeredUsers: 0,
  guestUsers: 0,
})
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedUser = ref(null)
const userConversations = ref([])
const userKnowledgeBases = ref([])
const filters = reactive({
  keyword: '',
  guestOnly: null,
  page: 1,
  size: 10,
})

const activeUsers = computed(() =>
  users.value.filter((item) => {
    if (!item.lastActiveAt) {
      return false
    }

    const lastActiveAt = new Date(item.lastActiveAt).getTime()
    return Date.now() - lastActiveAt <= 24 * 60 * 60 * 1000
  }).length,
)

async function fetchUsers() {
  loading.value = true

  try {
    const payload = await getAdminUsers({
      page: filters.page - 1,
      size: filters.size,
      keyword: filters.keyword || undefined,
      guestOnly: filters.guestOnly,
    })

    users.value = payload?.items || []
    total.value = payload?.total || 0
    summary.totalUsers = payload?.summary?.totalUsers || 0
    summary.registeredUsers = payload?.summary?.registeredUsers || 0
    summary.guestUsers = payload?.summary?.guestUsers || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  fetchUsers()
}

function handleReset() {
  filters.keyword = ''
  filters.guestOnly = null
  filters.page = 1
  fetchUsers()
}

async function handleDeleteUser(user) {
  await ElMessageBox.confirm(
    `确认删除注册用户“${user.username}”吗？`,
    '删除用户',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    },
  )

  await deleteAdminUser(user.userId)
  showSuccessMessage('用户删除成功')

  if (users.value.length === 1 && filters.page > 1) {
    filters.page -= 1
  }

  await fetchUsers()
}

async function openUserDetail(user) {
  selectedUser.value = user
  detailVisible.value = true
  detailLoading.value = true

  try {
    const [conversationPayload, knowledgeBasePayload] = await Promise.all([
      getAdminUserConversations(user.userId, { page: 0, size: 10 }),
      getAdminUserKnowledgeBases(user.userId, { page: 0, size: 10 }),
    ])

    userConversations.value = conversationPayload || []
    const knowledgeBases = knowledgeBasePayload || []
    userKnowledgeBases.value = await Promise.all(
      knowledgeBases.map(async (item) => {
        const documents = await getKnowledgeDocumentList(item.id, {
          page: 0,
          size: 3,
        })

        return {
          ...item,
          documents: documents || [],
        }
      }),
    )
  } finally {
    detailLoading.value = false
  }
}

function handleDrawerClosed() {
  selectedUser.value = null
  userConversations.value = []
  userKnowledgeBases.value = []
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="用户管理" description="查看站内用户、游客账号和最近活跃情况。" />

    <div class="grid grid-cols-1 gap-6 md:grid-cols-4">
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">用户总数</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.totalUsers }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">注册用户</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.registeredUsers }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">游客账号</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ summary.guestUsers }}</p>
      </div>
      <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p class="text-sm font-medium text-slate-500">当前页活跃用户</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">{{ activeUsers }}</p>
      </div>
    </div>

    <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div class="grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,1fr)_220px_auto]">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索用户名或邮箱"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.guestOnly" clearable placeholder="用户类型">
          <el-option :value="null" label="全部用户" />
          <el-option :value="false" label="注册用户" />
          <el-option :value="true" label="游客账号" />
        </el-select>
        <div class="flex gap-3">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </div>

    <LoadingBlock v-if="loading" text="用户列表加载中..." />

    <EmptyState
      v-else-if="!users.length"
      title="暂无用户数据"
      description="当前筛选条件下没有可展示的用户记录。"
    />

    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
            <tr>
              <th class="px-6 py-4">用户</th>
              <th class="px-6 py-4">类型</th>
              <th class="px-6 py-4">邮箱</th>
              <th class="px-6 py-4">创建时间</th>
              <th class="px-6 py-4">最后活跃</th>
              <th class="px-6 py-4">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in users" :key="item.userId" class="hover:bg-slate-50">
              <td class="px-6 py-4">
                <div>
                  <p class="text-sm font-semibold text-slate-900">{{ item.username }}</p>
                  <p class="mt-1 text-xs text-slate-400">User ID: {{ item.userId }}</p>
                </div>
              </td>
              <td class="px-6 py-4">
                <span
                  class="rounded-full px-2.5 py-1 text-[10px] font-bold"
                  :class="item.isGuest ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'"
                >
                  {{ item.isGuest ? '游客账号' : '注册用户' }}
                </span>
              </td>
              <td class="px-6 py-4 text-sm text-slate-600">{{ item.email || '-' }}</td>
              <td class="px-6 py-4 text-sm text-slate-400">{{ formatDateTime(item.createdAt) }}</td>
              <td class="px-6 py-4 text-sm text-slate-400">
                {{ item.lastActiveAt ? formatDateTime(item.lastActiveAt) : '暂无记录' }}
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center gap-3">
                  <el-button text type="primary" @click="openUserDetail(item)">查看详情</el-button>
                  <el-button
                    v-if="!item.isGuest"
                    text
                    type="danger"
                    @click="handleDeleteUser(item)"
                  >
                    删除用户
                  </el-button>
                </div>
              </td>
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
          @current-change="fetchUsers"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <el-drawer
      v-model="detailVisible"
      size="720px"
      title="用户详情"
      @closed="handleDrawerClosed"
    >
      <div v-if="selectedUser" class="space-y-6">
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">账号信息</p>
            <p class="mt-3 text-lg font-bold text-slate-900">{{ selectedUser.username }}</p>
            <p class="mt-1 text-sm text-slate-500">User ID: {{ selectedUser.userId }}</p>
            <p class="mt-1 text-sm text-slate-500">邮箱：{{ selectedUser.email || '-' }}</p>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">活跃信息</p>
            <p class="mt-3 text-sm text-slate-600">
              创建时间：{{ formatDateTime(selectedUser.createdAt) }}
            </p>
            <p class="mt-1 text-sm text-slate-600">
              最后活跃：{{ selectedUser.lastActiveAt ? formatDateTime(selectedUser.lastActiveAt) : '暂无记录' }}
            </p>
            <p class="mt-1 text-sm text-slate-600">
              账号类型：{{ selectedUser.isGuest ? '游客账号' : '注册用户' }}
            </p>
          </div>
        </div>

        <LoadingBlock v-if="detailLoading" text="用户关联数据加载中..." />

        <template v-else>
          <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-5 py-4">
              <h3 class="text-base font-bold text-slate-900">最近会话</h3>
            </div>
            <div v-if="userConversations.length" class="divide-y divide-slate-100">
              <div v-for="item in userConversations" :key="item.id" class="px-5 py-4">
                <p class="text-sm font-semibold text-slate-900">{{ item.title || `Conversation #${item.id}` }}</p>
                <p class="mt-1 text-xs text-slate-500">Conversation ID: {{ item.id }}</p>
                <p class="mt-2 text-sm text-slate-600">{{ item.lastMessage || item.firstUserMessage || '暂无消息预览' }}</p>
              </div>
            </div>
            <div v-else class="px-5 py-8 text-sm text-slate-500">该用户暂无会话记录。</div>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-5 py-4">
              <h3 class="text-base font-bold text-slate-900">创建的知识库</h3>
            </div>
            <div v-if="userKnowledgeBases.length" class="divide-y divide-slate-100">
              <div v-for="item in userKnowledgeBases" :key="item.id" class="px-5 py-4">
                <div class="flex items-start justify-between gap-4 border-b border-dashed border-slate-100 pb-4">
                  <div>
                    <p class="text-sm font-semibold text-slate-900">{{ item.name }}</p>
                    <p class="mt-1 text-xs text-slate-500">{{ item.description || '暂无描述' }}</p>
                  </div>
                  <span class="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold text-slate-600">
                    {{ item.documentCount ?? 0 }} 篇文档
                  </span>
                </div>
                <div class="pt-4">
                  <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">前三个文档</p>
                  <div v-if="item.documents?.length" class="mt-3 space-y-2">
                    <div
                      v-for="document in item.documents"
                      :key="document.id"
                      class="rounded-xl bg-slate-50 px-3 py-3"
                    >
                      <p class="text-sm font-medium text-slate-900">{{ document.fileName }}</p>
                      <p class="mt-1 text-xs text-slate-500">
                        {{ document.parseStatus || 'UNKNOWN' }} · {{ formatDateTime(document.createdAt) }}
                      </p>
                    </div>
                  </div>
                  <p v-else class="mt-3 text-sm text-slate-500">该知识库下暂无文档。</p>
                </div>
              </div>
            </div>
            <div v-else class="px-5 py-8 text-sm text-slate-500">该用户暂无创建知识库。</div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
