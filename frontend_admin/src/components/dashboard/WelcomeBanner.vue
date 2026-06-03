<script setup>
defineProps({
  username: {
    type: String,
    default: 'Admin User',
  },
})
</script>

<template>
  <div>
    <h2 class="text-2xl font-bold tracking-tight">Dashboard Overview</h2>
    <p class="mt-1 text-slate-500">
      Welcome back, {{ username }}. Here's what's happening with your portal today.
    </p>
  </div>
</template>
