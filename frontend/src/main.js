import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import userApiService from './services/userApi.js';
import { ElOption, ElSelect } from 'element-plus/es/components/select/index';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/option/style/css';
import 'element-plus/es/components/select/style/css';

import './assets/styles/variables.css';
import './assets/styles/common.css';
import './assets/styles/auth.css';
import { FontAwesomeIcon } from '../icon/icons.js';

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
  app.component('FontAwesomeIcon', FontAwesomeIcon);

  app.use(router);
  app.mount('#app');
}

initializeApp();
