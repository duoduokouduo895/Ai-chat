<script setup>
import AdminHeader from '@/components/shell/AdminHeader.vue'
import AdminSidebar from '@/components/shell/AdminSidebar.vue'
</script>

<template>
  <div class="flex min-h-screen bg-[var(--color-bg-light)] text-slate-900">
    <AdminSidebar />

    <main class="flex min-w-0 flex-1 flex-col overflow-hidden">
      <AdminHeader />

      <section class="flex-1 overflow-y-auto p-6 md:p-8">
        <router-view />
      </section>
    </main>
  </div>
</template>
