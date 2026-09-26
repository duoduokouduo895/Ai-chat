import { createApp, nextTick } from 'vue';
import App from './App.vue';
import router from './router';
import { ElOption, ElSelect } from 'element-plus/es/components/select/index';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/option/style/css';
import 'element-plus/es/components/select/style/css';

import './assets/styles/variables.css';
import './assets/styles/common.css';
import './assets/styles/auth.css';
import { FontAwesomeIcon } from '../icon/icons.js';

const app = createApp(App);
app.component('ElSelect', ElSelect);
app.component('ElOption', ElOption);
app.component('FontAwesomeIcon', FontAwesomeIcon);
app.use(router);
app.mount('#app');

router.isReady().then(async () => {
  await nextTick();
  document.getElementById('startup-shell')?.remove();
}).catch((error) => {
  const shell = document.getElementById('startup-shell');
  shell?.setAttribute('aria-busy', 'false');
  const copy = shell?.querySelector('.startup-copy');
  if (copy) {
    copy.textContent = '页面加载失败，请刷新重试';
  }
  console.error(error);
});
