<template>
  <div ref="workspaceRoot" class="ai-platform"
    :class="{ 'is-intro-held': introHeld, 'is-intro-revealing': introRevealing }"
    :inert="introBlocked || undefined"
    :aria-hidden="introBlocked || undefined" :data-intro-phase="introVisible ? introPhase : undefined"
    tabindex="-1">
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
      :visible="isHistoryDrawerOpen"
      :conversations="displayedConversations"
      :conversation-id="conversationId"
      @close="isHistoryDrawerOpen = false"
      @new-conversation="createConversation"
      @open-conversation="openConversation"
      @delete-conversation="deleteConversation"
    />

    <main class="main-content">
      <header class="mobile-workspace-header" data-intro="nav" data-intro-text="Dduo AI-Chat">
        <button type="button" aria-label="打开菜单" @click="isMobileDrawerOpen = true">
          <font-awesome-icon icon="bars" aria-hidden="true" />
        </button>
        <strong><DecryptText text="Dduo AI-Chat" /></strong>
        <span><DecryptText :text="viewLabel" /></span>
      </header>

      <Transition name="view-switch" mode="out-in">
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
      </Transition>
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
  <AsciiIntro v-if="introVisible" :key="introRun"
    :get-layout="collectIntroLayout"
    @phase="introPhase = $event" @reveal-start="startReveal"
    @complete="finishIntro" />
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import AsciiIntro from '../intro/AsciiIntro.vue';
import DecryptText from '../intro/DecryptText.vue';
import { introSession } from '../intro/introModel.mjs';
import { provideIntroReveal } from '../intro/introReveal.mjs';
import { readIntroLayout } from '../intro/workspaceRaster.mjs';
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
const workspaceRevealed = ref(!introVisible.value);
const decrypting = ref(false);
const introPhase = ref('idle');
const introRun = ref(0);
const introPaused = ref(false);
let replayFocus = null;

provideIntroReveal({
  decrypting: computed(() => decrypting.value),
  paused: computed(() => introPaused.value)
});

const collectIntroLayout = (rect) => readIntroLayout(workspaceRoot.value, rect);

const introHeld = computed(() => introVisible.value && !workspaceRevealed.value);
const introRevealing = computed(() => introVisible.value && workspaceRevealed.value);

const startReveal = () => {
  workspaceRevealed.value = true;
  decrypting.value = true;
};

const restoreFocus = async () => {
  await nextTick();
  const mobile = window.matchMedia('(max-width: 768px)').matches;
  if (replayFocus?.isConnected) {
    replayFocus.focus({ preventScroll: true });
    replayFocus = null;
    return;
  }
  if (!mobile && currentView.value === 'chat') {
    const input = workspaceRoot.value?.querySelector('textarea.composer-input');
    if (input) {
      input.focus({ preventScroll: true });
      return;
    }
  }
  workspaceRoot.value?.focus({ preventScroll: true });
};

const finishIntro = async () => {
  decrypting.value = false;
  introPaused.value = false;
  workspaceRevealed.value = true;
  introBlocked.value = false;
  introSession.finish();
  introVisible.value = false;
  introPhase.value = 'done';
  await restoreFocus();
};

const replayIntro = () => {
  if (introVisible.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  replayFocus = document.activeElement;
  introRun.value++;
  introBlocked.value = true;
  workspaceRevealed.value = false;
  decrypting.value = false;
  introPhase.value = 'idle';
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

const syncIntroPause = () => {
  introPaused.value = Boolean(introVisible.value && document.hidden);
};

onMounted(() => {
  initWorkspace();
  document.addEventListener('visibilitychange', syncIntroPause);
});
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', syncIntroPause);
});
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

.ai-platform.is-intro-held {
  opacity: 0;
  transition: none;
}

.ai-platform.is-intro-revealing {
  opacity: 1;
  transition: opacity 700ms cubic-bezier(1, 0.02, 0.37, 0.97);
}

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

/* 视图切换：旧视图快速淡出，新视图轻微上浮进入 */
.view-switch-enter-active {
  transition: opacity 200ms cubic-bezier(0.22, 1, 0.36, 1), transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
}

.view-switch-leave-active {
  transition: opacity 120ms cubic-bezier(0.4, 0, 1, 1);
}

.view-switch-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.view-switch-leave-to {
  opacity: 0;
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
    color: var(--text-secondary);
    font-size: var(--font-size-xs);
    min-width: 44px;
    text-align: right;
  }
}
</style>
