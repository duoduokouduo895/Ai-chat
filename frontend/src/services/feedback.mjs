import { reactive } from 'vue';

// 轻量的全局提示与对话框状态，由 FeedbackHost 渲染；可在组件外（服务层）调用
export const feedbackState = reactive({
  toasts: [],
  dialog: null
});

const TOAST_DURATION = { info: 3000, success: 3000, warning: 4000, error: 5000 };
const MAX_TOASTS = 4;
let nextId = 1;

export const dismissToast = (id) => {
  const index = feedbackState.toasts.findIndex((toast) => toast.id === id);
  if (index !== -1) feedbackState.toasts.splice(index, 1);
};

const pushToast = (type, message) => {
  const text = String(message ?? '').trim();
  if (!text) return null;

  // 同一条提示仍在显示时不重复堆叠
  if (feedbackState.toasts.some((toast) => toast.type === type && toast.message === text)) {
    return null;
  }

  const id = nextId++;
  feedbackState.toasts.push({ id, type, message: text });
  if (feedbackState.toasts.length > MAX_TOASTS) {
    feedbackState.toasts.splice(0, feedbackState.toasts.length - MAX_TOASTS);
  }
  setTimeout(() => dismissToast(id), TOAST_DURATION[type]);
  return id;
};

export const notify = {
  info: (message) => pushToast('info', message),
  success: (message) => pushToast('success', message),
  warning: (message) => pushToast('warning', message),
  error: (message) => pushToast('error', message)
};

const openDialog = (options, withCancel) => new Promise((resolve) => {
  // 新对话框出现时，之前未关闭的对话框视为取消
  feedbackState.dialog?.resolve(false);
  feedbackState.dialog = {
    id: nextId++,
    title: options.title || '提示',
    message: options.message || '',
    confirmText: options.confirmText || '确定',
    cancelText: withCancel ? (options.cancelText || '取消') : null,
    tone: options.tone || 'default',
    dismissible: options.dismissible !== false,
    resolve
  };
});

export const closeDialog = (confirmed) => {
  const dialog = feedbackState.dialog;
  if (!dialog) return;
  feedbackState.dialog = null;
  dialog.resolve(confirmed);
};

/** 确认框：确认返回 true，取消 / Esc / 点击遮罩返回 false */
export const confirmDialog = (options) => openDialog(options, true);

/** 提示框：用户确认后 resolve；dismissible: false 时只能通过按钮关闭 */
export const alertDialog = (options) => openDialog(options, false).then(() => undefined);
