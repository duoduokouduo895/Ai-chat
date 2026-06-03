<template>
  <article class="message" :class="message.sender">
    <div class="avatar" aria-hidden="true">
      <span v-if="message.sender === 'user'">你</span>
      <img v-else src="/icon/OIP-C.webp" alt="">
    </div>
    <div class="message-body">
      <MessageContent
        :text="message.text"
        :plain="message.sender === 'user'"
        :completed="message.completed !== false"
      />
      <time>{{ message.timestamp }}</time>
    </div>
  </article>
</template>

<script setup>
import MessageContent from './MessageContent.vue';

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

.message.user {
  align-self: end;
}

.avatar {
  align-items: center;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  color: var(--text-secondary);
  display: flex;
  flex: 0 0 34px;
  font-size: var(--font-size-xs);
  height: 34px;
  justify-content: center;
  position: absolute;
  right: calc(100% + 10px);
  top: 0;
  width: 34px;
}

.user .avatar {
  left: calc(100% + 10px);
  right: auto;
}

.avatar img {
  border-radius: 8px;
  height: 26px;
  object-fit: cover;
  width: 26px;
}

.message-body {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  color: var(--text-primary);
  line-height: 1.65;
  min-width: 0;
  padding: 12px 14px 8px;
}

.user .message-body {
  background: var(--accent-soft);
  border-color: oklch(0.49 0.1 285 / 0.75);
}

time {
  color: var(--text-muted);
  display: block;
  font-size: var(--font-size-xs);
  margin-top: 6px;
  text-align: right;
}

@media (max-width: 980px) {
  .avatar {
    display: none;
  }
}
</style>
