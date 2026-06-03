<script setup>
import EmptyState from '@/components/common/EmptyState.vue'
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<template>
  <div>
    <PageHeader title="个人中心" description="当前用户信息与退出登录能力会放在这里。" />
    <EmptyState title="个人中心页面待接入" description="后续会接 /api/users/me 和退出登录。" />
  </div>
</template>
