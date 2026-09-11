<template>
  <section data-intro="input" data-intro-surface class="composer" :class="{ immersive }" aria-label="消息输入">
    <textarea
      ref="textareaRef"
      v-model="localMessage"
      class="composer-input"
      placeholder="开始新的聊天"
      aria-label="聊天消息"
      rows="1"
      @keydown.enter.ctrl="handleSend"
      @keydown.enter.exact="handleEnterKey"
    ></textarea>

    <div class="composer-footer" data-intro="frame" data-intro-surface>
      <div class="composer-tools">
        <ModelPills
          v-model="localSelectedModel"
          :models="availableModels"
        />
        <KnowledgeSelector
          v-model="knowledgeBaseIds"
          @mode-change="$emit('mode-change', $event)"
        />
      </div>

      <div class="composer-actions">
        <button class="quiet-action" type="button" title="清空上下文" aria-label="清空上下文" @click="$emit('clear')">
          清空
        </button>
        <button
          v-if="!isLoading"
          class="send-action"
          type="button"
          :disabled="!localMessage.trim()"
          aria-label="发送消息"
          @click="handleSend"
        >
          ↑
        </button>
        <button
          v-else
          class="stop-action"
          type="button"
          aria-label="停止生成"
          @click="$emit('stop')"
        >
          ■
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import ModelPills from './ModelPills.vue';
import KnowledgeSelector from '../knowledge/KnowledgeSelector.vue';

const props = defineProps({
  message: { type: String, default: '' },
  selectedModel: { type: String, default: 'chat-fast' },
  availableModels: { type: Array, default: () => [] },
  isLoading: { type: Boolean, default: false },
  selectedKnowledgeBaseIds: { type: Array, default: () => [] },
  immersive: { type: Boolean, default: false }
});

const emit = defineEmits([
  'update:message',
  'update:selectedModel',
  'update:selectedKnowledgeBaseIds',
  'send',
  'clear',
  'mode-change',
  'stop'
]);

const textareaRef = ref(null);
const knowledgeBaseIds = ref(props.selectedKnowledgeBaseIds || []);

const localMessage = computed({
  get: () => props.message,
  set: (value) => emit('update:message', value)
});

const localSelectedModel = computed({
  get: () => props.selectedModel,
  set: (value) => emit('update:selectedModel', value)
});

const handleSend = () => {
  if (!localMessage.value.trim() || props.isLoading) return;
  emit('send', localMessage.value.trim(), localSelectedModel.value, knowledgeBaseIds.value);
};

const handleEnterKey = (event) => {
  if (event.shiftKey) return;
  event.preventDefault();
  handleSend();
};

const adjustHeight = () => {
  nextTick(() => {
    if (!textareaRef.value) return;
    textareaRef.value.style.height = 'auto';
    textareaRef.value.style.height = `${Math.min(textareaRef.value.scrollHeight, 180)}px`;
  });
};

watch(localMessage, adjustHeight);
watch(knowledgeBaseIds, (value) => emit('update:selectedKnowledgeBaseIds', value), { deep: true });
</script>

<style scoped>
.composer {
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  margin: 0 auto;
  max-width: var(--chat-column-width);
  padding: 13px;
  width: 100%;
}

.composer:focus-within {
  border-color: oklch(0.57 0.14 285);
  box-shadow: 0 0 0 3px oklch(0.68 0.18 285 / 0.12);
}

.composer-input {
  background: transparent;
  border: 0;
  color: var(--text-primary);
  display: block;
  font: inherit;
  font-size: var(--font-size-base);
  line-height: 1.6;
  max-height: 180px;
  min-height: 52px;
  outline: none;
  padding: 2px 4px 10px;
  resize: none;
  width: 100%;
}

.composer-input::placeholder {
  color: var(--text-muted);
}

.composer-footer,
.composer-tools,
.composer-actions {
  align-items: center;
  display: flex;
}

.composer-footer {
  gap: 10px;
  justify-content: space-between;
}

.composer-tools {
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.composer-actions {
  gap: 6px;
}

.quiet-action,
.send-action,
.stop-action {
  align-items: center;
  border-radius: var(--border-radius-md);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: var(--font-size-xs);
  justify-content: center;
  min-height: 36px;
}

.quiet-action {
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-muted);
  padding: 0 8px;
}

.quiet-action:hover {
  background: var(--surface-3);
  color: var(--text-secondary);
}

.send-action,
.stop-action {
  border: 0;
  color: var(--text-white);
  font-size: 18px;
  height: 36px;
  width: 36px;
}

.send-action {
  background: var(--accent-color);
}

.send-action:disabled {
  background: var(--surface-3);
  color: var(--text-muted);
  cursor: not-allowed;
}

.stop-action {
  background: var(--danger-color);
  font-size: var(--font-size-xs);
}

@media (max-width: 768px) {
  .composer {
    border-radius: 12px;
    padding: 10px;
  }

  .composer-input {
    font-size: 16px;
    min-height: 44px;
  }

  .composer-footer {
    align-items: flex-end;
  }

  .composer-tools {
    gap: 6px;
  }

  .send-action,
  .stop-action {
    height: 44px;
    width: 44px;
  }
}
</style>
