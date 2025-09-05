<template>
  <div class="register-container">
    <div class="register-card">
      <div class="register-header">
        <h2 class="register-title">✨ Dduo AI-Chat</h2>
        <p class="register-subtitle">创建您的账户</p>
      </div>
      
      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <label for="username" class="form-label">用户名</label>
          <input
            id="username"
            v-model="formData.username"
            type="text"
            class="form-input"
            :class="{ 'error': errors.username }"
            placeholder="请输入用户名（3-20个字符）"
            @blur="validateUsername"
            @input="clearError('username')"
          />
          <span v-if="errors.username" class="error-message">{{ errors.username }}</span>
        </div>
        
        <div class="form-group">
          <label for="email" class="form-label">邮箱</label>
          <input
            id="email"
            v-model="formData.email"
            type="email"
            class="form-input"
            :class="{ 'error': errors.email }"
            placeholder="请输入邮箱地址"
            @blur="validateEmail"
            @input="clearError('email')"
          />
          <span v-if="errors.email" class="error-message">{{ errors.email }}</span>
        </div>
        
        <div class="form-group">
          <label for="password" class="form-label">密码</label>
          <input
            id="password"
            v-model="formData.password"
            type="password"
            class="form-input"
            :class="{ 'error': errors.password }"
            placeholder="请输入密码（至少6个字符）"
            @blur="validatePassword"
            @input="clearError('password')"
          />
          <span v-if="errors.password" class="error-message">{{ errors.password }}</span>
        </div>
        
        <div class="form-group">
          <label for="confirmPassword" class="form-label">确认密码</label>
          <input
            id="confirmPassword"
            v-model="formData.confirmPassword"
            type="password"
            class="form-input"
            :class="{ 'error': errors.confirmPassword }"
            placeholder="请再次输入密码"
            @blur="validateConfirmPassword"
            @input="clearError('confirmPassword')"
          />
          <span v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</span>
        </div>
        
        <div v-if="generalError" class="general-error">
          {{ generalError }}
        </div>
        
        <div v-if="successMessage" class="success-message">
          {{ successMessage }}
        </div>
        
        <button
          type="submit"
          class="register-button"
          :disabled="isLoading || !isFormValid"
        >
          <span v-if="isLoading" class="loading-spinner"></span>
          {{ isLoading ? '注册中...' : '注册' }}
        </button>
      </form>
      
      <div class="register-footer">
        <p class="login-link">
          已有账户？
          <router-link to="/login" class="link">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { userApiService } from '../../services/userApi.js';

