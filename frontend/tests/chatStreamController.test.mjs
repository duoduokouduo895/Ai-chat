import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { Buffer } from 'node:buffer'
import { useChatMessages } from '../src/composables/useChatMessages.js'

const source = readFileSync(new URL('../src/composables/useChatStreamController.js', import.meta.url), 'utf8')
  .replace(
    "import { aiApiService } from '@/services/aiApi.js';",
    "const aiApiService = globalThis.__chatStreamControllerTestAiApiService;"
  )

const delay = () => new Promise((resolve) => setTimeout(resolve, 0))
globalThis.__chatStreamControllerTestAiApiService = {
  chatStream: () => new Promise(() => {})
}
globalThis.requestAnimationFrame = (callback) => setTimeout(callback, 0)
globalThis.cancelAnimationFrame = (id) => clearTimeout(id)

const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const { useChatStreamController } = await import(moduleUrl)

{
  globalThis.__chatStreamControllerTestAiApiService.chatStream = () => new Promise(() => {})
  const abortController = { value: null }
  const conversationId = { value: null }
  const isLoading = { value: false }
  const chatMessages = useChatMessages()

  const controller = useChatStreamController({
    abortController,
    chatMessages,
    conversationId,
    isLoading,
    loadRecentConversations: () => {},
    streamIdleTimeoutMs: 0
  })

  controller.handleSendMessage({
    message: '你好',
    model: 'test-model',
    knowledgeBaseIds: []
  })

  assert.equal(chatMessages.messages.value.length, 2)
  assert.equal(chatMessages.messages.value[0].sender, 'user')
  assert.equal(chatMessages.messages.value[0].text, '你好')
  assert.equal(chatMessages.messages.value[1].sender, 'ai')
  assert.equal(chatMessages.messages.value[1].text, '')
  assert.equal(chatMessages.messages.value[1].completed, false)
  assert.equal(chatMessages.messages.value[1].status, '正在连接模型')
}

{
  globalThis.__chatStreamControllerTestAiApiService.chatStream = async (
    request,
    onData,
    onComplete,
    onError,
    onMetadata,
    onStatus
  ) => {
    onMetadata({ conversationId: 88 })
    onStatus('正在生成回答')
    onData('早上')
    onData('好')
    onComplete(false)
  }
  const abortController = { value: null }
  const conversationId = { value: null }
  const isLoading = { value: false }
  const chatMessages = useChatMessages()
  let historyRefreshCount = 0

  const controller = useChatStreamController({
    abortController,
    chatMessages,
    conversationId,
    isLoading,
    loadRecentConversations: () => { historyRefreshCount += 1 }
  })

  await controller.handleSendMessage({
    message: '你好',
    model: 'test-model',
    knowledgeBaseIds: []
  })
  await delay()

  assert.equal(conversationId.value, 88)
  assert.equal(isLoading.value, false)
  assert.equal(historyRefreshCount, 1)
  assert.equal(chatMessages.messages.value.length, 2)
  assert.equal(chatMessages.messages.value[1].sender, 'ai')
  assert.equal(chatMessages.messages.value[1].text, '早上好')
  assert.equal(chatMessages.messages.value[1].completed, true)
  assert.equal(chatMessages.messages.value[1].status, '')
}

{
  let signal
  globalThis.__chatStreamControllerTestAiApiService.chatStream = (
    request,
    onData,
    onComplete,
    onError,
    onMetadata,
    onStatus,
    abortController
  ) => {
    signal = abortController.signal
    onMetadata({ conversationId: 89 })
    onStatus('正在生成回答')
    return new Promise(() => {})
  }
  const abortController = { value: null }
  const conversationId = { value: null }
  const isLoading = { value: false }
  const chatMessages = useChatMessages()

  const controller = useChatStreamController({
    abortController,
    chatMessages,
    conversationId,
    isLoading,
    loadRecentConversations: () => {},
    streamIdleTimeoutMs: 10
  })

  controller.handleSendMessage({
    message: '你好',
    model: 'test-model',
    knowledgeBaseIds: []
  })
  await delay()
  await new Promise((resolve) => setTimeout(resolve, 20))

  assert.equal(conversationId.value, 89)
  assert.equal(isLoading.value, false)
  assert.equal(abortController.value, null)
  assert.equal(signal.aborted, true)
  assert.equal(chatMessages.messages.value.length, 2)
  assert.equal(chatMessages.messages.value[1].sender, 'ai')
  assert.equal(chatMessages.messages.value[1].isError, true)
  assert.equal(chatMessages.messages.value[1].text, '模型响应超时，请稍后重试或切换模型。')
}

delete globalThis.__chatStreamControllerTestAiApiService
delete globalThis.requestAnimationFrame
delete globalThis.cancelAnimationFrame
