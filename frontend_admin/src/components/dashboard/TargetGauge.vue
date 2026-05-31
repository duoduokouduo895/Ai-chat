<script setup>
defineProps({
  percent: {
    type: Number,
    default: 75,
  },
  description: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <div class="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
    <h3 class="mb-4 text-base font-bold">Monthly Target</h3>
    <div class="relative flex items-center justify-center py-4">
      <svg class="h-40 w-40 -rotate-90 transform">
        <circle
          class="text-slate-100"
          cx="80"
          cy="80"
          r="70"
          fill="transparent"
          stroke="currentColor"
          stroke-width="12"
        />
        <circle
          class="text-[var(--color-primary)]"
          cx="80"
          cy="80"
          r="70"
          fill="transparent"
          stroke="currentColor"
          stroke-width="12"
          stroke-linecap="round"
          stroke-dasharray="440"
          :stroke-dashoffset="440 - (440 * percent) / 100"
        />
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span class="text-3xl font-bold">{{ percent }}%</span>
        <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Achieved</span>
      </div>
    </div>
    <div class="mt-2 text-center">
      <p class="text-xs text-slate-500">{{ description }}</p>
    </div>
  </div>
</template>
