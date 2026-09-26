import assert from 'node:assert/strict';
import { renderMarkdownBlocks } from '../src/utils/markdownBlocks.mjs';

assert.deepEqual(renderMarkdownBlocks(''), []);

const blocks = renderMarkdownBlocks('# 标题\n\n段落 [链接][1]\n\n```js\nconst a = 1;\n```\n\n- a\n- b\n\n[1]: https://example.com');
assert.equal(blocks.length, 4);
assert.match(blocks[0], /^<h1>/);
assert.match(blocks[1], /href="https:\/\/example.com"/, 'reference links resolve across blocks');
assert.match(blocks[2], /<span class="code-language">js<\/span>/);
assert.match(blocks[2], /<pre><code class="hljs language-js">/);
assert.match(blocks[2], /<span class="hljs-keyword">const<\/span>/, 'code is syntax highlighted');
assert.match(blocks[3], /^<ul>/);

// 代码中的特殊字符只转义一次
const [code] = renderMarkdownBlocks('```js\nif (a < b && s === "x") {}\n```');
assert.match(code, /a &lt; b &amp;&amp; s/);
assert.doesNotMatch(code, /&amp;lt;|&amp;amp;|&amp;quot;/);

const [inline] = renderMarkdownBlocks('用 `a<b && "c"` 比较');
assert.match(inline, /<code>a&lt;b &amp;&amp; &quot;c&quot;<\/code>/);

// 原始 HTML 仍被当作文本
const [raw] = renderMarkdownBlocks('<img src=x onerror=alert(1)>');
assert.doesNotMatch(raw, /<img/);

// 无语言与未知语言的代码块
const [plain] = renderMarkdownBlocks('```\nplain\n```');
assert.match(plain, /<span class="code-language">text<\/span>/);
assert.match(plain, /<pre><code class="hljs">/);
const [unknown] = renderMarkdownBlocks('```nosuchlang\nx < y\n```');
assert.match(unknown, /x &lt; y/);

// 流式追加只改变最后一个块，前面的块字符串保持不变
const before = renderMarkdownBlocks('第一段\n\n第二段正在');
const after = renderMarkdownBlocks('第一段\n\n第二段正在输出');
assert.equal(before.length, 2);
assert.equal(after[0], before[0]);
assert.notEqual(after[1], before[1]);

// 未闭合的代码块仍然是一个块
const open = renderMarkdownBlocks('说明\n\n```py\nprint(1)\n');
assert.equal(open.length, 2);
assert.match(open[1], /<pre><code class="hljs language-py">/);

console.log('markdownBlocks tests passed');
