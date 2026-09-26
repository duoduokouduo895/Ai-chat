import { shallowRef } from 'vue';

// 所有 MessageContent 共享同一个加载状态；加载完成后已挂载的消息会自动重新渲染
export const markdownRenderer = shallowRef(null);

let loading = null;

export const loadMarkdownRenderer = () => {
  if (!loading) {
    loading = import('./markdownRenderer.js')
      .then((module) => {
        markdownRenderer.value = module;
        return module;
      })
      .catch((error) => {
        loading = null;
        throw error;
      });
  }
  return loading;
};

/** 空闲时预加载，让第一条回复到达前渲染器通常已经就绪，又不占用首屏关键路径 */
export const prewarmMarkdownRenderer = () => {
  if (markdownRenderer.value || loading) return;
  const run = () => loadMarkdownRenderer().catch(() => {});
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(run, { timeout: 3000 });
  } else {
    window.setTimeout(run, 1500);
  }
};
