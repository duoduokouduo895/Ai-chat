<template>
  <div id="app">
    <router-view />
    <FeedbackHost />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue';
import FeedbackHost from './components/common/FeedbackHost.vue';
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
  background: var(--canvas);
  color: var(--text-primary);
  margin: 0;
  padding: 0;
  min-height: 100%;
  width: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-text-size-adjust: 100%;
  -ms-text-size-adjust: 100%;
  -webkit-touch-callout: default;
  -webkit-user-select: text;
  -khtml-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
  user-select: text;
  touch-action: manipulation;
}

#app {
  min-height: 100%;
  width: 100%;
  font-family: var(--font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--text-primary);
  -webkit-tap-highlight-color: transparent;
  -webkit-touch-callout: default;
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
  body {
    -webkit-overflow-scrolling: touch;
  }
}

@media screen and (max-width: 768px) {
  html, body {
    min-height: 100vh;
    height: -webkit-fill-available;
  }

  #app {
    min-height: 100vh;
    height: -webkit-fill-available;
  }
}
</style>
