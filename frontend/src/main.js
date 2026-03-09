import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import userApiService from './services/userApi.js';
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/option/style/css';
import 'element-plus/es/components/select/style/css';

import './assets/styles/variables.css';
import './assets/styles/common.css';

async function initializeApp() {
  const token = localStorage.getItem('token');

  if (token) {
    try {
      await userApiService.getMe();
      console.log('Token is valid. Proceeding with app mount.');
    } catch (error) {
      console.log('Token validation failed, global interceptor will handle redirection.');
    }
  }

  const app = createApp(App);

  app.component('ElSelect', ElSelect);
  app.component('ElOption', ElOption);
  app.config.globalProperties.$message = ElMessage;
  app.config.globalProperties.$msgbox = ElMessageBox;

  app.use(router);
  app.mount('#app');
}

initializeApp();
