<template>
  <aside class="sidebar">
    <!-- 顶部导航图标 -->
    <nav class="sidebar-icons">
      <button
          class="icon-btn"
          :class="{ active: currentView === 'chat' }"
          @click="$emit('switchView', 'chat')"
          title="聊天"
      >
        💬
      </button>
      <button
          class="icon-btn"
          :class="{ active: currentView === 'knowledge' }"
          @click="$emit('switchView', 'knowledge')"
          title="知识库"
      >
        📚
      </button>
      <button
          class="icon-btn"
          :class="{ active: currentView === 'search' }"
          @click="$emit('switchView', 'search')"
          title="搜索"
      >
        🔍
      </button>
    </nav>

    <!-- 对话列表 -->
    <div class="conversations custom-scroll">
      <!-- 新对话按钮 -->
      <button class="conversation-item new-conversation" @click="$emit('newConversation')">
        <div class="conv-content">
          <div class="conv-title">+ 新的对话</div>
          <div class="conv-preview">开始新的对话</div>
        </div>
        <div class="conv-time">{{ new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</div>
      </button>

      <!-- 对话历史记录 -->
      <div v-if="conversations.length > 0" class="conversation-history">
        <div class="history-title">最近对话</div>
        <button
            v-for="conversation in conversations"
            :key="conversation.id"
            class="conversation-item"
            :class="{ active: String(conversationId) === String(conversation.conversationId || conversation.id) }"
            @click="$emit('openConversation', conversation)"
        >
          <div class="conv-content">
            <div class="conv-title">{{ conversation.title || 'New Conversation' }}</div>
            <div class="conv-preview">{{ getConversationPreview(conversation) }}</div>
          </div>
          <div class="conv-time">{{ formatTime(conversation.updatedAt) }}</div>
        </button>
      </div>

      <!-- 空白填充区域 -->
      <div class="conversation-spacer">
        <div v-if="conversations.length === 0" class="empty-state">
          <div class="empty-icon">💬</div>
          <div class="empty-text">暂无对话记录</div>
          <div class="empty-hint">点击上方按钮开始新对话</div>
        </div>
      </div>

      <!-- 使用提示 - 与对话列表同宽 -->
      <div v-if="conversations.length > 0 && conversations.length < 8" class="usage-tips">
        <div class="tips-title">💡 使用提示</div>
        <div class="tip-item">• 支持多模型对话</div>
        <div class="tip-item">• 可以搜索历史对话</div>
        <div class="tip-item">• 支持知识库问答</div>
      </div>
    </div>

    <!-- 底部操作 -->
    <footer class="sidebar-footer">
      <button class="icon-btn git" title="GitHub" @click="openGitHub">git</button>
      <button
          class="icon-btn login"
          :title="isUserLoggedIn ? '登出' : '登录'"
          @click="handleAuth"
      >
        {{ isUserLoggedIn ? '🚪' : '🔑' }}
      </button>
    </footer>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { userApiService } from '@/services/userApi.js';

// Props
const props = defineProps({
  currentView: {
    type: String,
    default: 'chat'
  },
  conversations: {
    type: Array,
    default: () => []
  },
  conversationId: {
    type: [String, Number],
    default: null
  }
});

// Emits
const emit = defineEmits([
  'switchView',
  'newConversation',
  'openConversation'
]);

const router = useRouter();

// 计算属性，用于判断用户是否为真实登录用户
const isUserLoggedIn = computed(() => {
  return userApiService.isLoggedIn();
});

// 处理认证操作
const handleAuth = async () => {
  if (isUserLoggedIn.value) {
    // 执行登出操作
    await userApiService.logout();
    // logout方法已经包含了跳转到登录页的逻辑
  } else {
    // 跳转到登录页
    router.push('/login');
  }
};

// 打开 GitHub 链接
const openGitHub = () => {
  window.open('https://gitee.com/duo-duo-kou-duo', '_blank');
};

// 工具方法
const formatTime = (timestamp) => {
  if (!timestamp) return '';

  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now - date;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) {
    return '刚刚';
  } else if (diffMins < 60) {
    return `${diffMins}分钟前`;
  } else if (diffHours < 24) {
    return `${diffHours}小时前`;
  } else if (diffDays < 7) {
    return `${diffDays}天前`;
  } else {
    return date.toLocaleDateString();
  }
};

const getConversationPreview = (conversation) => {
  if (!conversation) return 'New Conversation';

  if (conversation.firstUserMessage && conversation.firstUserMessage.trim()) {
    return conversation.firstUserMessage.trim();
  } else if (conversation.lastMessage && conversation.lastMessage.trim()) {
    return conversation.lastMessage.trim();
  } else {
    return 'New Conversation';
  }
};
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  background-color: var(--bg-sidebar);
  color: var(--text-white);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-dark);
  height: 100vh;
}

