<script setup>
defineProps({
  text: {
    type: String,
    default: '加载中...',
  },
})
</script>

<template>
  <div class="rounded-2xl border border-slate-200 bg-white p-10 text-center text-sm text-slate-500 shadow-sm">
    {{ text }}
  </div>
</template>
