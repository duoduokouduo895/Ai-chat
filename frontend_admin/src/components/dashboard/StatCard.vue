<script setup>
const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  titleZh: {
    type: String,
    default: '',
  },
  value: {
    type: [String, Number],
    required: true,
  },
  icon: {
    type: String,
    required: true,
  },
  iconClass: {
    type: String,
    default: 'bg-slate-100 text-slate-700',
  },
  trend: {
    type: Object,
    default: null,
  },
})

const trendClass = props.trend?.positive ? 'text-green-500' : 'text-red-500'
const trendIcon = props.trend?.positive ? 'trending_up' : 'trending_down'
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
    <div class="mb-4 flex items-center justify-between">
      <div class="rounded-lg p-2" :class="iconClass">
        <span class="material-symbols-outlined">{{ icon }}</span>
      </div>
      <span v-if="trend?.value" class="flex items-center gap-1 text-xs font-bold" :class="trendClass">
        <span class="material-symbols-outlined text-sm">{{ trendIcon }}</span>
        {{ trend.value }}
      </span>
    </div>
    <p class="text-sm font-medium text-slate-500">{{ title }}</p>
    <h3 class="mt-1 text-2xl font-bold">{{ value }}</h3>
    <p v-if="titleZh" class="mt-2 text-[10px] font-semibold uppercase text-slate-400">{{ titleZh }}</p>
  </div>
</template>
