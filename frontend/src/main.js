import { createApp, nextTick } from 'vue';
import App from './App.vue';
import router from './router';

import './assets/styles/variables.css';
import './assets/styles/common.css';
import './assets/styles/auth.css';
import { FontAwesomeIcon } from '../icon/icons.js';

const app = createApp(App);
app.component('FontAwesomeIcon', FontAwesomeIcon);
app.use(router);
app.mount('#app');

// 骨架屏淡出后再移除；transitionend 不触发（如减少动态效果）时由定时器兜底
const removeStartupShell = () => {
  const shell = document.getElementById('startup-shell');
  if (!shell) return;
  shell.setAttribute('aria-busy', 'false');
  shell.classList.add('is-leaving');
  const remove = () => shell.remove();
  shell.addEventListener('transitionend', remove, { once: true });
  setTimeout(remove, 400);
};

router.isReady().then(async () => {
  await nextTick();
  removeStartupShell();
}).catch((error) => {
  const shell = document.getElementById('startup-shell');
  shell?.setAttribute('aria-busy', 'false');
  const copy = shell?.querySelector('.startup-copy');
  if (copy) {
    copy.textContent = '页面加载失败，请刷新重试';
  }
  console.error(error);
});
