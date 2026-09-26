<template>
  <div v-if="plain" class="message-content plain-message-content">{{ text }}</div>
  <div v-else ref="contentRef" class="message-content" :class="{ 'is-streaming': !completed }">
    <div v-for="(html, index) in renderedBlocks" :key="index" class="md-block" v-html="html"></div>
    <span v-if="!completed && renderedBlocks.length === 0" class="stream-caret" aria-hidden="true"></span>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownBlocks } from '@/utils/markdownBlocks.mjs';
import 'highlight.js/styles/atom-one-dark.css';

const props = defineProps({
  text: {
    type: String,
    required: true
  },
  plain: {
    type: Boolean,
    default: false
  },
  completed: {
    type: Boolean,
    default: true
  }
});

const contentRef = ref(null);
let purifierHooksRegistered = false;

function isSafeUrl(value) {
  const normalized = value.trim().toLowerCase();
  return normalized.startsWith('http://') ||
    normalized.startsWith('https://') ||
    normalized.startsWith('mailto:') ||
    normalized.startsWith('/') ||
    normalized.startsWith('#');
}

if (!purifierHooksRegistered) {
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.nodeName === 'A') {
      const href = node.getAttribute('href');
      if (href && !isSafeUrl(href)) {
        node.removeAttribute('href');
      }
      if (node.hasAttribute('href')) {
        node.setAttribute('target', '_blank');
        node.setAttribute('rel', 'noopener noreferrer');
      }
    }
  });
  purifierHooksRegistered = true;
}

const SANITIZE_OPTIONS = {
  ALLOWED_TAGS: [
    'a', 'b', 'blockquote', 'br', 'button', 'code', 'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5',
    'h6', 'hr', 'li', 'ol', 'p', 'pre', 'span', 'strong', 'table', 'tbody', 'td', 'th',
    'thead', 'tr', 'ul'
  ],
  ALLOWED_ATTR: ['class', 'data-copy-code', 'href', 'rel', 'target', 'type'],
  FORBID_ATTR: [/^on/i]
};

function renderBlock(rawHtml) {
  return DOMPurify.sanitize(rawHtml, SANITIZE_OPTIONS);
}

// 在最后一个可见文本节点后插入光标，让光标跟随正文（包括未闭合的代码块）
function appendCaret(html) {
  const template = document.createElement('template');
  template.innerHTML = html;
  const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_TEXT);
  let lastText = null;
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (node.nodeValue.trim() && !node.parentElement?.closest('.code-header')) {
      lastText = node;
    }
  }
  const caret = document.createElement('span');
  caret.className = 'stream-caret';
  caret.setAttribute('aria-hidden', 'true');
  if (lastText) {
    lastText.nodeValue = lastText.nodeValue.replace(/\s+$/, '');
    lastText.after(caret);
  } else {
    template.content.append(caret);
  }
  return template.innerHTML;
}

// 已完成的块字符串不变，Vue 不会重写它们的 innerHTML，选区和复制按钮状态得以保留
const renderedBlocks = computed(() => {
  if (!props.text) {
    return [];
  }

  const blocks = renderMarkdownBlocks(props.text).map(renderBlock);
  if (!props.completed && blocks.length > 0) {
    blocks[blocks.length - 1] = appendCaret(blocks[blocks.length - 1]);
  }
  return blocks;
});

async function handleCopyClick(event) {
  const button = event.target.closest('[data-copy-code="true"]');
  if (!button || !contentRef.value?.contains(button)) {
    return;
  }

  const code = button.closest('.code-container')?.querySelector('pre code');
  if (!code) {
    return;
  }

  await navigator.clipboard.writeText(code.innerText);
  button.innerText = '已复制';
  window.setTimeout(() => {
    button.innerText = '复制';
  }, 2000);
}

onMounted(() => {
  contentRef.value?.addEventListener('click', handleCopyClick);
});

onBeforeUnmount(() => {
  contentRef.value?.removeEventListener('click', handleCopyClick);
});
</script>

<style scoped>
.message-content {
  word-wrap: break-word;
  line-height: 1.7;
  font-size: var(--font-size-base);
  color: var(--text-primary);
  overflow-wrap: break-word;
  hyphens: auto;
  user-select: text;
}

.md-block {
  display: contents;
}

.message-content :deep(.stream-caret) {
  animation: caretBlink 1s steps(1, end) infinite;
  background: var(--text-primary);
  display: inline-block;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  width: 0.5em;
}

@keyframes caretBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .message-content :deep(.stream-caret) {
    animation: none;
  }
}

.plain-message-content {
  white-space: pre-wrap;
}

.message-content :deep(p) {
  margin-bottom: 1rem;
}

.md-block:last-child > :deep(:last-child) {
  margin-bottom: 0;
}

.message-content :deep(h1, h2, h3, h4, h5) {
  margin-top: 1.5em;
  margin-bottom: 0.8em;
  font-weight: 600;
  line-height: 1.3;
}

.message-content :deep(h3) {
  font-size: 1.25em;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 2em;
  margin-bottom: 1rem;
}

.message-content :deep(li) {
  margin-bottom: 0.4em;
}

.message-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0.25em 1em;
  border: 1px solid var(--border-light);
  background-color: var(--bg-secondary);
  color: var(--text-secondary);
}

.message-content :deep(strong) {
  font-weight: 600;
}

.message-content :deep(em) {
  font-style: italic;
}

.message-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
  display: block;
  overflow-x: auto;
}

.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid var(--border-light);
  padding: 0.6em 1em;
}

.message-content :deep(th) {
  background-color: var(--bg-secondary);
  font-weight: 600;
}

.message-content :deep(code) {
  background: var(--bg-tertiary);
  color: var(--text-accent);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-size: 0.875em;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.message-content :deep(pre) {
  margin: 1em 0;
  padding: 0;
  background: var(--surface-1);
  border-radius: 8px;
  color: var(--text-secondary);
}

.message-content :deep(.code-container pre) {
  margin: 0;
  border-radius: 0;
}

.message-content :deep(.code-container) {
  margin: 1em 0;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
}

.message-content :deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--surface-2);
  color: var(--text-secondary);
  font-size: 0.8em;
}

.message-content :deep(.code-language) {
  font-weight: 500;
  text-transform: uppercase;
}

.message-content :deep(.copy-btn) {
  background: var(--surface-3);
  color: var(--text-primary);
  border: none;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
}

.message-content :deep(.copy-btn:hover) {
  background: var(--surface-hover);
}

.message-content :deep(pre code),
.message-content :deep(pre code.hljs) {
  padding: 1em !important;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.5;
  display: block;
  overflow-x: auto;
  max-height: 500px;
  background: transparent !important;
  color: inherit !important;
}

.message-content::selection,
.message-content :deep(::selection) {
  background-color: var(--text-primary);
  color: var(--canvas);
}
</style>
