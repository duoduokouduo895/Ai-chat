<template>
  <div class="message" :class="message.sender">
    <div class="avatar">
      <span v-if="message.sender === 'user'" class="user-avatar">👤</span>
      <img 
        v-else 
        src="/icon/OIP-C.webp" 
        alt="AI" 
        class="ai-avatar-img" 
      />
    </div>
    <div class="content">
      <MessageContent :text="message.text" />
      <div class="timestamp">{{ message.timestamp }}</div>
    </div>
  </div>
</template>

<script setup>
import MessageContent from './MessageContent.vue'

defineProps({
  message: {
    type: Object,
    required: true
  }
})
</script>

<style scoped>
.message {
  display: flex;
  gap: var(--spacing-md);
  max-width: 100%; /* 增加最大宽度，减少右侧空白 */
  animation: fadeIn 0.3s ease-in;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message.ai {
  align-self: flex-start;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--border-radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xl);
  flex-shrink: 0;
}

.message.user .avatar {
  background: linear-gradient(135deg, #b8d4f0 0%, #d6e8f5 100%);
  box-shadow: 0 4px 15px rgba(52, 73, 94, 0.15);
  border: 2px solid rgba(52, 73, 94, 0.1);
  transition: var(--transition-base);
}

.message.user .avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(52, 73, 94, 0.2);
}

.user-avatar {
  font-size: var(--font-size-lg);
  color: #2c3e50;
  text-shadow: 0 1px 3px rgba(255, 255, 255, 0.5);
  transition: var(--transition-base);
}

.message.user .avatar:hover .user-avatar {
  transform: scale(1.1);
  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
}

.ai-avatar-img {
  width: 32px;
  height: 32px;
  border-radius: var(--border-radius-full);
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 2px 8px rgba(155, 89, 182, 0.3);
  transition: var(--transition-base);
}

.ai-avatar-img:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 15px rgba(155, 89, 182, 0.5);
  border-color: rgba(255, 255, 255, 1);
}

.message.ai .avatar {
  background: linear-gradient(135deg, #9b59b6 0%, #8e44ad 100%);
  border: 2px solid rgba(155, 89, 182, 0.3);
  box-shadow: 0 4px 15px rgba(155, 89, 182, 0.3);
  transition: var(--transition-base);
}

.message.ai .avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(155, 89, 182, 0.5);
}

.content {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: var(--border-radius-xl);
  padding: var(--spacing-md) var(--spacing-lg);
  color: var(--text-white);
  line-height: 1.5;
  border: 1px solid rgba(255, 255, 255, 0.1);
  position: relative;
  text-align: left;
  user-select: text;
}

/* 用户消息样式 */
.message.user .content {
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f8ff 100%);
  color: #2c3e50;
  margin-left: auto;
  margin-right: 0;
  box-shadow: var(--shadow-sm);
  border: 1px solid rgba(52, 73, 94, 0.1);
  transition: var(--transition-base);
}

.message.user .content:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* AI消息样式 */
.message.ai .content {
  background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%);
  color: #2c3e50;
  margin-left: 0;
  margin-right: auto;
  border: 1px solid rgba(52, 73, 94, 0.1);
  box-shadow: var(--shadow-sm);
  transition: var(--transition-base);
}

.message.ai .content:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
  border-color: rgba(52, 73, 94, 0.15);
}

.timestamp {
  font-size: var(--font-size-xs);
  color: rgba(0, 0, 0, 0.5);
  margin-top: var(--spacing-xs);
  text-align: right;
  display: block;
  opacity: 1;
  visibility: visible;
}

.message.user .timestamp {
  color: rgba(0, 0, 0, 0.6);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }
}
</style>