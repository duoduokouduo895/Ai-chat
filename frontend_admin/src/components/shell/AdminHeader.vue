<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/modules/auth'

const router = useRouter()
const authStore = useAuthStore()

const usernameInitials = computed(() => {
  const username = authStore.username || 'Admin User'
  return username.slice(0, 2).toUpperCase()
})

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <header
    class="sticky top-0 z-10 flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 md:px-8"
  >
    <div class="flex flex-1 items-center gap-4">
      <div class="relative w-full max-w-xl">
        <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-xl text-slate-400">
          search
        </span>
        <input
          type="text"
          placeholder="Search data, users, or system logs..."
          class="w-full rounded-xl border-none bg-slate-100 py-2 pl-10 pr-4 text-sm outline-none ring-0 placeholder:text-slate-400 focus:ring-2 focus:ring-[var(--color-primary)]/30"
        />
      </div>
    </div>

    <div class="flex items-center gap-4">
      <button
        type="button"
        class="relative rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100"
      >
        <span class="material-symbols-outlined">notifications</span>
        <span class="absolute right-2 top-2 h-2 w-2 rounded-full border-2 border-white bg-red-500"></span>
      </button>

      <div class="mx-1 h-8 w-px bg-slate-200"></div>

      <div class="flex items-center gap-3 pl-2">
        <div class="hidden text-right sm:block">
          <p class="text-sm font-semibold">{{ authStore.username || 'Admin User' }}</p>
          <p class="text-[10px] font-medium text-slate-500">Authenticated User</p>
        </div>
        <div
          class="flex h-10 w-10 items-center justify-center rounded-full border-2 border-[var(--color-primary)]/20 bg-slate-200 text-sm font-bold text-slate-700"
        >
          {{ usernameInitials }}
        </div>
        <button
          type="button"
          class="rounded-lg bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-200"
          @click="handleLogout"
        >
          退出
        </button>
      </div>
    </div>
  </header>
</template>
