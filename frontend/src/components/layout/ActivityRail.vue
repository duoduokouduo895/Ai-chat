<template>
  <aside class="activity-rail" aria-label="主导航">
    <button class="brand-mark" type="button" aria-label="Dduo AI-Chat 首页" @click="$emit('switch-view', 'chat')">
      <img src="/icon/OIP-C.webp" alt="" aria-hidden="true">
    </button>

    <nav class="rail-nav" aria-label="工作区">
      <button
        v-for="item in primaryItems"
        :key="item.id"
        class="rail-button"
        :class="{ active: item.id === currentView || (item.id === 'history' && historyOpen) }"
        type="button"
        :title="item.label"
        :aria-label="item.label"
        :aria-pressed="item.id === 'history' ? historyOpen : item.id === currentView"
        @click="handleItem(item.id)"
      >
        <font-awesome-icon :icon="item.icon" aria-hidden="true" />
      </button>
    </nav>

    <div class="rail-footer">
      <button class="rail-button" type="button" title="代码仓库" aria-label="打开代码仓库" @click="openRepository">
        <font-awesome-icon icon="code-branch" aria-hidden="true" />
      </button>
      <button class="rail-button" type="button" title="账号" aria-label="账号操作" @click="$emit('auth-action')">
        <font-awesome-icon :icon="isLoggedIn && !isGuest ? 'right-from-bracket' : 'right-to-bracket'" aria-hidden="true" />
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  currentView: { type: String, default: 'chat' },
  historyOpen: { type: Boolean, default: false },
  isGuest: { type: Boolean, default: false },
  isLoggedIn: { type: Boolean, default: false }
});

const emit = defineEmits(['switch-view', 'toggle-history', 'auth-action']);

const allPrimaryItems = [
  { id: 'chat', icon: 'comments', label: '聊天' },
  { id: 'history', icon: 'clock-rotate-left', label: '最近对话' },
  { id: 'search', icon: 'magnifying-glass', label: '搜索对话' },
  { id: 'knowledge', icon: 'book-open', label: '知识库' }
];

const primaryItems = computed(() => (
  props.isLoggedIn && !props.isGuest
    ? allPrimaryItems
    : allPrimaryItems.filter((item) => item.id === 'chat' || item.id === 'history')
));

const handleItem = (itemId) => {
  if (itemId === 'history') {
    emit('toggle-history');
    return;
  }
  emit('switch-view', itemId);
};

const openRepository = () => {
  window.open('https://gitee.com/duo-duo-kou-duo', '_blank', 'noopener,noreferrer');
};
</script>

<style scoped>
.activity-rail {
  align-items: center;
  background: var(--surface-1);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex: 0 0 var(--activity-rail-width);
  flex-direction: column;
  height: 100dvh;
  padding: 10px 8px;
  width: var(--activity-rail-width);
}

.brand-mark,
.rail-button {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--border-radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  display: inline-flex;
  height: 44px;
  justify-content: center;
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast);
  width: 44px;
}

.brand-mark {
  margin-bottom: 18px;
}

.brand-mark img {
  border-radius: 10px;
  height: 30px;
  object-fit: cover;
  width: 30px;
}

.rail-nav,
.rail-footer {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rail-footer {
  margin-top: auto;
}

.rail-button:hover,
.rail-button:focus-visible,
.brand-mark:hover,
.brand-mark:focus-visible {
  background: var(--surface-2);
  border-color: var(--border-subtle);
  color: var(--text-primary);
  outline: none;
}

.rail-button.active {
  background: var(--accent-soft);
  border-color: oklch(0.5 0.11 285);
  color: var(--text-accent);
}

@media (max-width: 768px) {
  .activity-rail {
    display: none;
  }
}
</style>
