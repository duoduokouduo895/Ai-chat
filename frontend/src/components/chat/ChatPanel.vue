<template>
  <section class="chat-panel">
    <header class="chat-toolbar" data-intro="frame">
      <div data-intro="title" :data-intro-text="conversationTitle">
        <span class="toolbar-eyebrow"><DecryptText text="Chat" /></span>
        <strong><DecryptText :text="conversationTitle" /></strong>
      </div>
      <button class="new-chat-action" type="button" @click="$emit('new-conversation')">
        <span aria-hidden="true">+</span>
        新建对话
      </button>
    </header>

    <div v-if="isEmptyConversation" class="empty-chat">
      <div class="empty-chat-content">
        <span class="brand-wordmark" aria-hidden="true">dduo</span>
        <h1 data-intro="title" data-intro-text="Dduo AI-Chat"><DecryptText text="Dduo AI-Chat" /></h1>
        <p data-intro="title" data-intro-text="选择模型，开始一段新的对话。"><DecryptText text="选择模型，开始一段新的对话。" /></p>
        <ChatInput
          v-model:message="inputMessage"
          v-model:selected-model="selectedModel"
          v-model:selected-knowledge-base-ids="selectedKnowledgeBaseIds"
          :available-models="availableModels"
          :is-loading="isLoading"
          immersive
          @send="handleSendMessage"
          @clear="$emit('clear-context')"
          @mode-change="chatMode = $event"
          @stop="$emit('stop-generation')"
        />
        <button class="intro-replay" type="button" @click="$emit('replay-intro')">重播开场 <span aria-hidden="true">↗</span></button>
      </div>
    </div>

    <template v-else>
      <div ref="messageContainer" class="chat-messages custom-scroll">
        <div class="message-column">
          <div v-if="isLoadingConversation" class="conversation-loading" role="status" aria-live="polite">
            <span class="conversation-loading-spinner"></span>
            <span>加载对话中</span>
          </div>
          <StateBlock
            v-else-if="loadError"
            type="error"
            title="对话加载失败"
            :message="loadError"
            action-text="开始新对话"
            @action="$emit('new-conversation')"
          />
          <template v-else>
            <div v-for="message in messages" :key="message.id" class="message-wrapper" :class="message.sender">
              <SystemMessage v-if="message.type === 'system'" :message="message.text" />
              <ChatMessage v-else :message="message" />
            </div>
          </template>
        </div>
      </div>

      <footer class="chat-composer-area" data-intro="divider">
        <ChatInput
          v-model:message="inputMessage"
          v-model:selected-model="selectedModel"
          v-model:selected-knowledge-base-ids="selectedKnowledgeBaseIds"
          :available-models="availableModels"
          :is-loading="isLoading"
          @send="handleSendMessage"
          @clear="$emit('clear-context')"
          @mode-change="chatMode = $event"
          @stop="$emit('stop-generation')"
        />
      </footer>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useGuestMessageGate } from '@/composables/useGuestMessageGate.js';
import ChatInput from './ChatInput.vue';
import ChatMessage from './ChatMessage.vue';
import SystemMessage from './SystemMessage.vue';
import StateBlock from '../common/StateBlock.vue';
import DecryptText from '../intro/DecryptText.vue';

const props = defineProps({
  messages: { type: Array, required: true },
  availableModels: { type: Array, default: () => [] },
  conversationId: { type: [String, Number], default: null },
  isLoading: { type: Boolean, default: false },
  isLoadingConversation: { type: Boolean, default: false },
  loadError: { type: String, default: '' }
});

const emit = defineEmits(['send-message', 'new-conversation', 'clear-context', 'stop-generation', 'replay-intro']);

const { canSendMessage } = useGuestMessageGate();
const messageContainer = ref(null);
const inputMessage = ref('');
const selectedModel = ref('chat-fast');
const selectedKnowledgeBaseIds = ref([]);
const chatMode = ref('normal');
let scrollFrame = 0;

const isEmptyConversation = computed(() => !props.isLoadingConversation && !props.loadError && props.messages.length === 0);
const conversationTitle = computed(() => chatMode.value === 'rag' ? '知识库问答' : '新的对话');

