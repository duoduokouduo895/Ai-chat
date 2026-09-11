<template>
  <article class="message" :class="message.sender">
    <div class="avatar" aria-hidden="true">
      <span>{{ message.sender === 'user' ? '[YOU]' : '[AI]' }}</span>
    </div>
    <div class="message-body">
      <div v-if="!message.text && message.completed === false" class="message-status" role="status" aria-live="polite">
        <span class="status-dot"></span>
        <span>{{ message.status || '正在准备回答' }}</span>
      </div>
      <MessageContent
        v-else
        :text="message.text"
        :plain="message.sender === 'user'"
        :completed="message.completed !== false"
      />
      <ChatSources v-if="message.retrieval" :retrieval="message.retrieval" />
      <time>{{ message.timestamp }}</time>
    </div>
  </article>
</template>

<script setup>
import MessageContent from './MessageContent.vue';
import ChatSources from './ChatSources.vue';

defineProps({
  message: { type: Object, required: true }
});
</script>

<style scoped>
.message {
  display: flex;
  gap: 10px;
  max-width: min(100%, 72ch);
  position: relative;
}

.message:not(.user) {
  width: min(100%, 72ch);
}

.message.user {
  align-self: end;
}

.avatar {
  align-items: center;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-secondary);
  display: flex;
  flex: 0 0 34px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  height: 34px;
  justify-content: center;
  letter-spacing: 0.02em;
  position: absolute;
  right: calc(100% + 10px);
  top: 0;
  width: 34px;
}

.user .avatar {
  left: calc(100% + 10px);
  right: auto;
}

.message-body {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  color: var(--text-primary);
  line-height: 1.65;
  min-width: 0;
  padding: 12px 14px 8px;
}

.message:not(.user) .message-body {
  min-height: 58px;
  width: 100%;
}

.user .message-body {
  background: var(--accent-soft);
  border-color: var(--border-strong);
}

time {
  color: var(--text-muted);
  display: block;
  font-size: var(--font-size-xs);
  margin-top: 6px;
  text-align: right;
}

.message-status {
  align-items: center;
  color: var(--text-secondary);
  display: inline-flex;
  font-size: var(--font-size-sm);
  gap: 8px;
  min-height: 28px;
}

.status-dot {
  animation: statusPulse 1s ease-in-out infinite;
  background: var(--text-primary);
  border-radius: 999px;
  height: 7px;
  width: 7px;
}

@keyframes statusPulse {
  0%, 100% {
    opacity: 0.45;
    transform: scale(0.9);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 980px) {
  .avatar {
    display: none;
  }
}
</style>
