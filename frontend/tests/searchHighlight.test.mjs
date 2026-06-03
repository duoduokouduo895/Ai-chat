import assert from 'node:assert/strict'
import { splitHighlightText } from '../src/utils/searchHighlight.mjs'

const join = (parts) => parts.map((part) => part.text).join('')

{
  const parts = splitHighlightText('<script>alert(1)</script>', '<script>')
  assert.equal(join(parts), '<script>alert(1)</script>')
  assert.equal(parts.filter((part) => part.highlight).length, 1)
}

{
  const keyword = 'a+b?.*'
  const parts = splitHighlightText('xx a+b?.* yy', keyword)
  assert.deepEqual(parts, [
    { text: 'xx ', highlight: false },
    { text: keyword, highlight: true },
    { text: ' yy', highlight: false },
  ])
}

{
  const parts = splitHighlightText('<b>hello</b>', '')
  assert.deepEqual(parts, [{ text: '<b>hello</b>', highlight: false }])
}

{
  const parts = splitHighlightText('Alpha alpha', 'alpha')
  assert.equal(parts.filter((part) => part.highlight).length, 2)
  assert.equal(join(parts), 'Alpha alpha')
}
