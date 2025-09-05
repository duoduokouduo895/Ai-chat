import { createApp } from 'vue';
import App from './App.vue';
import router from './router'; // 引入路由
import userApiService from './services/userApi.js'; // 导入修正后的 userApiService
import { library } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'; // 确保正确导入 FontAwesomeIcon
import { fas } from '@fortawesome/free-solid-svg-icons';
import { far } from '@fortawesome/free-regular-svg-icons';
import { faPaw } from '@fortawesome/free-solid-svg-icons';
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 导入全局样式
import './assets/styles/variables.css'
import './assets/styles/common.css'

// 创建一个异步函数来初始化和挂载应用
async function initializeApp() {
    const token = localStorage.getItem('token');

    if (token) {
        try {
            // 应用启动时，使用 service 中的 getMe 方法验证 token 的有效性
            await userApiService.getMe();
            console.log('Token is valid. Proceeding with app mount.');
        } catch (error) {
            // 如果验证失败 (例如返回401)，全局拦截器会自动处理跳转和清理
            // 这里我们只需要捕获错误，防止应用启动中断即可
            console.log('Token validation failed, global interceptor will handle redirection.');
        }
    }

    // 在 token 验证完成后，再创建和挂载 Vue 应用
    const app = createApp(App);
    library.add(faPaw);
    library.add(fas, far);

    app.component('font-awesome-icon', FontAwesomeIcon); // 注册全局组件
    app.use(router);
    app.use(ElementPlus);
    app.mount('#app');
}

// 调用异步函数来启动应用
initializeApp();
