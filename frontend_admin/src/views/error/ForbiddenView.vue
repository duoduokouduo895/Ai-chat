<template>
  <div class="flex min-h-screen items-center justify-center bg-[var(--color-bg-light)] p-8">
    <div class="max-w-md text-center">
      <p class="text-sm font-semibold uppercase tracking-[0.24em] text-[var(--color-primary)]">403</p>
      <h1 class="mt-3 text-4xl font-black text-slate-900">无权限访问</h1>
      <p class="mt-3 text-sm text-slate-500">当前账号没有访问此页面的权限。</p>
      <RouterLink
        to="/dashboard"
        class="mt-6 inline-flex rounded-xl bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white"
      >
        返回首页
      </RouterLink>
    </div>
  </div>
</template>
