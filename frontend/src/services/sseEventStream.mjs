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
  if (data === DONE_MARKER) {
    return true;
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

  handlers.onMessage?.(data);
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
      shouldStop = await processLine(line, state, handlers);
      if (shouldStop) break;
    }
  }

  if (!shouldStop && buffer) {
    shouldStop = await processLine(buffer, state, handlers);
  }

  if (!shouldStop && state.data !== null) {
    await dispatchEvent(state.eventName, state.data, handlers);
  }
};

export default {
  readSseEventStream
};
