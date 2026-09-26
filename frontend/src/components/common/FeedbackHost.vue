<template>
  <div class="toast-stack" aria-live="polite" aria-relevant="additions">
    <TransitionGroup name="toast">
      <div
        v-for="toast in feedbackState.toasts"
        :key="toast.id"
        class="toast"
        :class="toast.type"
        :role="toast.type === 'error' ? 'alert' : 'status'"
      >
        <span class="toast-mark" aria-hidden="true"></span>
        <span class="toast-message">{{ toast.message }}</span>
        <button class="toast-close" type="button" aria-label="关闭提示" @click="dismissToast(toast.id)">×</button>
      </div>
    </TransitionGroup>
  </div>

  <Transition name="dialog">
    <div v-if="dialog" class="dialog-layer" @click.self="dismiss">
      <div
        ref="dialogRef"
        class="dialog"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="`dialog-title-${dialog.id}`"
        :aria-describedby="`dialog-message-${dialog.id}`"
        tabindex="-1"
      >
        <h2 :id="`dialog-title-${dialog.id}`">{{ dialog.title }}</h2>
        <p :id="`dialog-message-${dialog.id}`">{{ dialog.message }}</p>
        <div class="dialog-actions">
          <button
            v-if="dialog.cancelText"
            ref="cancelRef"
            class="dialog-button secondary"
            type="button"
            @click="closeDialog(false)"
          >
            {{ dialog.cancelText }}
          </button>
          <button
            ref="confirmRef"
            class="dialog-button"
            :class="{ danger: dialog.tone === 'danger' }"
            type="button"
            @click="closeDialog(true)"
          >
            {{ dialog.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { computed, ref } from 'vue';
import { closeDialog, dismissToast, feedbackState } from '@/services/feedback.mjs';
import { useModalDialog } from '@/composables/useModalDialog.js';

const dialog = computed(() => feedbackState.dialog);
const dialogRef = ref(null);
const cancelRef = ref(null);
const confirmRef = ref(null);

// 危险操作默认聚焦“取消”，避免回车误删
const initialFocusRef = computed(() => (
  dialog.value?.tone === 'danger' && cancelRef.value ? cancelRef.value : confirmRef.value
));

const dismiss = () => {
  if (dialog.value?.dismissible) closeDialog(false);
};

useModalDialog({
  isOpen: computed(() => Boolean(dialog.value)),
  dialogRef,
  onClose: dismiss,
  initialFocusRef
});
</script>

<style scoped>
.toast-stack {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
  left: 50%;
  max-width: calc(100vw - 32px);
  pointer-events: none;
  position: fixed;
  top: 16px;
  transform: translateX(-50%);
  width: max-content;
  z-index: var(--z-tooltip);
}

.toast {
  align-items: center;
  background: var(--surface-2);
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  box-shadow: 0 8px 24px color-mix(in srgb, #000 30%, transparent);
  color: var(--text-primary);
  display: flex;
  font-size: var(--font-size-sm);
  gap: 10px;
  max-width: 480px;
  padding: 8px 8px 8px 12px;
  pointer-events: auto;
}

.toast.error { background: var(--danger-bg); color: var(--danger-text); }
.toast.warning { background: var(--warning-bg); color: var(--warning-text); }
.toast.success { background: var(--success-bg); color: var(--success-text); }

.toast-mark {
  background: var(--info-color);
  border-radius: 999px;
  flex: 0 0 8px;
  height: 8px;
}

.toast.error .toast-mark { background: var(--danger-color); }
.toast.warning .toast-mark { background: var(--warning-color); }
.toast.success .toast-mark { background: var(--success-color); }

.toast-message {
  line-height: 1.5;
}

.toast-close {
  background: transparent;
  border: 0;
  border-radius: 4px;
  color: inherit;
  cursor: pointer;
  flex: 0 0 28px;
  font-size: 16px;
  height: 28px;
  opacity: 0.7;
}

.toast-close:hover,
.toast-close:focus-visible {
  background: color-mix(in srgb, currentColor 12%, transparent);
  opacity: 1;
}

.toast-enter-active {
  transition: opacity 180ms linear, transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
}

.toast-leave-active {
  transition: opacity 140ms linear;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}

.toast-leave-to {
  opacity: 0;
}

.dialog-layer {
  align-items: center;
  background: color-mix(in srgb, var(--canvas) 60%, transparent);
  display: flex;
  inset: 0;
  justify-content: center;
  padding: 16px;
  position: fixed;
  z-index: var(--z-modal);
}

.dialog {
  background: var(--surface-1);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 16px 48px color-mix(in srgb, #000 40%, transparent);
  color: var(--text-primary);
  max-width: 420px;
  outline: none;
  padding: 20px;
  width: 100%;
}

.dialog h2 {
  font-size: var(--font-size-md);
  margin: 0 0 8px;
}

.dialog p {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  margin: 0;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 20px;
}

.dialog-button {
  background: var(--accent-color);
  border: 1px solid transparent;
  border-radius: var(--border-radius-md);
  color: var(--text-on-accent);
  cursor: pointer;
  font: inherit;
  font-size: var(--font-size-sm);
  font-weight: 600;
  min-height: 38px;
  padding: 0 16px;
}

.dialog-button:hover {
  background: var(--accent-hover);
}

.dialog-button.secondary {
  background: transparent;
  border-color: var(--border-subtle);
  color: var(--text-secondary);
}

.dialog-button.secondary:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}

.dialog-button.danger {
  background: var(--danger-color);
  color: #fff;
}

.dialog-button:focus-visible {
  outline: 2px solid var(--text-primary);
  outline-offset: 2px;
}

.dialog-enter-active {
  transition: opacity 180ms linear;
}

.dialog-enter-active .dialog {
  transition: transform 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.dialog-leave-active {
  transition: opacity 140ms linear;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog {
  transform: translateY(8px) scale(0.98);
}
</style>
