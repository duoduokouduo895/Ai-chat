<script setup>
import { computed, onMounted, ref } from 'vue'

import RecentActivityTable from '@/components/dashboard/RecentActivityTable.vue'
import StatCard from '@/components/dashboard/StatCard.vue'
import SystemStatusPanel from '@/components/dashboard/SystemStatusPanel.vue'
import WelcomeBanner from '@/components/dashboard/WelcomeBanner.vue'
import { getDashboardData } from '@/service/dashboard/dashboard-service'
import { useAuthStore } from '@/stores/modules/auth'

const authStore = useAuthStore()
const dashboardLoading = ref(false)
const dashboardData = ref({
  stats: [],
  systemStatus: {
    label: 'Stable',
    items: [],
  },
  activities: [],
})

const username = computed(() => authStore.username || 'Admin User')

async function fetchDashboardData() {
  dashboardLoading.value = true

  try {
    dashboardData.value = await getDashboardData()
  } finally {
    dashboardLoading.value = false
  }
}

onMounted(() => {
  fetchDashboardData()
})
</script>

<template>
  <div class="space-y-8">
    <WelcomeBanner :username="username" />

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:col-span-2">
        <StatCard
          v-for="stat in dashboardData.stats"
          :key="stat.key"
          :title="stat.title"
          :title-zh="stat.titleZh"
          :value="stat.value"
          :icon="stat.icon"
          :icon-class="stat.iconClass"
          :trend="stat.trend"
        />
      </div>

      <div class="space-y-6">
        <SystemStatusPanel
          :label="dashboardData.systemStatus.label"
          :items="dashboardData.systemStatus.items"
        />
      </div>
    </div>

    <div v-if="dashboardLoading" class="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
      首页数据加载中...
    </div>

    <RecentActivityTable v-else :items="dashboardData.activities" />
  </div>
</template>