.sidebar-icons {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  border-bottom: 1px solid var(--border-dark);
}

.sidebar-icons .icon-btn:first-child {
  justify-self: start;
}

.sidebar-icons .icon-btn:nth-child(2) {
  justify-self: center;
}

.sidebar-icons .icon-btn:last-child {
  justify-self: end;
}

.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: var(--transition-normal);
  font-size: var(--font-size-lg);
  background-color: var(--bg-darker);
  border: none;
  color: var(--text-white);
}

.icon-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.icon-btn.active {
  background-color: rgba(255, 255, 255, 0.2);
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
}

.conversations {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  min-height: 0; /* 允许容器收缩 */
  position: relative;
  min-height: 500px; /* 增加最小高度 */
}

.conversation-item {
  position: relative;
  padding: var(--spacing-lg) var(--spacing-md); /* 增加上下内边距 */
  margin-bottom: var(--spacing-lg); /* 增加卡片间距 */
  border-radius: var(--border-radius-lg);
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: var(--transition-normal);
  border: 1px solid transparent;
  width: 100%;
  text-align: left;
  color: var(--text-white);
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  min-height: 80px; /* 设置最小高度 */
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.conversation-item.active {
  background-color: var(--primary-color);
  color: var(--text-white);
  border-color: rgba(255, 255, 255, 0.5);
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.3);
}

.conversation-item.new-conversation {
  background: linear-gradient(45deg, var(--success-color), var(--success-hover));
  border: 2px solid rgba(255, 255, 255, 0.3);
  font-weight: bold;
  min-height: 85px; /* 与普通对话卡片保持一致的高度 */
}

.conversation-item.new-conversation:hover {
  background: linear-gradient(45deg, var(--success-hover), var(--success-color));
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(76, 175, 80, 0.4);
}

.conv-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center; /* 垂直居中 */
  min-height: 60px; /* 保证内容区域有足够高度 */
}

.conv-title {
  font-weight: 700;
  font-size: var(--font-size-md);
  margin-bottom: var(--spacing-sm); /* 增加标题下间距 */
  color: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3; /* 调整行高 */
}

.conv-preview {
  font-size: var(--font-size-sm);
  color: var(--text-white-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.5; /* 增加行高 */
  margin-top: var(--spacing-xs); /* 添加上间距 */
}

.conv-time {
  position: absolute;
  bottom: var(--spacing-sm); /* 调整底部间距 */
  right: var(--spacing-sm); /* 调整右侧间距 */
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.5);
  background: rgba(0, 0, 0, 0.3);
  padding: 3px 8px; /* 轻微增加内边距 */
  border-radius: var(--border-radius-sm);
  backdrop-filter: blur(4px);
  font-weight: 500;
}

.conversation-history {
  margin-top: var(--spacing-xl);
}

.history-title {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: var(--spacing-md);
  padding-left: var(--spacing-xs);
  font-weight: 500;
}

.conversation-spacer {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--spacing-xl);
}

.empty-state {
  text-align: center;
  padding: var(--spacing-2xl);
  color: rgba(255, 255, 255, 0.6);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-lg);
  opacity: 0.7;
}

.empty-text {
  font-size: var(--font-size-md);
  font-weight: 500;
  margin-bottom: var(--spacing-sm);
  color: rgba(255, 255, 255, 0.8);
}

.empty-hint {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.5);
  line-height: 1.4;
}

.usage-tips {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-sm) var(--spacing-md);
  background: rgba(255, 255, 255, 0.08);
  border-radius: var(--border-radius-lg);
  border: 1px solid rgba(255, 255, 255, 0.15);
  width: 100%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(8px);
}

.tips-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: var(--spacing-xs);
  text-align: left;
}

.tip-item {
  font-size: var(--font-size-xs);
  color: rgba(255, 255, 255, 0.7);
  margin-bottom: 2px;
  text-align: left;
  line-height: 1.2;
  white-space: nowrap;
}

.sidebar-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--border-dark);
}

.sidebar-footer .icon-btn {
  width: 35px;
  height: 35px;
  font-size: var(--font-size-base);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    width: 100%;
    height: auto;
    max-height: 400px; /* 进一步增加移动端高度 */
  }

  .sidebar-icons {
    padding: var(--spacing-md);
  }

  .conversations {
    max-height: 280px; /* 增加移动端对话区域高度 */
    min-height: 280px;
  }

  .conversation-spacer {
    margin-top: var(--spacing-md);
  }

  .usage-tips {
    padding: var(--spacing-xs) var(--spacing-sm);
    margin-top: var(--spacing-sm);
  }

  .empty-state {
    padding: var(--spacing-lg);
  }

  .sidebar-footer {
    padding: var(--spacing-sm) var(--spacing-md);
  }
}
</style>
