<script setup>
defineProps({
  label: {
    type: String,
    default: 'Stable',
  },
  items: {
    type: Array,
    default: () => [],
  },
})
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-sm font-bold">System Status</h3>
      <span class="rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-bold uppercase text-green-600">
        {{ label }}
      </span>
    </div>
    <div class="space-y-3">
      <div v-for="item in items" :key="item.label" class="flex items-center justify-between text-xs">
        <span class="text-slate-500">{{ item.label }}</span>
        <span class="font-bold">{{ item.value }}</span>
      </div>
    </div>
  </div>
</template>
