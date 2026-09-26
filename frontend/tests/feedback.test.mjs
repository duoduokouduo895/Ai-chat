import assert from 'node:assert/strict';
import {
  alertDialog,
  closeDialog,
  confirmDialog,
  dismissToast,
  feedbackState,
  notify
} from '../src/services/feedback.mjs';

const timers = [];
globalThis.setTimeout = (fn, ms) => {
  timers.push({ fn, ms });
  return timers.length;
};

// 提示：入栈、去重、按类型设置时长、自动与手动关闭
const first = notify.info('已保存');
assert.equal(feedbackState.toasts.length, 1);
assert.equal(notify.info('已保存'), null, 'identical visible toast is not stacked');
assert.equal(notify.warning('  '), null, 'blank messages are ignored');
notify.error('失败');
assert.deepEqual(feedbackState.toasts.map((toast) => toast.type), ['info', 'error']);
assert.ok(timers.at(-1).ms > timers[0].ms, 'errors stay longer than info');
dismissToast(first);
assert.deepEqual(feedbackState.toasts.map((toast) => toast.message), ['失败']);
timers.at(-1).fn();
assert.equal(feedbackState.toasts.length, 0);

for (let i = 0; i < 6; i += 1) notify.info(`消息 ${i}`);
assert.equal(feedbackState.toasts.length, 4, 'stack is capped');
assert.equal(feedbackState.toasts[0].message, '消息 2', 'oldest toasts are dropped first');
feedbackState.toasts.splice(0);

// 确认框：确认 / 取消
const accepted = confirmDialog({ title: '删除对话', message: '确定？', confirmText: '删除', tone: 'danger' });
assert.equal(feedbackState.dialog.cancelText, '取消');
assert.equal(feedbackState.dialog.tone, 'danger');
closeDialog(true);
assert.equal(await accepted, true);
assert.equal(feedbackState.dialog, null);

const rejected = confirmDialog({ message: '确定？' });
closeDialog(false);
assert.equal(await rejected, false);

// 新对话框会把旧的视为取消
const superseded = confirmDialog({ message: '旧' });
const latest = confirmDialog({ message: '新' });
assert.equal(await superseded, false);
assert.equal(feedbackState.dialog.message, '新');
closeDialog(true);
assert.equal(await latest, true);

// 提示框：无取消按钮，可设置为不可关闭
const alerted = alertDialog({ title: '游客体验已结束', message: '请登录', confirmText: '前往登录', dismissible: false });
assert.equal(feedbackState.dialog.cancelText, null);
assert.equal(feedbackState.dialog.dismissible, false);
closeDialog(true);
assert.equal(await alerted, undefined);

console.log('feedback_test=PASS');
