<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

import EmptyState from '@/components/common/EmptyState.vue'
import LoadingBlock from '@/components/common/LoadingBlock.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  getOperationsIncident,
  getOperationsIncidents,
  getOperationsReport,
  getOperationsSummary,
} from '@/api/operations'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const detailLoading = ref(false)
const incidentItems = ref([])
const total = ref(0)
const selectedId = ref('')
const selectedIncident = ref(null)
const selectedReport = ref(null)
const summary = reactive({
  totalIncidents: 0,
  activeIncidents: 0,
  resolvedIncidents: 0,
  reportCount: 0,
  supportedReports: 0,
  abstainedReports: 0,
  evidenceCompleteReports: 0,
  averageReportLatencyMs: 0,
})
const filters = reactive({
  page: 1,
  size: 20,
  status: '',
  severity: '',
  windowHours: 24,
})

const coverageText = computed(() => {
  if (!summary.reportCount) return '暂无报告'
  return `${summary.evidenceCompleteReports}/${summary.reportCount}`
})

const supportText = computed(() => {
  if (!summary.reportCount) return '暂无样本'
  return `${summary.supportedReports}/${summary.reportCount}`
})

function statusLabel(status) {
  return {
    OPEN: '待处理',
    DIAGNOSING: '诊断中',
    DIAGNOSED: '已诊断',
    FAILED: '失败',
    RESOLVED: '已恢复',
    CLOSED: '已关闭',
  }[status] || status || '-'
}

function statusClass(status) {
  if (status === 'RESOLVED' || status === 'CLOSED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'FAILED') return 'bg-red-50 text-red-700'
  if (status === 'DIAGNOSING') return 'bg-violet-50 text-violet-700'
  return 'bg-amber-50 text-amber-700'
}

function severityClass(severity) {
  if (severity === 'CRITICAL') return 'text-red-700'
  if (severity === 'WARNING') return 'text-amber-700'
  return 'text-slate-500'
}

function formatLatency(milliseconds) {
  if (!milliseconds) return '-'
  return milliseconds >= 1000 ? `${(milliseconds / 1000).toFixed(1)} 秒` : `${milliseconds} ms`
}

async function fetchSummary() {
  const payload = await getOperationsSummary({ windowHours: filters.windowHours })
  Object.assign(summary, payload || {})
}

async function fetchIncidents() {
  const payload = await getOperationsIncidents({
    page: filters.page - 1,
    size: filters.size,
    status: filters.status || undefined,
    severity: filters.severity || undefined,
  })
  incidentItems.value = payload?.items || []
  total.value = payload?.total || 0
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([fetchSummary(), fetchIncidents()])
  } finally {
    loading.value = false
  }
}

async function selectIncident(item) {
  selectedId.value = item.id
  selectedIncident.value = null
  selectedReport.value = null
  detailLoading.value = true
  try {
    selectedIncident.value = await getOperationsIncident(item.id)
    if (item.hasReport) {
      selectedReport.value = await getOperationsReport(item.id)
    }
  } finally {
    detailLoading.value = false
  }
}

function handleFilter() {
  filters.page = 1
  refresh()
}

function resetFilters() {
  filters.status = ''
  filters.severity = ''
  filters.windowHours = 24
  filters.page = 1
  refresh()
}

