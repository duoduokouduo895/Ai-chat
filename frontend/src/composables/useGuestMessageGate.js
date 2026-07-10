import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index';
import { ElMessageBox } from 'element-plus/es/components/message-box/index';
import {
  clearSessionState,
  getGuestMessageCount,
  incrementGuestMessageCount,
  isGuest
} from '@/services/sessionStore.mjs';

export function useGuestMessageGate() {
  const router = useRouter();

  const redirectToLogin = () => {
    clearSessionState();
    router.push('/login').catch(() => {
      window.location.href = '/login';
    });
  };

  const canSendMessage = (knowledgeBaseIds = []) => {
    if (!isGuest()) {
      return true;
    }

    if (knowledgeBaseIds?.length > 0) {
      ElMessage.warning('游客模式下无法使用知识库系统，请登录以继续使用。');
      return false;
    }

    const guestMessageCount = getGuestMessageCount();
    if (guestMessageCount >= 2) {
      ElMessageBox.alert('体验次数已用尽，请创建账户或登录后继续使用。', '游客体验已结束', {
        confirmButtonText: '前往登录',
        type: 'warning',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        callback: redirectToLogin
      });
      return false;
    }

    const nextCount = incrementGuestMessageCount();
    if (nextCount < 2) {
      ElMessage.info(`游客模式还可以发送 ${2 - nextCount} 条消息。`);
    }
    return true;
  };

  return {
    canSendMessage
  };
}
