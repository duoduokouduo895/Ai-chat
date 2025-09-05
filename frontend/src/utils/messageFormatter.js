import hljs from 'highlight.js';

/**
 * 格式化消息内容，处理代码块和行内代码
 * @param {string} text - 原始消息文本
 * @returns {string} - 格式化后的HTML字符串
 */
export function formatMessage(text) {
  if (!text) return '';
  
  // 处理代码块 ```language\ncode\n```
  const codeBlockRegex = /```([a-zA-Z0-9+#-]*)?\s*\n?([\s\S]*?)```/g;
  let formattedText = text.replace(codeBlockRegex, (match, language, code) => {
    const lang = language || 'plaintext';
    let highlightedCode;
    
    try {
      // 清理代码内容，移除开头和结尾的换行，但保持内部格式
      let cleanCode = code.replace(/^\n+|\n+$/g, '');
      
      // 预处理代码：确保注释独立成行，减少过多的空行
      cleanCode = preprocessCode(cleanCode);
      
      if (language && hljs.getLanguage(language)) {
        highlightedCode = hljs.highlight(cleanCode, { language }).value;
      } else {
        highlightedCode = hljs.highlightAuto(cleanCode).value;
      }
    } catch (e) {
      // 如果高亮失败，保持原格式并转义HTML
      highlightedCode = escapeHtml(preprocessCode(code.replace(/^\n+|\n+$/g, '')));
    }
    
    const codeId = 'code-' + Math.random().toString(36).substr(2, 9);
    return `\n<div class="code-block-container">\n  <div class="code-header">\n    <span class="code-language">${lang.toUpperCase()}</span>\n    <button class="copy-btn" onclick="copyCode('${codeId}')" title="复制代码">\n      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">\n        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>\n        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>\n      </svg>\n    </button>\n  </div>\n  <pre class="code-block" id="${codeId}"><code class="hljs language-${lang}">${highlightedCode}</code></pre>\n</div>\n`;
  });
  
  // 处理行内代码 `code`
  const inlineCodeRegex = /`([^`\n]+)`/g;
  formattedText = formattedText.replace(inlineCodeRegex, (match, code) => {
    return `<code class="inline-code">${escapeHtml(code)}</code>`;
  });
  
  // 处理Markdown格式符号，移除或替换过多的星号
  formattedText = processMarkdownFormatting(formattedText);
  
  // 改进的段落和换行处理
  formattedText = processTextFormatting(formattedText);
  
  return formattedText;
}

/**
 * 预处理代码，优化格式和注释显示
 * @param {string} code - 原始代码
 * @returns {string} - 处理后的代码
 */
function preprocessCode(code) {
  // 按行分割代码
  let lines = code.split('\n');
  
  // 处理每一行
  lines = lines.map(line => {
    const trimmedLine = line.trim();
    
    // 跳过已经是注释行的情况
    if (trimmedLine.startsWith('//') || trimmedLine.startsWith('/*') || trimmedLine.startsWith('*')) {
      return line;
    }
    
    // 处理行内注释 - 将注释分离到新行
    if (line.includes('//')) {
      const commentIndex = line.indexOf('//');
      const codePart = line.substring(0, commentIndex).trim();
      const commentPart = line.substring(commentIndex).trim();
      
      if (codePart && commentPart) {
        // 保持原有缩进
        const indent = line.match(/^\s*/)[0];
        return [codePart, indent + commentPart];
      }
    }
    
    // 处理块注释
    if (line.includes('/*') && line.includes('*/') && !trimmedLine.startsWith('/*')) {
      const startIndex = line.indexOf('/*');
      const endIndex = line.indexOf('*/') + 2;
      const codeBefore = line.substring(0, startIndex).trim();
      const comment = line.substring(startIndex, endIndex).trim();
      const codeAfter = line.substring(endIndex).trim();
      
      const result = [];
      if (codeBefore) result.push(codeBefore);
      if (comment) result.push(comment);
      if (codeAfter) result.push(codeAfter);
      
      return result.length > 1 ? result : line;
    }
    
    return line;
  }).flat();
  
  // 减少连续空行，但保留必要的空行用于代码块分隔
  const processedLines = [];
  let consecutiveEmptyLines = 0;
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmedLine = line.trim();
    
    if (trimmedLine === '') {
      consecutiveEmptyLines++;
      // 只保留一个空行，除非是在方法或类之间
      if (consecutiveEmptyLines === 1) {
        processedLines.push(line);
      }
    } else {
      consecutiveEmptyLines = 0;
      processedLines.push(line);
    }
  }
  
  // 移除开头和结尾的空行
  while (processedLines.length > 0 && processedLines[0].trim() === '') {
    processedLines.shift();
  }
  while (processedLines.length > 0 && processedLines[processedLines.length - 1].trim() === '') {
    processedLines.pop();
  }
  
  return processedLines.join('\n');
}

/**
 * 处理Markdown格式符号，改善用户体验
 * @param {string} text - 包含Markdown格式的文本
 * @returns {string} - 处理后的文本
 */
function processMarkdownFormatting(text) {
  // 处理粗体 **text** 或 ***text***
  text = text.replace(/\*\*\*([^*]+?)\*\*\*/g, '<strong class="bold-text">$1</strong>');
  text = text.replace(/\*\*([^*]+?)\*\*/g, '<strong class="bold-text">$1</strong>');
  
  // 处理单个星号强调 *text*（但避免处理列表项）
  text = text.replace(/(?<!\*)\*([^*\n]+?)\*(?!\*)/g, '<em class="italic-text">$1</em>');
  
  // 移除多余的星号（通常是装饰性的）
  // 移除3个或更多连续的星号
  text = text.replace(/\*{3,}/g, '');
  
  // 移除单独的星号行
  text = text.replace(/^\s*\*+\s*$/gm, '');
  
  return text;
}

/**
 * 改进的文本格式处理，增加适当的换行和段落间距
 * @param {string} text - 文本内容
 * @returns {string} - 格式化后的文本
 */
function processTextFormatting(text) {
  // 保护代码块不被处理
  const codeBlocks = [];
  let codeBlockIndex = 0;
  text = text.replace(/<div class="code-block-container">.*?<\/div>/gs, (match) => {
    codeBlocks.push(match);
    return `__CODE_BLOCK_${codeBlockIndex++}__`;
  });
  
  // 处理标题格式（以数字开头的行，如 "1. **", "2. **" 等）
  text = text.replace(/^(\d+\.)\s*\*\*([^*]+?)\*\*\s*:?/gm, '<div class="numbered-section"><strong class="section-title">$1 $2:</strong></div>');
  
  // 处理以 ** 开头的段落标题
  text = text.replace(/^\*\*([^*]+?)\*\*\s*:?/gm, '<div class="section-title-block"><strong class="section-title">$1:</strong></div>');
  
  // 按双换行或更多换行分割段落
  const paragraphs = text.split(/\n\s*\n+/);
  
  // 处理每个段落
  const processedParagraphs = paragraphs.map(paragraph => {
    paragraph = paragraph.trim();
    if (!paragraph) return '';
    
    // 如果是代码块占位符，跳过处理
    if (paragraph.includes('__CODE_BLOCK_')) {
      return paragraph;
    }
    
    // 如果已经是HTML标签包装的内容，保持原样
    if (paragraph.startsWith('<div class="')) {
      return paragraph;
    }
    
    // 将单个换行转换为<br>，但在句号、冒号、问号后增加额外间距
    paragraph = paragraph.replace(/\n/g, '<br>');
    
    // 在句号后面如果有<br>，增加一个额外的<br>来增加段落间距
    paragraph = paragraph.replace(/([。！？:：])(<br>)/g, '$1$2<br>');
    
    // 包装在段落标签中
    return `<div class="text-paragraph">${paragraph}</div>`;
  });
  
  // 过滤空段落并合并
  let result = processedParagraphs.filter(p => p.trim() !== '').join('\n\n');
  
  // 恢复代码块
  codeBlocks.forEach((block, index) => {
    result = result.replace(`__CODE_BLOCK_${index}__`, block);
  });
  
  return result;
}

/**
 * 转义HTML特殊字符
 * @param {string} text - 需要转义的文本
 * @returns {string} - 转义后的文本
 */
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * 复制代码到剪贴板
 * @param {string} codeId - 代码块的ID
 */
export function copyCode(codeId) {
  const codeElement = document.getElementById(codeId);
  if (codeElement) {
    const text = codeElement.textContent || codeElement.innerText;
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => {
        showCopySuccess(codeId);
      }).catch(err => {
        console.error('复制失败:', err);
        fallbackCopyTextToClipboard(text);
      });
    } else {
      fallbackCopyTextToClipboard(text);
    }
  }
}

/**
 * 显示复制成功提示
 * @param {string} codeId - 代码块的ID
 */
function showCopySuccess(codeId) {
  const button = document.querySelector(`#${codeId}`).parentElement.querySelector('.copy-btn');
  if (button) {
    const originalContent = button.innerHTML;
    button.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="20,6 9,17 4,12"></polyline>
      </svg>
    `;
    button.style.color = '#52c41a';
    
    setTimeout(() => {
      button.innerHTML = originalContent;
      button.style.color = '';
    }, 2000);
  }
}

/**
 * 备用复制方法（兼容旧浏览器）
 * @param {string} text - 要复制的文本
 */
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
    console.log('代码已复制到剪贴板');
  } catch (err) {
    console.error('复制失败:', err);
  }
  
  document.body.removeChild(textArea);
}

// 将复制函数挂载到全局，供HTML中的onclick使用
if (typeof window !== 'undefined') {
  window.copyCode = copyCode;
}