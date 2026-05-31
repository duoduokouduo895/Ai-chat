<script setup>
defineProps({
  item: {
    type: Object,
    required: true,
  },
  active: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <RouterLink
    :to="item.path"
    class="flex items-center gap-3 rounded-xl px-4 py-3 transition-all"
    :class="
      active
        ? 'bg-[var(--color-primary)] text-white'
        : 'text-slate-400 hover:bg-white/10 hover:text-white'
    "
  >
    <span class="material-symbols-outlined">{{ item.icon }}</span>
    <div class="flex flex-col">
      <span class="text-sm font-medium">{{ item.label }}</span>
      <span class="text-[10px] opacity-70">{{ item.labelEn }}</span>
    </div>
  </RouterLink>
</template>
