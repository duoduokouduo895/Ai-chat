<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue';
import { userApiService } from './services/userApi.js';

let heartbeatInterval = null;

// 心跳函数，用于保持会话活跃
const startHeartbeat = () => {
  // 立即执行一次，然后每4分钟执行一次
  console.log('Starting session heartbeat...');
  
  // 先停止任何可能存在的旧定时器
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval);
  }

  const performHeartbeat = () => {
    // 只有当用户是登录状态时才发送心跳
    if (userApiService.isLoggedIn()) {
      console.log('Sending heartbeat to keep session alive...');
      userApiService.getMe().catch(error => {
        // 错误会被全局拦截器处理，这里只打印日志用于调试
        console.error('Heartbeat failed:', error.message);
      });
    }
  };

  performHeartbeat(); // 立即执行一次
  heartbeatInterval = setInterval(performHeartbeat, 4 * 60 * 1000); // 4分钟
};

const stopHeartbeat = () => {
  if (heartbeatInterval) {
    console.log('Stopping session heartbeat.');
    clearInterval(heartbeatInterval);
    heartbeatInterval = null;
  }
};

// 处理页面可见性变化
const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    // 页面变为可见，如果用户已登录，则启动心跳
    if (userApiService.isLoggedIn()) {
      startHeartbeat();
    }
  } else {
    // 页面变为不可见，停止心跳以节省资源
    stopHeartbeat();
  }
};

onMounted(async () => {
  // 初始加载时，检查用户是否已登录
  if (userApiService.isLoggedIn()) {
    // 如果已登录，则启动心跳
    startHeartbeat();
  }
  // 添加事件监听器
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onBeforeUnmount(() => {
  // 组件卸载时，停止心跳并移除监听器
  stopHeartbeat();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style>
/* 重置一些默认样式，确保组件全屏显示 */
html, body {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  overflow: hidden; /* 防止出现滚动条 */
  /* 移动端优化 */
  -webkit-text-size-adjust: 100%;
  -ms-text-size-adjust: 100%;
  -webkit-touch-callout: none;
  -webkit-user-select: none;
  -khtml-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
  /* 禁用双击缩放 */
  touch-action: manipulation;
}

#app {
  height: 100%;
  width: 100%;
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  /* 移动端触摸优化 */
  -webkit-tap-highlight-color: transparent;
  -webkit-touch-callout: none;
}

/* 全局移动端优化 */
* {
  box-sizing: border-box;
  /* 移动端触摸优化 */
  -webkit-tap-highlight-color: transparent;
}

/* 可选择文本的元素 */
input, textarea, [contenteditable] {
  -webkit-user-select: text;
  -khtml-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
  user-select: text;
}

/* 移动端响应式设计 */
@media (max-width: 768px) {
  html {
    font-size: 14px;
  }
  
  body {
    -webkit-overflow-scrolling: touch;
  }
}

@media (max-width: 480px) {
  html {
    font-size: 13px;
  }
}

/* 移动端视口优化 */
@media screen and (max-width: 768px) {
  html, body {
    height: 100vh;
    height: -webkit-fill-available;
  }
  
  #app {
    height: 100vh;
    height: -webkit-fill-available;
  }
}
</style>

<!--
<style>
#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
-->