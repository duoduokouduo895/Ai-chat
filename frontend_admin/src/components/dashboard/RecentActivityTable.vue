<script setup>
const props = defineProps({
  items: {
    type: Array,
    default: () => [],
  },
})

function getInitials(title = '') {
  return title
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('')
}
</script>

<template>
  <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
    <div class="flex items-center justify-between border-b border-slate-100 p-6">
      <div>
        <h3 class="text-lg font-bold">Recent Activities</h3>
        <p class="text-sm text-slate-500">Latest session updates and system logs</p>
      </div>
      <button type="button" class="text-sm font-semibold text-[var(--color-primary)] hover:underline">
        View All Logs
      </button>
    </div>

    <div class="overflow-x-auto">
      <table class="w-full text-left">
        <thead class="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
          <tr>
            <th class="px-6 py-4">Session</th>
            <th class="px-6 py-4">Status</th>
            <th class="px-6 py-4">Preview</th>
            <th class="px-6 py-4">Platform</th>
            <th class="px-6 py-4">Time</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr
            v-for="item in items"
            :key="item.id"
            class="transition-colors hover:bg-slate-50"
          >
            <td class="px-6 py-4">
              <div class="flex items-center gap-3">
                <div class="flex size-8 items-center justify-center rounded-full bg-[var(--color-primary)]/10 text-xs font-bold text-[var(--color-primary)]">
                  {{ getInitials(item.title) || 'DU' }}
                </div>
                <div>
                  <p class="text-sm font-semibold">{{ item.title }}</p>
                  <p class="text-[10px] text-slate-500">SID-{{ item.id }}</p>
                </div>
              </div>
            </td>
            <td class="px-6 py-4">
              <span class="rounded-full bg-green-100 px-2.5 py-1 text-[10px] font-bold text-green-700">
                {{ item.status }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-slate-600">{{ item.subtitle }}</td>
            <td class="px-6 py-4 text-sm text-slate-600">{{ item.platform }}</td>
            <td class="px-6 py-4 text-sm text-slate-400">{{ item.time }}</td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="5" class="px-6 py-10 text-center text-sm text-slate-500">
              暂无近期活动数据
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
