<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <div class="mb-6">
    <h2 class="text-2xl font-bold tracking-tight">{{ title }}</h2>
    <p v-if="description" class="mt-1 text-sm text-slate-500">
      {{ description }}
    </p>
  </div>
</template>
