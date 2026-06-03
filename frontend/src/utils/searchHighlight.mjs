export function escapeSearchKeyword(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function splitHighlightText(text, keyword) {
  const source = text == null ? '' : String(text)
  const query = keyword == null ? '' : String(keyword).trim()
  if (!source || !query) {
    return [{ text: source, highlight: false }]
  }

  const regex = new RegExp(escapeSearchKeyword(query), 'gi')
  const parts = []
  let lastIndex = 0
  let match

  while ((match = regex.exec(source)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ text: source.slice(lastIndex, match.index), highlight: false })
    }
    parts.push({ text: match[0], highlight: true })
    lastIndex = regex.lastIndex
  }

  if (lastIndex < source.length) {
    parts.push({ text: source.slice(lastIndex), highlight: false })
  }

  return parts.length > 0 ? parts : [{ text: source, highlight: false }]
}
