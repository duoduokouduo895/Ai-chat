<template>
  <div v-if="plain" class="message-content plain-message-content">{{ text }}</div>
  <div v-else ref="contentRef" class="message-content" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import hljs from '@/utils/highlight.js';
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

marked.setOptions({
  highlight(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
    return hljs.highlight(code, { language }).value;
  },
  langPrefix: 'hljs language-',
  gfm: true,
  breaks: true
});

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

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

const renderedHtml = computed(() => {
  if (!props.text) {
    return '';
  }

  const rawHtml = marked.parse(escapeHtml(props.text));
  const withCopyButton = rawHtml.replace(
    /<pre><code class="(.*?)">/g,
    `<div class="code-container">
       <div class="code-header">
         <span class="code-language">$1</span>
         <button class="copy-btn" type="button" data-copy-code="true">复制</button>
       </div>
       <pre><code class="$1">`
  );

  return DOMPurify.sanitize(
    withCopyButton.replace(/<\/code><\/pre>/g, '</code></pre></div>'),
    {
      ALLOWED_TAGS: [
        'a', 'b', 'blockquote', 'br', 'button', 'code', 'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5',
        'h6', 'hr', 'li', 'ol', 'p', 'pre', 'span', 'strong', 'table', 'tbody', 'td', 'th',
        'thead', 'tr', 'ul'
      ],
      ALLOWED_ATTR: ['class', 'data-copy-code', 'href', 'rel', 'target', 'type'],
      FORBID_ATTR: [/^on/i]
    }
  );
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

.plain-message-content {
  white-space: pre-wrap;
}

.message-content :deep(p) {
  margin-bottom: 1rem;
}

.message-content :deep(p:last-child) {
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
  background-color: var(--accent-color);
  color: var(--text-white);
}
</style>