onMounted(refresh)
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="智能运维" description="只读查看 AI-chat 告警、确定性诊断与 shadow 数据质量。" />

    <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm" aria-label="观测摘要">
      <div class="flex flex-col gap-4 border-b border-slate-100 px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p class="text-sm font-semibold text-slate-900">最近 {{ filters.windowHours }} 小时</p>
          <p class="mt-1 text-xs text-slate-500">服务：ai-chat-candidate，数据来自 AIDiag 私有只读接口</p>
        </div>
        <el-select v-model="filters.windowHours" class="w-full md:w-40" aria-label="观测窗口" @change="handleFilter">
          <el-option label="最近 24 小时" :value="24" />
          <el-option label="最近 7 天" :value="168" />
          <el-option label="最近 30 天" :value="720" />
        </el-select>
      </div>
      <div class="grid grid-cols-2 divide-x divide-y divide-slate-100 md:grid-cols-4 md:divide-y-0">
        <div class="px-5 py-4">
          <p class="text-xs font-medium text-slate-500">Incident</p>
          <p class="mt-1 text-xl font-bold text-slate-900">{{ summary.totalIncidents }}</p>
          <p class="mt-1 text-xs text-slate-400">活跃 {{ summary.activeIncidents }}，已恢复 {{ summary.resolvedIncidents }}</p>
        </div>
        <div class="px-5 py-4">
          <p class="text-xs font-medium text-slate-500">主要根因成立</p>
          <p class="mt-1 text-xl font-bold text-slate-900">{{ supportText }}</p>
          <p class="mt-1 text-xs text-slate-400">弃权 {{ summary.abstainedReports }}</p>
        </div>
        <div class="px-5 py-4">
          <p class="text-xs font-medium text-slate-500">Metric + Log 完整</p>
          <p class="mt-1 text-xl font-bold text-slate-900">{{ coverageText }}</p>
          <p class="mt-1 text-xs text-slate-400">Trace 仅作辅助证据</p>
        </div>
        <div class="px-5 py-4">
          <p class="text-xs font-medium text-slate-500">平均出报告</p>
          <p class="mt-1 text-xl font-bold text-slate-900">{{ formatLatency(summary.averageReportLatencyMs) }}</p>
          <p class="mt-1 text-xs text-slate-400">未测量时不推断准确率</p>
        </div>
      </div>
    </section>

    <section class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="Incident 筛选">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-[180px_180px_auto] md:items-end">
        <label class="text-xs font-semibold text-slate-600">
          状态
          <el-select v-model="filters.status" clearable class="mt-2 w-full" placeholder="全部状态">
            <el-option label="待处理" value="OPEN" />
            <el-option label="诊断中" value="DIAGNOSING" />
            <el-option label="已诊断" value="DIAGNOSED" />
            <el-option label="已恢复" value="RESOLVED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </label>
        <label class="text-xs font-semibold text-slate-600">
          严重级别
          <el-select v-model="filters.severity" clearable class="mt-2 w-full" placeholder="全部级别">
            <el-option label="信息" value="INFO" />
            <el-option label="警告" value="WARNING" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </label>
        <div class="flex gap-3 md:justify-end">
          <el-button type="primary" @click="handleFilter">筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button :loading="loading" aria-label="刷新智能运维数据" @click="refresh">刷新</el-button>
        </div>
      </div>
    </section>

    <LoadingBlock v-if="loading" text="Incident 加载中..." />
    <EmptyState v-else-if="!incidentItems.length" title="当前窗口没有 Incident" description="这可能代表系统稳定，也可能是观测窗口内尚无告警样本。" />

    <div v-else class="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.75fr)]">
      <section class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm" aria-label="Incident 列表">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[760px] text-left">
            <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <th class="px-5 py-4">告警</th>
                <th class="px-5 py-4">级别</th>
                <th class="px-5 py-4">状态</th>
                <th class="px-5 py-4">最近更新</th>
                <th class="px-5 py-4"><span class="sr-only">操作</span></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="item in incidentItems" :key="item.id" :class="selectedId === item.id ? 'bg-violet-50/60' : 'hover:bg-slate-50'">
                <td class="px-5 py-4">
                  <p class="text-sm font-semibold text-slate-900">{{ item.alertName }}</p>
                  <p class="mt-1 max-w-[38ch] truncate text-xs text-slate-500">{{ item.title }}</p>
                </td>
                <td class="px-5 py-4 text-xs font-bold" :class="severityClass(item.severity)">{{ item.severity }}</td>
                <td class="px-5 py-4">
                  <span class="rounded-full px-2.5 py-1 text-xs font-semibold" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
                </td>
                <td class="px-5 py-4 text-sm text-slate-500">{{ formatDateTime(item.lastAlertAt) }}</td>
                <td class="px-5 py-4 text-right">
                  <el-button text type="primary" :aria-label="`查看 ${item.alertName}`" @click="selectIncident(item)">查看</el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="flex justify-end border-t border-slate-100 px-5 py-4">
          <el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" background
            layout="total, sizes, prev, pager, next" :page-sizes="[20, 50, 100]" :total="total"
            @current-change="fetchIncidents" @size-change="handleFilter" />
        </div>
      </section>

      <aside class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="诊断详情">
        <LoadingBlock v-if="detailLoading" text="诊断详情加载中..." />
        <div v-else-if="selectedIncident" class="space-y-5">
          <div>
            <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">确定性诊断</p>
            <h2 class="mt-2 text-lg font-bold text-slate-900">{{ selectedIncident.alertName }}</h2>
            <p class="mt-2 text-sm leading-6 text-slate-600">{{ selectedIncident.title }}</p>
          </div>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-3 border-y border-slate-100 py-4 text-sm">
            <div><dt class="text-xs text-slate-400">Incident 状态</dt><dd class="mt-1 font-semibold text-slate-700">{{ statusLabel(selectedIncident.status) }}</dd></div>
            <div><dt class="text-xs text-slate-400">任务状态</dt><dd class="mt-1 font-semibold text-slate-700">{{ selectedIncident.diagnosisRunStatus || '-' }}</dd></div>
            <div><dt class="text-xs text-slate-400">开始时间</dt><dd class="mt-1 text-slate-600">{{ formatDateTime(selectedIncident.startedAt) }}</dd></div>
            <div><dt class="text-xs text-slate-400">尝试次数</dt><dd class="mt-1 text-slate-600">{{ selectedIncident.attempt || '-' }}</dd></div>
          </dl>
          <div v-if="selectedReport" class="space-y-4">
            <div>
              <p class="text-xs font-semibold text-slate-500">根因候选</p>
              <p class="mt-1 text-sm font-bold text-slate-900">{{ selectedReport.candidate || '证据不足，未确认主要根因' }}</p>
              <p class="mt-1 text-xs text-slate-500">验证：{{ selectedReport.verificationStatus || '-' }} · 支持度 {{ selectedReport.supportScore ?? '-' }}</p>
            </div>
            <div>
              <p class="text-xs font-semibold text-slate-500">验证建议</p>
              <p class="mt-1 text-sm leading-6 text-slate-600">{{ selectedReport.verificationSuggestion || '暂无建议' }}</p>
            </div>
            <div>
              <p class="text-xs font-semibold text-slate-500">证据引用</p>
              <p class="mt-1 text-sm text-slate-600">支持证据 {{ selectedReport.supportingEvidenceIds?.length || 0 }} 条，反对证据 {{ selectedReport.opposingEvidenceIds?.length || 0 }} 条</p>
            </div>
            <p class="rounded-xl bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800">{{ selectedReport.riskWarning || '报告仅用于辅助判断，不执行自动修复。' }}</p>
          </div>
          <p v-else class="text-sm leading-6 text-slate-500">该 Incident 尚未生成报告。系统会保留任务状态，不会虚构主要根因。</p>
        </div>
        <EmptyState v-else title="选择一条 Incident" description="查看任务状态、证据结论与验证建议。" />
      </aside>
    </div>
  </div>
</template>
