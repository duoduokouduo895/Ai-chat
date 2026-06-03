<template>
  <AuthShell title-id="register-title" title="创建账户" description="注册后解锁知识库问答、历史搜索和完整会话能力。">
    <form class="auth-form" @submit.prevent="handleRegister">
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
          :aria-describedby="errors.username ? 'register-username-error' : undefined"
          placeholder="3-20 个字符"
          @blur="validateUsername"
          @input="clearError('username')"
        >
        <span v-if="errors.username" id="register-username-error" class="field-error">{{ errors.username }}</span>
      </div>

      <div class="auth-field">
        <label for="email">邮箱</label>
        <input
          id="email"
          v-model="formData.email"
          class="auth-input"
          :class="{ error: errors.email }"
          type="email"
          autocomplete="email"
          :aria-invalid="!!errors.email"
          :aria-describedby="errors.email ? 'register-email-error' : undefined"
          placeholder="请输入邮箱地址"
          @blur="validateEmail"
          @input="clearError('email')"
        >
        <span v-if="errors.email" id="register-email-error" class="field-error">{{ errors.email }}</span>
      </div>

      <div class="auth-field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="formData.password"
          class="auth-input"
          :class="{ error: errors.password }"
          type="password"
          autocomplete="new-password"
          :aria-invalid="!!errors.password"
          :aria-describedby="errors.password ? 'register-password-error' : undefined"
          placeholder="至少 6 个字符，包含字母和数字"
          @blur="validatePassword"
          @input="clearError('password')"
        >
        <span v-if="errors.password" id="register-password-error" class="field-error">{{ errors.password }}</span>
      </div>

      <div class="auth-field">
        <label for="confirmPassword">确认密码</label>
        <input
          id="confirmPassword"
          v-model="formData.confirmPassword"
          class="auth-input"
          :class="{ error: errors.confirmPassword }"
          type="password"
          autocomplete="new-password"
          :aria-invalid="!!errors.confirmPassword"
          :aria-describedby="errors.confirmPassword ? 'register-confirm-password-error' : undefined"
          placeholder="请再次输入密码"
          @blur="validateConfirmPassword"
          @input="clearError('confirmPassword')"
        >
        <span v-if="errors.confirmPassword" id="register-confirm-password-error" class="field-error">{{ errors.confirmPassword }}</span>
      </div>

      <div v-if="generalError" class="auth-alert" role="alert">{{ generalError }}</div>
      <div v-if="successMessage" class="auth-alert success" role="status" aria-live="polite">{{ successMessage }}</div>

      <button class="auth-submit" type="submit" :disabled="isLoading || !isFormValid">
        <span v-if="isLoading" class="auth-spinner"></span>
        {{ isLoading ? '注册中...' : '注册' }}
      </button>
    </form>

    <p class="auth-footer">
      已有账户？
      <router-link to="/login">立即登录</router-link>
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
const formData = ref({ username: '', email: '', password: '', confirmPassword: '' });
const errors = ref({});
const generalError = ref('');
const successMessage = ref('');
const isLoading = ref(false);

const validateUsername = () => {
  const username = formData.value.username.trim();
  if (!username) errors.value.username = '请输入用户名';
  else if (username.length < 3 || username.length > 20) errors.value.username = '用户名长度应为3-20个字符';
  else if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) errors.value.username = '用户名只能包含字母、数字和下划线';
  else delete errors.value.username;
  return !errors.value.username;
};

const validateEmail = () => {
  const email = formData.value.email.trim();
  if (!email) errors.value.email = '请输入邮箱地址';
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.value.email = '请输入有效的邮箱地址';
  else delete errors.value.email;
  return !errors.value.email;
};

const validatePassword = () => {
  const password = formData.value.password;
  if (!password) errors.value.password = '请输入密码';
  else if (password.length < 6 || password.length > 50) errors.value.password = '密码长度应为6-50个字符';
  else if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,50}$/.test(password)) errors.value.password = '密码必须包含字母和数字';
  else delete errors.value.password;
  return !errors.value.password;
};

const validateConfirmPassword = () => {
  if (!formData.value.confirmPassword) errors.value.confirmPassword = '请确认密码';
  else if (formData.value.confirmPassword !== formData.value.password) errors.value.confirmPassword = '两次输入的密码不一致';
  else delete errors.value.confirmPassword;
  return !errors.value.confirmPassword;
};

const clearError = (field) => {
  delete errors.value[field];
  generalError.value = '';
  successMessage.value = '';
};

const isFormValid = computed(() => (
  formData.value.username.trim()
  && formData.value.email.trim()
  && formData.value.password
  && formData.value.confirmPassword
  && Object.keys(errors.value).length === 0
));

const handleRegister = async () => {
  if (![validateUsername(), validateEmail(), validatePassword(), validateConfirmPassword()].every(Boolean)) return;

  isLoading.value = true;
  generalError.value = '';
  successMessage.value = '';
  try {
    const guestId = localStorage.getItem('guestId');
    const result = await userApiService.register({
      username: formData.value.username.trim(),
      email: formData.value.email.trim(),
      password: formData.value.password,
      ...(guestId ? { guestId } : {})
    });

    if (!result.success) {
      generalError.value = result.message;
      return;
    }

    successMessage.value = '注册成功，正在跳转到登录页面...';
    if (guestId) {
      localStorage.removeItem('guestId');
      localStorage.removeItem('token');
    }
    window.setTimeout(() => router.push('/login'), 2000);
  } catch (error) {
    generalError.value = getApiErrorMessage(error, '注册失败，请稍后重试');
  } finally {
    isLoading.value = false;
  }
};
</script>