watch(() => props.availableModels, (models) => {
  if (!models.length) return;
  if (!models.some((model) => model.value === selectedModel.value)) {
    selectedModel.value = models[0].value;
  }
}, { immediate: true });

const handleSendMessage = (message, model, knowledgeBaseIds) => {
  if (!canSendMessage(knowledgeBaseIds)) {
    return;
  }

  emit('send-message', {
    message,
    model,
    conversationId: props.conversationId,
    ...(knowledgeBaseIds?.length > 0 ? {
      knowledgeBaseIds,
      maxRetrievedDocs: 5,
      minSimilarity: 0.3
    } : {})
  });
  inputMessage.value = '';
};

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollFrame) cancelAnimationFrame(scrollFrame);
    scrollFrame = requestAnimationFrame(() => {
      if (messageContainer.value) {
        messageContainer.value.scrollTop = messageContainer.value.scrollHeight;
      }
      scrollFrame = 0;
    });
  });
};

watch(() => props.messages, scrollToBottom, { deep: true });

onBeforeUnmount(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame);
});
</script>

<style scoped>
.intro-replay {
  display: block;
  margin: 14px auto 0;
  padding: 8px 12px;
  min-height: 44px;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  font: 12px var(--font-family);
  cursor: pointer;
}
.intro-replay span { margin-left: 6px; }
.intro-replay:hover { color: var(--text-primary); }
.intro-replay:focus-visible { outline: 2px solid var(--text-primary); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .intro-replay { display: none; } }

.chat-panel {
  background: var(--canvas);
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.chat-toolbar {
  align-items: center;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  justify-content: space-between;
  min-height: 58px;
  padding: 8px 18px;
}

.toolbar-eyebrow {
  color: var(--text-muted);
  display: block;
  font-size: var(--font-size-xs);
  letter-spacing: 0.12em;
  margin-bottom: 2px;
  text-transform: uppercase;
}

.chat-toolbar strong {
  font-size: var(--font-size-sm);
}

.new-chat-action {
  background: transparent;
  border: 1px solid var(--border-subtle);
  border-radius: var(--border-radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: var(--font-size-sm);
  min-height: 38px;
  padding: 0 12px;
}

.new-chat-action:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.empty-chat {
  align-items: flex-start;
  display: flex;
  flex: 1;
  justify-content: center;
  overflow-y: auto;
  padding: 36px 20px 12vh;
}

.empty-chat-content {
  margin-bottom: auto;
  margin-top: auto;
  max-width: 780px;
  text-align: center;
  width: 100%;
}

.brand-wordmark {
  color: var(--text-primary);
  display: block;
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.12em;
  margin-bottom: 12px;
}

.empty-chat h1 {
  font-size: 30px;
  letter-spacing: -0.03em;
  margin: 0;
}

.empty-chat p {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  margin: 8px 0 22px;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 18px;
}

.message-column {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: var(--chat-column-width);
}

.message-wrapper {
  display: flex;
}

.message-wrapper.user {
  justify-content: flex-end;
}

.conversation-loading {
  align-items: center;
  color: var(--text-secondary);
  display: inline-flex;
  font-size: var(--font-size-sm);
  gap: 8px;
  margin: 18px auto;
}

.conversation-loading-spinner {
  animation: spin 0.8s linear infinite;
  border: 2px solid var(--border-subtle);
  border-radius: 50%;
  border-top-color: var(--text-primary);
  height: 16px;
  width: 16px;
}

.chat-composer-area {
  border-top: 1px solid var(--border-subtle);
  padding: 14px 18px 18px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .chat-toolbar {
    display: none;
  }

  .empty-chat {
    padding: 24px 12px 8vh;
  }

  .empty-chat h1 {
    font-size: 26px;
  }

  .chat-messages {
    padding: 16px 12px;
  }

  .chat-composer-area {
    padding: 10px;
  }
}

@media (max-height: 560px) {
  .empty-chat {
    padding: 12px 20px 16px;
  }
  .brand-wordmark {
    font-size: 22px;
    margin-bottom: 6px;
  }
  .empty-chat p {
    margin: 6px 0 12px;
  }
  .intro-replay {
    margin-top: 8px;
  }
}

@media (max-width: 768px) and (max-height: 560px) {
  .empty-chat {
    padding: 8px 12px 12px;
  }
}
</style>
