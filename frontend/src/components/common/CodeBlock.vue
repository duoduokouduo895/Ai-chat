<template>
  <div class="code-block-container">
    <div class="code-header">
      <span class="code-language">{{ displayLanguage }}</span>
      <button 
        class="copy-btn" 
        @click="handleCopy" 
        :title="copyButtonTitle"
        :class="{ 'copied': isCopied }"
      >
        <svg v-if="!isCopied" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20,6 9,17 4,12"></polyline>
        </svg>
      </button>
    </div>
    <pre class="code-block" ref="codeElement">
      <code :class="`hljs language-${language}`" v-html="highlightedCode"></code>
    </pre>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import hljs from 'highlight.js';

const props = defineProps({
  code: {
    type: String,
    required: true
  },
  language: {
    type: String,
    default: 'plaintext'
  }
});

const codeElement = ref(null);
const isCopied = ref(false);

// 计算属性：显示的语言名称
const displayLanguage = computed(() => {
  const langMap = {
    'javascript': 'JavaScript',
    'typescript': 'TypeScript',
    'java': 'Java',
    'python': 'Python',
    'cpp': 'C++',
    'csharp': 'C#',
    'html': 'HTML',
    'css': 'CSS',
    'sql': 'SQL',
    'json': 'JSON',
    'xml': 'XML',
    'yaml': 'YAML',
    'markdown': 'Markdown',
    'bash': 'Bash',
    'shell': 'Shell',
    'plaintext': 'Text'
  };
  return langMap[props.language] || props.language.toUpperCase();
});

// 计算属性：高亮后的代码
const highlightedCode = computed(() => {
  try {
    const cleanCode = props.code.trim();
    
    if (props.language && props.language !== 'plaintext' && hljs.getLanguage(props.language)) {
      return hljs.highlight(cleanCode, { language: props.language }).value;
    } else {
      return hljs.highlightAuto(cleanCode).value;
    }
  } catch (e) {
    // 如果高亮失败，返回转义后的原始代码
    return escapeHtml(props.code.trim());
  }
});

// 计算属性：复制按钮提示文字
const copyButtonTitle = computed(() => {
  return isCopied.value ? '已复制!' : '复制代码';
});

// 转义HTML特殊字符
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// 处理复制操作
async function handleCopy() {
  try {
    const text = props.code.trim();
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      // 备用复制方法
      fallbackCopyTextToClipboard(text);
    }
    
    // 显示复制成功状态
    isCopied.value = true;
    setTimeout(() => {
      isCopied.value = false;
    }, 2000);
    
  } catch (err) {
    console.error('复制失败:', err);
    fallbackCopyTextToClipboard(props.code.trim());
  }
}

// 备用复制方法（兼容旧浏览器）
function fallbackCopyTextToClipboard(text) {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.style.position = 'fixed';
  textArea.style.top = '0';
  textArea.style.left = '0';
  textArea.style.width = '2em';
  textArea.style.height = '2em';
  textArea.style.padding = '0';
  textArea.style.border = 'none';
  textArea.style.outline = 'none';
  textArea.style.boxShadow = 'none';
  textArea.style.background = 'transparent';
  
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();
  
  try {
    document.execCommand('copy');
    isCopied.value = true;
    setTimeout(() => {
      isCopied.value = false;
    }, 2000);
  } catch (err) {
    console.error('复制失败:', err);
  }
  
  document.body.removeChild(textArea);
}
</script>

<style scoped>
.code-block-container {
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
  background-color: #1e1e1e;
  border: 1px solid #333;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #2d2d2d;
  border-bottom: 1px solid #333;
  font-size: 12px;
}

.code-language {
  color: #888;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.copy-btn {
  background: none;
  border: none;
  color: #888;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 14px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  /* 触摸优化 */
  -webkit-user-select: none;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.copy-btn:hover,
.copy-btn:active {
  background-color: #404040;
  color: #fff;
  transform: scale(0.95);
}

.copy-btn.copied {
  color: #52c41a;
}

.copy-btn.copied:hover {
  background-color: rgba(82, 196, 26, 0.1);
}

.code-block {
  margin: 0;
  padding: 20px;
  background-color: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', 'Courier New', 'SF Mono', 'Roboto Mono', monospace;
  font-size: 14px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre;
  max-height: 500px;
  overflow-y: auto;
  tab-size: 2;
}

.code-block::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.code-block::-webkit-scrollbar-track {
  background: #2d2d2d;
  border-radius: 4px;
}

.code-block::-webkit-scrollbar-thumb {
  background: #555;
  border-radius: 4px;
}

.code-block::-webkit-scrollbar-thumb:hover {
  background: #777;
}

.code-block::-webkit-scrollbar-corner {
  background: #2d2d2d;
}

.code-block code {
  background: none;
  padding: 0;
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  display: block;
  white-space: pre;
}

/* 代码高亮样式已移至全局CSS文件中 */

/* 响应式设计 */
@media (max-width: 768px) {
  .code-block-container {
    margin: 12px 0;
    border-radius: 6px;
  }
  
  .code-header {
    padding: 10px 12px;
    font-size: 11px;
  }
  
  .code-language {
    font-size: 11px;
  }
  
  .copy-btn {
    padding: 6px 8px;
    font-size: 12px;
    min-width: 40px;
    min-height: 40px;
  }
  
  .copy-btn svg {
    width: 14px;
    height: 14px;
  }
  
  .code-block {
    font-size: 12px;
    padding: 16px;
    line-height: 1.5;
    max-height: 400px;
  }
  
  .code-block::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }
}

@media (max-width: 480px) {
  .code-block-container {
    margin: 10px 0;
    border-radius: 4px;
  }
  
  .code-header {
    padding: 8px 10px;
    font-size: 10px;
  }
  
  .code-language {
    font-size: 10px;
  }
  
  .copy-btn {
    padding: 4px 6px;
    font-size: 11px;
    min-width: 36px;
    min-height: 36px;
  }
  
  .copy-btn svg {
    width: 12px;
    height: 12px;
  }
  
  .code-block {
    font-size: 11px;
    padding: 12px;
    line-height: 1.4;
    max-height: 300px;
  }
  
  .code-block::-webkit-scrollbar {
    width: 4px;
    height: 4px;
  }
  
  .code-block::-webkit-scrollbar-thumb {
    border-radius: 2px;
  }
}
</style>