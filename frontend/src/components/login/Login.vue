<template>
  <AuthShell title-id="login-title" title="欢迎回来">
    <div class="test-account">
      <span>测试账户已预填充</span>
      <span>Dduo</span>
    </div>

    <form class="auth-form" @submit.prevent="handleLogin">
      <div class="auth-field">
        <label for="username">用户名</label>
        <input
          id="username"
          v-model="formData.username"
          class="auth-input"
          :class="{ error: errors.username }"
          type="text"
          autocomplete="username"
          :aria-invalid="!!errors.username"
          :aria-describedby="errors.username ? 'login-username-error' : undefined"
          placeholder="请输入用户名"
          @blur="validateUsername"
          @input="clearError('username')"
        >
        <span v-if="errors.username" id="login-username-error" class="field-error">{{ errors.username }}</span>
      </div>

      <div class="auth-field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="formData.password"
          class="auth-input"
          :class="{ error: errors.password }"
          type="password"
          autocomplete="current-password"
          :aria-invalid="!!errors.password"
          :aria-describedby="errors.password ? 'login-password-error' : undefined"
          placeholder="请输入密码"
          @blur="validatePassword"
          @input="clearError('password')"
        >
        <span v-if="errors.password" id="login-password-error" class="field-error">{{ errors.password }}</span>
      </div>

      <div v-if="generalError" class="auth-alert" role="alert">{{ generalError }}</div>

      <button class="auth-submit" type="submit" :disabled="isLoading || !isFormValid">
        <span v-if="isLoading" class="auth-spinner"></span>
        {{ isLoading ? '登录中...' : '登录' }}
      </button>
    </form>

    <p class="auth-footer">
      还没有账户？
      <router-link to="/register">立即注册</router-link>
    </p>
  </AuthShell>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthShell from '../auth/AuthShell.vue';
import { getApiErrorMessage } from '../../services/apiErrorService.js';
import { userApiService } from '../../services/userApi.js';

const router = useRouter();
const formData = ref({ username: 'Dduo', password: 'c123456' });
const errors = ref({});
const generalError = ref('');
const isLoading = ref(false);

const validateUsername = () => {
  const username = formData.value.username.trim();
  if (!username) errors.value.username = '请输入用户名';
  else if (username.length < 3 || username.length > 20) errors.value.username = '用户名长度应为3-20个字符';
  else if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) errors.value.username = '用户名只能包含字母、数字和下划线';
  else delete errors.value.username;
  return !errors.value.username;
};

const validatePassword = () => {
  const password = formData.value.password;
  if (!password) errors.value.password = '请输入密码';
  else if (password.length < 6) errors.value.password = '密码长度至少6个字符';
  else delete errors.value.password;
  return !errors.value.password;
};

const clearError = (field) => {
  delete errors.value[field];
  generalError.value = '';
};

const isFormValid = computed(() => (
  formData.value.username.trim()
  && formData.value.password
  && Object.keys(errors.value).length === 0
));

const handleLogin = async () => {
  if (!validateUsername() || !validatePassword()) return;

  isLoading.value = true;
  generalError.value = '';
  try {
    const result = await userApiService.login({
      username: formData.value.username.trim(),
      password: formData.value.password
    });
    if (result.success) {
      router.push('/');
    } else {
      generalError.value = result.message;
    }
  } catch (error) {
    generalError.value = getApiErrorMessage(error, '登录失败，请稍后重试');
  } finally {
    isLoading.value = false;
  }
};
</script>
