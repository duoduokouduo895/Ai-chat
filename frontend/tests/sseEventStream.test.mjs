import assert from 'node:assert/strict';
import { readSseEventStream } from '../src/services/sseEventStream.mjs';

const encoder = new TextEncoder();

const responseFromChunks = (chunks) => ({
  body: new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    }
  })
});

{
  const events = [];
  await readSseEventStream(responseFromChunks([
    'event: metadata\r\n',
    'data: {"conversationId":42}\r\n\r\n',
    'event: status\n',
    'data: {"message":"正在生成回答"}\n\n',
    'data: 你好\n\n'
  ]), {
    onMetadata: (payload) => events.push(['metadata', payload.conversationId]),
    onStatus: (message) => events.push(['status', message]),
    onMessage: (data) => events.push(['message', data])
  });

  assert.deepEqual(events, [
    ['metadata', 42],
    ['status', '正在生成回答'],
    ['message', '你好']
  ]);
}

{
  const messages = [];
  await readSseEventStream(responseFromChunks([
    'data: # 标题\n',
    'data:   缩进内容\n\n'
  ]), {
    onMessage: (data) => messages.push(data)
  });

  assert.deepEqual(messages, ['# 标题\n  缩进内容']);
}

{
  const errors = [];
  const messages = [];
  await readSseEventStream(responseFromChunks([
    'event: error\n',
    'data: {"message":"失败了","code":"CHAT_ERROR"}\n\n',
    'data: 不应继续分发\n\n'
  ]), {
    onError: (error) => errors.push(error),
    onMessage: (data) => messages.push(data)
  });

  assert.equal(errors.length, 1);
  assert.equal(errors[0].message, '失败了');
  assert.deepEqual(errors[0].response.data, { message: '失败了', code: 'CHAT_ERROR' });
  assert.deepEqual(messages, []);
}

{
  const messages = [];
  await readSseEventStream(responseFromChunks([
    'data: 第一段\n\n',
    'data: [DONE]\n\n',
    'data: 第二段\n\n'
  ]), {
    onMessage: (data) => messages.push(data)
  });

  assert.deepEqual(messages, ['第一段']);
}

{
  const abortError = new DOMException('The operation was aborted.', 'AbortError');
  const response = {
    body: {
      getReader() {
        return {
          read() {
            throw abortError;
          }
        };
      }
    }
  };

  await assert.rejects(
    () => readSseEventStream(response, {}),
    (error) => error.name === 'AbortError'
  );
}
