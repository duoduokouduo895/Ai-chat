<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { adminMenus } from '@/constants/menu'
import SidebarNavItem from './SidebarNavItem.vue'

const route = useRoute()

const activeMenuKey = computed(() => route.meta.menuKey)
</script>

<template>
  <aside class="hidden w-72 shrink-0 flex-col border-r border-slate-800 bg-slate-900 text-white lg:flex">
    <div class="flex items-center gap-3 p-6">
      <div class="flex size-10 items-center justify-center rounded-xl bg-[var(--color-primary)]">
        <span class="material-symbols-outlined text-white">rocket_launch</span>
      </div>
      <div>
        <h1 class="text-lg font-bold leading-tight">Dduo Admin</h1>
        <p class="text-xs font-medium uppercase tracking-wider text-slate-400">Management Portal</p>
      </div>
    </div>

    <nav class="mt-4 flex-1 space-y-1 px-4">
      <SidebarNavItem
        v-for="item in adminMenus"
        :key="item.key"
        :item="item"
        :active="activeMenuKey === item.key"
      />
    </nav>

    <div class="border-t border-slate-800 p-6">
      <div class="rounded-xl bg-slate-800 p-4">
        <p class="mb-2 text-xs text-slate-400">Current Plan: Pro</p>
        <div class="mb-3 h-1.5 w-full overflow-hidden rounded-full bg-slate-700">
          <div class="h-full w-3/4 bg-[var(--color-primary)]"></div>
        </div>
        <button
          type="button"
          class="w-full rounded-lg bg-white/10 py-2 text-xs font-bold text-white transition-colors hover:bg-white/20"
        >
          Upgrade Storage
        </button>
      </div>
    </div>
  </aside>
</template>