export default {
  name: 'Register',
  setup() {
    const router = useRouter();
    
    // 表单数据
    const formData = ref({
      username: '',
      email: '',
      password: '',
      confirmPassword: ''
    });
    
    // 错误信息
    const errors = ref({});
    const generalError = ref('');
    const successMessage = ref('');
    const isLoading = ref(false);
    
    // 验证规则
    const validateUsername = () => {
      const username = formData.value.username.trim();
      if (!username) {
        errors.value.username = '请输入用户名';
        return false;
      }
      if (username.length < 3 || username.length > 20) {
        errors.value.username = '用户名长度应为3-20个字符';
        return false;
      }
      if (!/^[a-zA-Z0-9_]{3,20}$/.test(username)) {
        errors.value.username = '用户名只能包含字母、数字和下划线';
        return false;
      }
      delete errors.value.username;
      return true;
    };
    
    const validateEmail = () => {
      const email = formData.value.email.trim();
      if (!email) {
        errors.value.email = '请输入邮箱地址';
        return false;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        errors.value.email = '请输入有效的邮箱地址';
        return false;
      }
      delete errors.value.email;
      return true;
    };
    
    const validatePassword = () => {
      const password = formData.value.password;
      if (!password) {
        errors.value.password = '请输入密码';
        return false;
      }
      if (password.length < 6 || password.length > 50) {
        errors.value.password = '密码长度应为6-50个字符';
        return false;
      }
      if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,50}$/.test(password)) {
        errors.value.password = '密码必须包含字母和数字';
        return false;
      }
      delete errors.value.password;
      return true;
    };
    
    const validateConfirmPassword = () => {
      const confirmPassword = formData.value.confirmPassword;
      if (!confirmPassword) {
        errors.value.confirmPassword = '请确认密码';
        return false;
      }
      if (confirmPassword !== formData.value.password) {
        errors.value.confirmPassword = '两次输入的密码不一致';
        return false;
      }
      delete errors.value.confirmPassword;
      return true;
    };
    
    // 清除错误信息
    const clearError = (field) => {
      delete errors.value[field];
      generalError.value = '';
      successMessage.value = '';
    };
    
    // 表单验证状态
    const isFormValid = computed(() => {
      return formData.value.username.trim() && 
             formData.value.email.trim() && 
             formData.value.password && 
             formData.value.confirmPassword &&
             Object.keys(errors.value).length === 0;
    });
    
    // 处理注册
    const handleRegister = async () => {
      // 验证表单
      const isUsernameValid = validateUsername();
      const isEmailValid = validateEmail();
      const isPasswordValid = validatePassword();
      const isConfirmPasswordValid = validateConfirmPassword();
      
      if (!isUsernameValid || !isEmailValid || !isPasswordValid || !isConfirmPasswordValid) {
        return;
      }
      
      isLoading.value = true;
      generalError.value = '';
      successMessage.value = '';
      
      try {
        // 准备提交的数据
        const payload = {
          username: formData.value.username.trim(),
          email: formData.value.email.trim(),
          password: formData.value.password
        };

        // 检查是否存在guestId，如果存在则添加到payload中
        const guestId = localStorage.getItem('guestId');
        if (guestId) {
          payload.guestId = guestId;
        }

        const result = await userApiService.register(payload);
        
        if (result.success) {
          successMessage.value = '注册成功！正在跳转到登录页面...';
          
          // 注册成功后，清除游客ID和Token，确保完全退出游客状态
          if (guestId) {
            localStorage.removeItem('guestId');
            localStorage.removeItem('token'); // 关键：清除旧的游客token
          }

          // 延迟跳转到登录页面
          setTimeout(() => {
            router.push('/login');
          }, 2000);
        } else {
          generalError.value = result.message;
        }
      } catch (error) {
        generalError.value = '网络错误，请稍后重试';
      } finally {
        isLoading.value = false;
      }
    };
    
    return {
      formData,
      errors,
      generalError,
      successMessage,
      isLoading,
      isFormValid,
      validateUsername,
      validateEmail,
      validatePassword,
      validateConfirmPassword,
      clearError,
      handleRegister
    };
  }
};
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f4ff 0%, #ffffff 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.register-container::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(59,130,246,0.1) 0%, transparent 70%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.register-card {
  background: linear-gradient(145deg, #ffffff, #f8f9fa);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.08);
  padding: 48px 40px;
  width: 100%;
  max-width: 420px;
  border: none;
  opacity: 0;
  transform: translateY(20px);
  animation: fadeInUp 0.6s ease-out forwards;
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 12px 0;
}

.register-subtitle {
  font-size: 18px;
  color: #4b5563;
  margin: 0 0 32px 0;
}

.register-form {
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 14px 20px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  font-size: 16px;
  transition: all 0.3s ease;
  box-sizing: border-box;
  background-color: #f9fafb;
}

.form-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
  background-color: #ffffff;
}

.form-input.error {
  border-color: #ef4444;
  animation: shake 0.5s;
}

.form-input.error:focus {
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
}

.error-message {
  display: block;
  color: #ef4444;
  font-size: 13px;
  margin-top: 6px;
  opacity: 0;
  animation: fadeIn 0.3s ease-in forwards;
}

.general-error {
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 20px;
  text-align: center;
}

.success-message {
  background-color: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 20px;
  text-align: center;
}

.register-button {
  width: 100%;
  background-color: #3b82f6;
  color: #ffffff;
  border: none;
  padding: 16px 24px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
}

.register-button:hover:not(:disabled) {
  background-color: #2563eb;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.3);
}

.register-button:disabled {
  background-color: #6c757d;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top: 2px solid #ffffff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

@keyframes shake {
  0% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  50% { transform: translateX(4px); }
  75% { transform: translateX(-4px); }
  100% { transform: translateX(0); }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.register-footer {
  text-align: center;
}

.login-link {
  color: #6b7280;
  font-size: 14px;
  margin: 0;
}

.link {
  color: #007bff;
  text-decoration: none;
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .register-container {
    padding: 16px;
  }
  
  .register-card {
    padding: 32px 24px;
    max-width: 100%;
    border-radius: 16px;
  }
  
  .register-title {
    font-size: 28px;
  }
  
  .form-input {
    padding: 12px 16px;
  }
  
  .register-button {
    padding: 14px 20px;
  }
}

@media (min-width: 481px) and (max-width: 768px) {
  .register-card {
    max-width: 500px;
    padding: 48px;
  }
}
</style>