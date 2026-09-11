const DONE_MARKER = '[DONE]';

const createStreamError = (payload) => {
  const message = payload?.message || '流式响应失败';
  const error = new Error(message);
  error.response = { data: payload };
  return error;
};

const parseJsonPayload = (data, fallback = {}) => {
  try {
    return JSON.parse(data);
  } catch {
    return fallback;
  }
};

const dispatchEvent = async (eventName, data, handlers) => {
  if (eventName === 'complete') {
    const payload = data === DONE_MARKER
      ? { type: 'complete' }
      : parseJsonPayload(data, { type: 'complete' });
    handlers.onComplete?.(payload);
    return true;
  }

  if (data === DONE_MARKER) {
    handlers.onComplete?.({ type: 'complete' });
    return true;
  }

  if (eventName === 'message' && data.startsWith('{')) {
    const compatibilityPayload = parseJsonPayload(data, null);
    if (compatibilityPayload?.type === 'complete' && typeof compatibilityPayload.turnId === 'string') {
      handlers.onComplete?.(compatibilityPayload);
      return true;
    }
  }

  if (eventName === 'metadata') {
    handlers.onMetadata?.(parseJsonPayload(data, null));
    return false;
  }

  if (eventName === 'status') {
    const payload = parseJsonPayload(data, null);
    handlers.onStatus?.(payload?.message || data, payload);
    return false;
  }

  if (eventName === 'error') {
    const payload = parseJsonPayload(data, { message: data });
    const error = createStreamError(payload);
    handlers.onError?.(error);
    return true;
  }

  if (eventName === 'retrieval') {
    handlers.onRetrieval?.(parseJsonPayload(data, null));
  } else if (eventName === 'message') {
    handlers.onMessage?.(data);
  }
  return false;
};

const processLine = async (line, state, handlers) => {
  let normalizedLine = line;
  if (normalizedLine.endsWith('\r')) {
    normalizedLine = normalizedLine.slice(0, -1);
  }

  if (!normalizedLine) {
    if (state.data !== null) {
      const shouldStop = await dispatchEvent(state.eventName, state.data, handlers);
      state.eventName = 'message';
      state.data = null;
      return shouldStop;
    }

    state.eventName = 'message';
    return false;
  }

  if (normalizedLine.startsWith('event:')) {
    state.eventName = normalizedLine.slice(6).trim();
    return false;
  }

  if (normalizedLine.startsWith('data:')) {
    let currentData = normalizedLine.slice(5);
    if (currentData.startsWith(' ')) {
      currentData = currentData.slice(1);
    }

    state.data = state.data === null ? currentData : `${state.data}\n${currentData}`;
  }

  return false;
};

export const readSseEventStream = async (response, handlers = {}) => {
  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('流式响应不可读取');
  }

  try {
    let protocolVersion = 1;
    let completed = false;
    let failed = false;
    const dispatchHandlers = {
      ...handlers,
      onMetadata(payload) {
        protocolVersion = payload?.protocolVersion ?? 1;
        handlers.onMetadata?.(payload);
      },
      onComplete(payload) {
        completed = true;
        handlers.onComplete?.(payload);
      },
      onError(error) {
        failed = true;
        handlers.onError?.(error);
      }
    };
    const decoder = new TextDecoder('utf-8');
    const state = {
      eventName: 'message',
      data: null
    };
    let buffer = '';
    let shouldStop = false;

    while (!shouldStop) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        shouldStop = await processLine(line, state, dispatchHandlers);
        if (shouldStop) break;
      }
    }

    if (!shouldStop && buffer) {
      shouldStop = await processLine(buffer, state, dispatchHandlers);
    }

    if (!shouldStop && state.data !== null) {
      await dispatchEvent(state.eventName, state.data, dispatchHandlers);
    }
    if (protocolVersion >= 2 && !completed && !failed) {
      throw createStreamError({ code: 'CHAT_INCOMPLETE_STREAM', message: '连接已中断，回答尚未确认保存，请重试。' });
    }
  } finally {
    try { await reader.cancel?.(); } catch { /* Preserve the original transport error. */ }
    reader.releaseLock?.();
  }
};

export default {
  readSseEventStream
};
