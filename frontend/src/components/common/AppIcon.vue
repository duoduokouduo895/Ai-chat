<template>
  <svg
    v-if="definition"
    class="app-icon"
    :viewBox="`0 0 ${definition[0]} ${definition[1]}`"
    focusable="false"
    aria-hidden="true"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path fill="currentColor" :d="definition[2]" />
  </svg>
</template>

<script setup>
import { computed } from 'vue';
import { ICONS } from './iconPaths.js';

const props = defineProps({
  icon: { type: String, required: true }
});

const definition = computed(() => {
  const found = ICONS[props.icon];
  if (!found && import.meta.env?.DEV) {
    console.warn(`[AppIcon] 未注册的图标: ${props.icon}`);
  }
  return found || null;
});
</script>

<style scoped>
/* 与 Font Awesome 默认的 .svg-inline--fa 尺寸保持一致，替换后布局不变 */
.app-icon {
  display: inline-block;
  height: 1em;
  overflow: visible;
  vertical-align: -0.125em;
}
</style>
