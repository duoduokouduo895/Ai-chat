<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue';
import { userApiService } from './services/userApi.js';
import heartbeatService from './services/heartbeatService.js';
import guestSessionService from './services/guestSessionService.js';

const syncHeartbeat = () => {
  if (!userApiService.isLoggedIn()) {
    heartbeatService.stop();
    guestSessionService.stop();
    return;
  }

  guestSessionService.syncWithAppState();
  heartbeatService.syncWithAppState();
};

const handleVisibilityChange = () => {
  syncHeartbeat();
};

onMounted(() => {
  syncHeartbeat();
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onBeforeUnmount(() => {
  heartbeatService.stop();
  guestSessionService.stop();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<style>
html, body {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  overflow: hidden;
  -webkit-text-size-adjust: 100%;
  -ms-text-size-adjust: 100%;
  -webkit-touch-callout: none;
  -webkit-user-select: none;
  -khtml-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
  touch-action: manipulation;
}

#app {
  height: 100%;
  width: 100%;
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  -webkit-tap-highlight-color: transparent;
  -webkit-touch-callout: none;
}

* {
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}

input, textarea, [contenteditable] {
  -webkit-user-select: text;
  -khtml-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
  user-select: text;
}

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
