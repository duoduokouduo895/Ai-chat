import { Marked } from 'marked';
import { markedHighlight } from 'marked-highlight';
import hljs from './highlight.js';

const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char]);
}

function unescapeHtml(text) {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&');
}

// 原文在进入 marked 前已整体转义（禁止原始 HTML），代码内容需要先还原，
// 再交给只转义一次的 hljs / 行内代码输出，否则会显示成 &lt; 之类的实体
const markdown = new Marked(
  markedHighlight({
    emptyLangClass: 'hljs',
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      const language = hljs.getLanguage(lang) ? lang : 'plaintext';
      return hljs.highlight(unescapeHtml(code), { language }).value;
    }
  }),
  {
    gfm: true,
    breaks: true,
    // 高亮结果与原文相同（如 plaintext）时 marked-highlight 不会标记 escaped，
    // 但代码原文本来就已转义，统一标记以免二次转义
    walkTokens(token) {
      if (token.type === 'code') token.escaped = true;
    },
    renderer: {
      codespan({ text }) {
        return `<code>${text}</code>`;
      }
    }
  }
);

function wrapCodeBlocks(html) {
  return html
    .replace(
      /<pre><code class="hljs(?: language-([^"]*))?">/g,
      (_, lang) => `<div class="code-container">
       <div class="code-header">
         <span class="code-language">${lang || 'text'}</span>
         <button class="copy-btn" type="button" data-copy-code="true">复制</button>
       </div>
       <pre><code class="hljs${lang ? ` language-${lang}` : ''}">`
    )
    .replace(/<\/code><\/pre>/g, '</code></pre></div>');
}

/**
 * 把 Markdown 拆成顶层块分别渲染，流式输出时前面已完成的块 HTML 字符串不变，
 * 视图只需要替换最后一个仍在增长的块。返回的 HTML 仍需在调用方消毒。
 */
export function renderMarkdownBlocks(text) {
  if (!text) return [];

  const tokens = markdown.lexer(escapeHtml(text));
  // 分开调用 lexer/parser 时 marked 不会自动执行 walkTokens，高亮扩展依赖它
  markdown.walkTokens(tokens, markdown.defaults.walkTokens);
  const blocks = [];
  for (const token of tokens) {
    if (token.type === 'space' || token.type === 'def') continue;
    const single = [token];
    single.links = tokens.links;
    const html = markdown.parser(single);
    if (html.trim()) blocks.push(wrapCodeBlocks(html));
  }
  return blocks;
}
