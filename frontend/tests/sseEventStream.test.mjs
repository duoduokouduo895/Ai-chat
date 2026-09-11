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
  const messages = [];
  const completions = [];
  await readSseEventStream(responseFromChunks([
    'event: metadata\ndata: {"protocolVersion":2}\n\n',
    'event: future-event\ndata: hidden\n\n',
    'event: retrieval\ndata: {"status":"EMPTY"}\n\n',
    'data: 资料不足\n\n',
    'event: complete\ndata: {"turnId":"saved-turn"}\n\n'
  ]), {
    onMessage: (data) => messages.push(data),
    onComplete: (data) => completions.push(data.turnId)
  });
  assert.deepEqual(messages, ['资料不足']);
  assert.deepEqual(completions, ['saved-turn']);
}

{
  const messages = [];
  const completions = [];
  await readSseEventStream(responseFromChunks([
    'event: metadata\ndata: {"protocolVersion":2}\n\n',
    'event: message\ndata: 下午好呀～有什么我可以帮忙的吗？\n\n',
    'id: saved-turn\n',
    'event: complete\ndata: [DONE]\n\n'
  ]), {
    onMessage: (data) => messages.push(data),
    onComplete: (data) => completions.push(data?.type)
  });
  assert.deepEqual(messages, ['下午好呀～有什么我可以帮忙的吗？']);
  assert.deepEqual(completions, ['complete']);
}

{
  const messages = [];
  const completions = [];
  await readSseEventStream(responseFromChunks([
    'event: metadata\ndata: {"protocolVersion":2}\n\n',
    'event: message\ndata: 下午好呀～有什么我可以帮忙的吗？\n\n',
    'data: {"turnId":"7328df62-6815-478c-8156-0552f1d43c8d","type":"complete"}\n\n'
  ]), {
    onMessage: (data) => messages.push(data),
    onComplete: (data) => completions.push(data.turnId)
  });
  assert.deepEqual(messages, ['下午好呀～有什么我可以帮忙的吗？']);
  assert.deepEqual(completions, ['7328df62-6815-478c-8156-0552f1d43c8d']);
}

{
  await assert.rejects(() => readSseEventStream(responseFromChunks([
    'event: metadata\ndata: {"protocolVersion":2}\n\n',
    'data: 未确认的回答\n\n'
  ])), (error) => error.response.data.code === 'CHAT_INCOMPLETE_STREAM');
}

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
