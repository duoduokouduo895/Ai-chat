<template>
  <aside class="history-drawer" :class="{ open: visible }" :aria-hidden="!visible" :inert="!visible || undefined">
    <div class="drawer-panel">
    <header class="drawer-header">
      <div>
        <span class="eyebrow">Workspace</span>
        <h2>最近对话</h2>
      </div>
      <button class="icon-button" type="button" aria-label="关闭最近对话" @click="$emit('close')">
        <app-icon icon="xmark" aria-hidden="true" />
      </button>
    </header>

    <button class="new-chat-button" type="button" @click="$emit('new-conversation')">
      <app-icon icon="plus" aria-hidden="true" />
      <span>开始新对话</span>
    </button>

    <div class="drawer-content custom-scroll">
      <div v-if="conversations.length === 0" class="drawer-empty">
        <app-icon icon="message" aria-hidden="true" />
        <p>还没有历史对话</p>
        <span>发送第一条消息后，会话会显示在这里。</span>
      </div>
      <div v-else class="conversation-list">
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-row"
          :class="{ active: String(conversationId) === String(conversation.conversationId || conversation.id) }"
        >
          <button class="conversation-main" type="button" @click="$emit('open-conversation', conversation)">
            <strong>{{ conversation.title || '新的对话' }}</strong>
            <span>{{ getConversationPreview(conversation) }}</span>
            <small>{{ formatConversationTime(conversation.updatedAt || conversation.createdAt) }}</small>
          </button>
          <button
            class="delete-button"
            type="button"
            :aria-label="`删除对话 ${conversation.title || '新的对话'}`"
            @click="$emit('delete-conversation', conversation)"
          >
            <app-icon icon="trash-can" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
    </div>
  </aside>
</template>

<script setup>
import { formatConversationTime, getConversationPreview } from '@/utils/conversationDisplay.js';

defineProps({
  visible: { type: Boolean, default: false },
  conversations: { type: Array, default: () => [] },
  conversationId: { type: [String, Number], default: null }
});

defineEmits(['close', 'new-conversation', 'open-conversation', 'delete-conversation']);
</script>

<style scoped>
/* 外层只负责让出宽度，内容面板固定宽度并用 transform 滑入，避免内部文字随宽度重排 */
.history-drawer {
  --drawer-ease: cubic-bezier(0.22, 1, 0.36, 1);
  flex: 0 0 0;
  height: 100dvh;
  overflow: hidden;
  pointer-events: none;
  transition: flex-basis 200ms cubic-bezier(0.4, 0, 1, 1), visibility 0s linear 200ms;
  visibility: hidden;
}

.history-drawer.open {
  flex-basis: var(--history-drawer-width);
  pointer-events: auto;
  transition: flex-basis 280ms var(--drawer-ease), visibility 0s;
  visibility: visible;
}

.drawer-panel {
  background: var(--surface-1);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  height: 100%;
  opacity: 0;
  transform: translateX(-24px);
  transition: opacity 140ms linear, transform 200ms cubic-bezier(0.4, 0, 1, 1);
  width: var(--history-drawer-width);
}

.history-drawer.open .drawer-panel {
  opacity: 1;
  transform: none;
  transition: opacity 200ms linear 60ms, transform 280ms var(--drawer-ease);
}

.drawer-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  padding: 18px 16px 12px;
  min-width: var(--history-drawer-width);
}

.eyebrow {
  color: var(--text-muted);
  display: block;
  font-size: var(--font-size-xs);
  letter-spacing: 0.12em;
  margin-bottom: 5px;
  text-transform: uppercase;
}

h2 {
  font-size: var(--font-size-lg);
  margin: 0;
}

.icon-button,
.delete-button {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--border-radius-md);
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  height: 44px;
  justify-content: center;
  width: 44px;
}

.icon-button:hover,
.delete-button:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.new-chat-button {
  align-items: center;
  background: var(--accent-color);
  border: 0;
  border-radius: var(--border-radius-md);
  color: var(--text-on-accent);
  cursor: pointer;
  display: flex;
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 650;
  gap: 8px;
  justify-content: center;
  margin: 0 16px 12px;
  min-height: 42px;
  min-width: 256px;
}

.drawer-content {
  min-width: var(--history-drawer-width);
  overflow-y: auto;
  padding: 6px 10px 16px;
}

.drawer-empty {
  color: var(--text-muted);
  padding: 52px 14px;
  text-align: center;
}

.drawer-empty p {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  margin: 14px 0 6px;
}

.drawer-empty span {
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.conversation-list {
  display: grid;
  gap: 4px;
}

.conversation-row {
  align-items: center;
  border: 1px solid transparent;
  border-radius: var(--border-radius-md);
  display: flex;
}

.conversation-row:hover {
  background: var(--surface-2);
}

.conversation-row.active {
  background: var(--accent-soft);
  border-color: var(--border-strong);
}

.conversation-main {
  background: transparent;
  border: 0;
  color: var(--text-secondary);
  cursor: pointer;
  display: grid;
  flex: 1;
  gap: 4px;
  min-width: 0;
  padding: 11px 4px 11px 10px;
  text-align: left;
}

.conversation-main strong,
.conversation-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-main strong {
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.conversation-main span,
.conversation-main small {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.delete-button {
  flex: 0 0 36px;
  height: 36px;
  opacity: 0;
  width: 36px;
}

.conversation-row:hover .delete-button,
.delete-button:focus-visible {
  opacity: 1;
}

@media (max-width: 768px) {
  .history-drawer {
    display: none;
  }
}
</style>
