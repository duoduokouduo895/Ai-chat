// 按需加载：marked、highlight.js、DOMPurify 及代码主题样式只在需要渲染 AI 回复时下载
import DOMPurify from 'dompurify';
import 'highlight.js/styles/atom-one-dark.css';
import { renderMarkdownBlocks } from './markdownBlocks.mjs';

function isSafeUrl(value) {
  const normalized = value.trim().toLowerCase();
  return normalized.startsWith('http://') ||
    normalized.startsWith('https://') ||
    normalized.startsWith('mailto:') ||
    normalized.startsWith('/') ||
    normalized.startsWith('#');
}

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

const SANITIZE_OPTIONS = {
  ALLOWED_TAGS: [
    'a', 'b', 'blockquote', 'br', 'button', 'code', 'div', 'em', 'h1', 'h2', 'h3', 'h4', 'h5',
    'h6', 'hr', 'li', 'ol', 'p', 'pre', 'span', 'strong', 'table', 'tbody', 'td', 'th',
    'thead', 'tr', 'ul'
  ],
  ALLOWED_ATTR: ['class', 'data-copy-code', 'href', 'rel', 'target', 'type'],
  FORBID_ATTR: [/^on/i]
};

/** 渲染并消毒 Markdown，按顶层块返回 HTML 字符串数组 */
export function renderSafeMarkdownBlocks(text) {
  return renderMarkdownBlocks(text).map((html) => DOMPurify.sanitize(html, SANITIZE_OPTIONS));
}
