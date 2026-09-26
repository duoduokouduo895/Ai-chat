<template>
  <Transition name="drawer-layer" :duration="{ enter: 300, leave: 220 }">
  <div v-if="visible" class="mobile-drawer-layer">
    <button class="drawer-backdrop" type="button" aria-label="关闭菜单" @click="$emit('close')"></button>
    <aside class="mobile-drawer" aria-label="移动端菜单">
      <header>
        <div class="mobile-brand">
          <span class="brand-wordmark" aria-hidden="true">dduo</span>
          <strong>Dduo AI-Chat</strong>
        </div>
        <button class="icon-button" type="button" aria-label="关闭菜单" @click="$emit('close')">
          <font-awesome-icon icon="xmark" aria-hidden="true" />
        </button>
      </header>

      <nav class="mobile-nav" aria-label="工作区">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          :class="{ active: currentView === item.id }"
          @click="openView(item.id)"
        >
          <font-awesome-icon :icon="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <section class="mobile-history">
        <div class="section-heading">
          <strong>最近对话</strong>
          <button type="button" @click="$emit('new-conversation')">新建</button>
        </div>
        <p v-if="conversations.length === 0" class="empty-copy">发送消息后，会话会显示在这里。</p>
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          class="mobile-conversation"
          type="button"
          @click="$emit('open-conversation', conversation)"
        >
          <strong>{{ conversation.title || '新的对话' }}</strong>
          <span>{{ getConversationPreview(conversation) }}</span>
        </button>
      </section>

      <footer>
        <button type="button" @click="$emit('auth-action')">
          <font-awesome-icon :icon="isLoggedIn && !isGuest ? 'right-from-bracket' : 'right-to-bracket'" aria-hidden="true" />
          <span>{{ isLoggedIn && !isGuest ? '退出账号' : '登录账号' }}</span>
        </button>
      </footer>
    </aside>
  </div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue';
import { getConversationPreview } from '@/utils/conversationDisplay.js';

const props = defineProps({
  visible: { type: Boolean, default: false },
  currentView: { type: String, default: 'chat' },
  conversations: { type: Array, default: () => [] },
  isGuest: { type: Boolean, default: false },
  isLoggedIn: { type: Boolean, default: false }
});

const emit = defineEmits(['close', 'switch-view', 'new-conversation', 'open-conversation', 'auth-action']);

const allItems = [
  { id: 'chat', icon: 'comments', label: '聊天' },
  { id: 'search', icon: 'magnifying-glass', label: '搜索对话' },
  { id: 'knowledge', icon: 'book-open', label: '知识库' }
];

const items = computed(() => (
  props.isLoggedIn && !props.isGuest
    ? allItems
    : allItems.filter((item) => item.id === 'chat')
));

const openView = (view) => {
  emit('switch-view', view);
  emit('close');
};
</script>

<style scoped>
.mobile-drawer-layer {
  display: none;
}

@media (max-width: 768px) {
  .mobile-drawer-layer {
    display: block;
    inset: 0;
    position: fixed;
    z-index: var(--z-modal);
  }

  .drawer-backdrop {
    background: color-mix(in srgb, var(--canvas) 68%, #000);
    border: 0;
    inset: 0;
    position: absolute;
  }

  .mobile-drawer {
    background: var(--surface-1);
    border-right: 1px solid var(--border-subtle);
    display: flex;
    flex-direction: column;
    height: 100%;
    max-width: min(86vw, 320px);
    padding: 14px;
    position: relative;
    width: 100%;
  }

  header,
  .mobile-brand,
  .section-heading {
    align-items: center;
    display: flex;
    justify-content: space-between;
  }

  .mobile-brand {
    gap: 10px;
  }

  .brand-wordmark {
    font-family: var(--font-mono);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.08em;
  }

  .icon-button {
    align-items: center;
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--border-radius-md);
    color: var(--text-secondary);
    display: inline-flex;
    height: 44px;
    justify-content: center;
    width: 44px;
  }

  .mobile-nav {
    border-bottom: 1px solid var(--border-subtle);
    display: grid;
    gap: 4px;
    margin-top: 18px;
    padding-bottom: 14px;
  }

  .mobile-nav button,
  footer button {
    align-items: center;
    background: transparent;
    border: 1px solid transparent;
    border-radius: var(--border-radius-md);
    color: var(--text-secondary);
    display: flex;
    font: inherit;
    gap: 12px;
    min-height: 44px;
    padding: 0 12px;
  }

  .mobile-nav button.active {
    background: var(--accent-soft);
    border-color: var(--border-strong);
    color: var(--text-accent);
  }

  .mobile-history {
    flex: 1;
    overflow-y: auto;
    padding: 18px 0;
  }

  .section-heading {
    color: var(--text-secondary);
    font-size: var(--font-size-sm);
    padding: 0 6px 8px;
  }

  .section-heading button {
    background: transparent;
    border: 0;
    color: var(--text-accent);
  }

  .empty-copy {
    color: var(--text-muted);
    font-size: var(--font-size-xs);
    line-height: 1.6;
    padding: 0 6px;
  }

  .mobile-conversation {
    background: transparent;
    border: 0;
    border-radius: var(--border-radius-md);
    color: var(--text-secondary);
    display: grid;
    gap: 4px;
    padding: 11px 8px;
    text-align: left;
    width: 100%;
  }

  .mobile-conversation strong,
  .mobile-conversation span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-conversation span {
    color: var(--text-muted);
    font-size: var(--font-size-xs);
  }

  footer {
    border-top: 1px solid var(--border-subtle);
    padding-top: 10px;
  }

  /* 遮罩淡入，抽屉从左侧滑入；退场更短并使用 ease-in */
  .drawer-layer-enter-active .drawer-backdrop {
    transition: opacity 240ms linear;
  }

  .drawer-layer-enter-active .mobile-drawer {
    transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
  }

  .drawer-layer-leave-active .drawer-backdrop {
    transition: opacity 200ms linear;
  }

  .drawer-layer-leave-active .mobile-drawer {
    transition: transform 220ms cubic-bezier(0.4, 0, 1, 1);
  }

  .drawer-layer-enter-from .drawer-backdrop,
  .drawer-layer-leave-to .drawer-backdrop {
    opacity: 0;
  }

  .drawer-layer-enter-from .mobile-drawer,
  .drawer-layer-leave-to .mobile-drawer {
    transform: translateX(-100%);
  }
}
</style>
