<template>
  <div ref="workspaceRoot" class="ai-platform" :inert="introBlocked || undefined"
    :aria-hidden="introBlocked || undefined" :data-intro-phase="introVisible ? introPhase : undefined"
    :style="{ opacity: workspaceVisibility }" tabindex="-1">
    <ActivityRail
      :current-view="currentView"
      :history-open="isHistoryDrawerOpen"
      :is-guest="isGuest"
      :is-logged-in="isLoggedIn"
      @switch-view="openView"
      @toggle-history="isHistoryDrawerOpen = !isHistoryDrawerOpen"
      @auth-action="handleAuthAction"
    />

    <HistoryDrawer
      v-if="isHistoryDrawerOpen"
      :visible="isHistoryDrawerOpen"
      :conversations="displayedConversations"
      :conversation-id="conversationId"
      @close="isHistoryDrawerOpen = false"
      @new-conversation="createConversation"
      @open-conversation="openConversation"
      @delete-conversation="deleteConversation"
    />

    <main class="main-content">
      <header class="mobile-workspace-header" data-intro="frame">
        <button type="button" aria-label="打开菜单" @click="isMobileDrawerOpen = true">
          <font-awesome-icon icon="bars" aria-hidden="true" />
        </button>
        <strong>Dduo AI-Chat</strong>
        <span>{{ viewLabel }}</span>
      </header>

      <ChatPanel
        v-if="currentView === 'chat'"
        :messages="messages"
        :available-models="availableModels"
        :conversation-id="conversationId"
        :is-loading="isLoading"
        :is-loading-conversation="isLoadingConversation"
        :load-error="loadError"
        @replay-intro="replayIntro"
        @send-message="handleSendMessage"
        @new-conversation="createConversation"
        @clear-context="clearContext"
        @stop-generation="stopGeneration"
      />

      <KnowledgePanel
        v-else-if="currentView === 'knowledge'"
        @close="openView('chat')"
      />

      <SearchPanel
        v-else-if="currentView === 'search'"
        @close="openView('chat')"
        @open-conversation="openConversation"
      />
    </main>

    <MobileDrawer
      :visible="isMobileDrawerOpen"
      :current-view="currentView"
      :conversations="displayedConversations"
      :is-guest="isGuest"
      :is-logged-in="isLoggedIn"
      @close="isMobileDrawerOpen = false"
      @switch-view="openView"
      @new-conversation="createConversation"
      @open-conversation="openConversation"
      @auth-action="handleAuthAction"
    />
  </div>
  <AsciiIntro v-if="introVisible" :key="introRun" :workspace="workspaceRoot"
    @phase="introPhase = $event" @reveal="workspaceVisibility = $event"
    @interactive="unlockWorkspace" @complete="finishIntro" />
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, ref } from 'vue';
import AsciiIntro from '../intro/AsciiIntro.vue';
import { introSession } from '../intro/introModel.mjs';
import { useChatWorkspace } from '@/composables/useChatWorkspace.js';
import ActivityRail from './ActivityRail.vue';
import HistoryDrawer from './HistoryDrawer.vue';
import MobileDrawer from './MobileDrawer.vue';
import ChatPanel from '../chat/ChatPanel.vue';

const KnowledgePanel = defineAsyncComponent(() => import('../knowledge/KnowledgePanel.vue'));
const SearchPanel = defineAsyncComponent(() => import('../search/SearchPanel.vue'));

const workspaceRoot = ref(null);
const introVisible = ref(introSession.shouldPlay(window.matchMedia('(prefers-reduced-motion: reduce)').matches));
const introBlocked = ref(introVisible.value);
const workspaceVisibility = ref(introVisible.value ? 0 : 1);
const introPhase = ref('sculpture');
const introRun = ref(0);
let replayFocus = null;

const unlockWorkspace = async () => {
  const focusWasInIntro = Boolean(document.activeElement?.closest('.ascii-intro'));
  introBlocked.value = false;
  await nextTick();
  if (focusWasInIntro) {
    const target = replayFocus?.isConnected ? replayFocus : workspaceRoot.value;
    target?.focus({ preventScroll: true });
  }
};
const finishIntro = () => {
  if (document.activeElement?.closest('.ascii-intro')) unlockWorkspace();
  introSession.finish();
  workspaceVisibility.value = 1;
  introVisible.value = false;
  introPhase.value = 'done';
};
const replayIntro = () => {
  if (introVisible.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  replayFocus = document.activeElement;
  introRun.value++;
  introBlocked.value = true;
  workspaceVisibility.value = 0;
  introPhase.value = 'sculpture';
  introVisible.value = true;
};

const isHistoryDrawerOpen = ref(false);
const isMobileDrawerOpen = ref(false);

const {
  availableModels,
  clearContext,
  conversationId,
  currentView,
  deleteConversation,
  displayedConversations,
  handleAuthAction,
  handleSendMessage,
  initWorkspace,
  isLoading,
  isLoadingConversation,
  isGuest,
  isLoggedIn,
  loadError,
  messages,
  openSearchedConversation,
  startNewConversation,
  stopGeneration,
  switchView
} = useChatWorkspace();

const viewLabel = computed(() => ({
  chat: '聊天',
  search: '搜索',
  knowledge: '知识库'
}[currentView.value] || '工作区'));

const openView = (view) => {
  switchView(view);
  isMobileDrawerOpen.value = false;
};

const createConversation = async () => {
  await startNewConversation();
  isHistoryDrawerOpen.value = false;
  isMobileDrawerOpen.value = false;
};

const openConversation = async (conversation) => {
  await openSearchedConversation(conversation);
  isHistoryDrawerOpen.value = false;
  isMobileDrawerOpen.value = false;
};

onMounted(initWorkspace);
</script>

<style scoped>
.ai-platform {
  background: var(--canvas);
  color: var(--text-primary);
  display: flex;
  font-family: var(--font-family);
  height: 100dvh;
  overflow: hidden;
}

.ai-platform:focus { outline: none; }

.main-content {
  background: var(--canvas);
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.mobile-workspace-header {
  display: none;
}

@media (max-width: 768px) {
  .mobile-workspace-header {
    align-items: center;
    background: var(--surface-1);
    border-bottom: 1px solid var(--border-subtle);
    display: flex;
    flex: 0 0 54px;
    gap: 12px;
    justify-content: space-between;
    padding: 5px 10px;
  }

  .mobile-workspace-header button {
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

  .mobile-workspace-header strong {
    font-size: var(--font-size-sm);
  }

  .mobile-workspace-header span {
    color: var(--text-muted);
    font-size: var(--font-size-xs);
    min-width: 44px;
    text-align: right;
  }
}

.ai-platform[data-intro-phase='falling'] :deep(.composer),
.ai-platform[data-intro-phase='field'] :deep(.composer),
.ai-platform[data-intro-phase='lock'] :deep(.composer),
.ai-platform[data-intro-phase='outline'] :deep(.composer) {
  transform: translateY(16px);
}

.ai-platform[data-intro-phase='reveal'] :deep(.composer),
.ai-platform[data-intro-phase='clearing'] :deep(.composer) {
  transform: none;
}

.ai-platform[data-intro-phase='reveal'] :deep(.composer) {
  animation: introComposerRise 0.7s ease both;
}

@keyframes introComposerRise {
  from { transform: translateY(16px); }
  to { transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .ai-platform :deep(.composer) {
    animation: none;
    transform: none;
  }
}
</style>
