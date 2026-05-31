<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/modules/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const formRef = ref()
const passwordVisible = ref(false)
const loginForm = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  try {
    await authStore.login(loginForm)
    ElMessage.success('登录成功')

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.push(redirect)
  } catch (error) {
    console.error('登录失败', error)
  }
}
</script>

<template>
  <div class="relative flex min-h-screen w-full">
    <div class="relative hidden overflow-hidden bg-[var(--color-primary)]/10 lg:flex lg:w-1/2">
      <div
        class="absolute inset-0 bg-cover bg-center"
        style="
          background-image: url('/admin-login-bg.svg');
        "
      ></div>
      <div class="absolute inset-0 bg-gradient-to-br from-[var(--color-primary)]/80 to-[var(--color-primary)]/40 mix-blend-multiply"></div>
      <div class="relative z-10 flex flex-col justify-center px-20 text-white">
        <div class="mb-8 flex h-16 w-16 items-center justify-center rounded-xl bg-white/20 backdrop-blur-md">
          <span class="material-symbols-outlined text-4xl">dashboard_customize</span>
        </div>
        <h1 class="mb-4 text-5xl font-black tracking-tight">Dduo 管理后台</h1>
        <p class="max-w-md text-xl font-medium text-white/90">
          统一管理用户、会话、知识库、搜索索引和模型配置。
        </p>
      </div>
      <div class="absolute bottom-10 left-20 flex gap-4 text-sm">
        <div class="flex items-center gap-2 text-white/70">
          <span class="material-symbols-outlined text-xs">verified_user</span>
          <span>权限隔离</span>
        </div>
        <div class="flex items-center gap-2 text-white/70">
          <span class="material-symbols-outlined text-xs">update</span>
          <span>运行状态可观测</span>
        </div>
      </div>
    </div>

    <div class="flex w-full items-center justify-center bg-[var(--color-bg-light)] p-8 lg:w-1/2">
      <div class="w-full max-w-[480px]">
        <div class="mb-12 text-center lg:hidden">
          <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-[var(--color-primary)]/10">
            <span class="material-symbols-outlined text-2xl text-[var(--color-primary)]">dashboard_customize</span>
          </div>
          <h1 class="text-3xl font-black text-slate-900">Dduo 管理后台</h1>
        </div>

        <div class="rounded-xl border border-[var(--color-primary)]/10 bg-white p-8 shadow-xl shadow-[var(--color-primary)]/5 lg:p-12">
          <div class="mb-10">
            <h2 class="mb-2 text-2xl font-bold text-slate-900">登录管理后台</h2>
            <p class="text-slate-500">使用管理员账号继续。</p>
          </div>

          <el-form ref="formRef" :model="loginForm" :rules="rules" label-position="top" hide-required-asterisk @submit.prevent>
            <el-form-item prop="username">
              <template #label>
                <div class="flex w-full justify-between text-sm font-semibold text-slate-700">
                  <span class="flex items-center gap-1">
                    <span class="text-[var(--el-color-danger)]">*</span>
                    <span>用户名</span>
                  </span>
                </div>
              </template>
              <div class="relative w-full">
                <span class="material-symbols-outlined pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-xl text-slate-400">
                  person
                </span>
                <el-input
                  v-model="loginForm.username"
                  placeholder="请输入用户名"
                  size="large"
                  class="login-input"
                />
              </div>
            </el-form-item>

            <el-form-item prop="password" class="mt-6">
              <template #label>
                <div class="flex w-full justify-between text-sm font-semibold text-slate-700">
                  <span class="flex items-center gap-1">
                    <span class="text-[var(--el-color-danger)]">*</span>
                    <span>密码</span>
                  </span>
                </div>
              </template>
              <div class="relative w-full">
                <span class="material-symbols-outlined pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-xl text-slate-400">
                  lock
                </span>
                <el-input
                  v-model="loginForm.password"
                  :type="passwordVisible ? 'text' : 'password'"
                  placeholder="请输入密码"
                  size="large"
                  class="login-input"
                  @keyup.enter="handleSubmit"
                />
                <button
                  type="button"
                  class="absolute right-4 top-1/2 z-10 -translate-y-1/2 text-slate-400 transition-colors hover:text-[var(--color-primary)]"
                  @click="passwordVisible = !passwordVisible"
                >
                  <span class="material-symbols-outlined text-xl">
                    {{ passwordVisible ? 'visibility_off' : 'visibility' }}
                  </span>
                </button>
              </div>
            </el-form-item>

            <button
              type="button"
              class="mt-4 flex w-full items-center justify-center gap-2 rounded-lg bg-[var(--color-primary)] py-4 font-bold text-white shadow-lg shadow-[var(--color-primary)]/30 transition-all hover:bg-[var(--color-primary)]/90 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="authStore.loginLoading"
              @click="handleSubmit"
            >
              <span>{{ authStore.loginLoading ? '登录中...' : '登录' }}</span>
              <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </el-form>

          <div class="mt-8 border-t border-slate-100 pt-8">
            <p class="text-center text-sm text-slate-500">
              仅限授权管理员访问，所有关键操作会被记录。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.login-input .el-input__wrapper) {
  border-radius: 0.75rem;
  box-shadow: none;
  min-height: 3.5rem;
  padding-left: 2.75rem;
  padding-right: 2.75rem;
}

:deep(.login-input .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgb(102 133 255 / 0.3);
}

:deep(.el-form-item__error) {
  padding-top: 0.375rem;
}
</style>
