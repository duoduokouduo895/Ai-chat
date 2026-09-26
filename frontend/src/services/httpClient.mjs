/**
 * 基于 fetch 的轻量 HTTP 客户端，替代 axios。
 *
 * 保持项目依赖的 axios 兼容约定，调用方无需改动：
 * - 成功：{ data, status, statusText, headers, config }，headers 为小写键的普通对象
 * - 失败：Error 带 response（{ data, status, headers }）、code、config；
 *   超时 code 为 ECONNABORTED，网络错误为 ERR_NETWORK，主动取消为 ERR_CANCELED
 * - 带 onUploadProgress 时改用 XHR，因为 fetch 无法报告上传进度
 */

const buildQuery = (params) => {
  if (!params) return '';
  const search = new URLSearchParams();
  const append = (key, value) => {
    if (value === undefined || value === null) return;
    search.append(key, value instanceof Date ? value.toISOString() : String(value));
  };
  for (const [key, value] of Object.entries(params)) {
    // 数组按重复键序列化（models=a&models=b），与 Spring 的 List 参数绑定一致
    if (Array.isArray(value)) {
      value.forEach((item) => append(key, item));
    } else if (value !== null && typeof value === 'object' && !(value instanceof Date)) {
      append(key, JSON.stringify(value));
    } else {
      append(key, value);
    }
  }
  const query = search.toString();
  return query ? `?${query}` : '';
};

const joinUrl = (baseURL = '', url = '') => {
  if (/^https?:\/\//i.test(url)) return url;
  if (!baseURL) return url;
  return `${baseURL.replace(/\/+$/, '')}/${url.replace(/^\/+/, '')}`;
};

const isFormData = (value) => typeof FormData !== 'undefined' && value instanceof FormData;
const isBinaryBody = (value) => (
  (typeof Blob !== 'undefined' && value instanceof Blob) ||
  (typeof URLSearchParams !== 'undefined' && value instanceof URLSearchParams) ||
  value instanceof ArrayBuffer
);

const headersToObject = (headers) => {
  const result = {};
  headers?.forEach?.((value, key) => {
    result[key.toLowerCase()] = value;
  });
  return result;
};

const parseXhrHeaders = (raw = '') => {
  const result = {};
  raw.trim().split(/[\r\n]+/).forEach((line) => {
    const index = line.indexOf(':');
    if (index > 0) {
      result[line.slice(0, index).trim().toLowerCase()] = line.slice(index + 1).trim();
    }
  });
  return result;
};

const parseText = (text) => {
  if (!text) return '';
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
};

const createError = (message, code, config, response) => {
  const error = new Error(message);
  error.code = code;
  error.config = config;
  if (response) error.response = response;
  if (code === 'ERR_CANCELED') error.name = 'CanceledError';
  return error;
};

const prepareBody = (config, headers) => {
  const { data } = config;
  if (data === undefined || data === null) return undefined;
  if (isFormData(data)) {
    // 让浏览器生成带 boundary 的 multipart Content-Type
    delete headers['content-type'];
    return data;
  }
  if (typeof data === 'string' || isBinaryBody(data)) return data;
  headers['content-type'] = headers['content-type'] || 'application/json';
  return JSON.stringify(data);
};

const fetchTransport = async ({ url, method, headers, body, signal, config }) => {
  const response = await fetch(url, { method, headers, body, signal });
  const data = config.responseType === 'blob'
    ? await response.blob()
    : parseText(await response.text());
  return {
    data,
    status: response.status,
    statusText: response.statusText,
    headers: headersToObject(response.headers)
  };
};

const xhrTransport = ({ url, method, headers, body, signal, config }) => new Promise((resolve, reject) => {
  const xhr = new XMLHttpRequest();
  xhr.open(method, url, true);
  Object.entries(headers).forEach(([key, value]) => xhr.setRequestHeader(key, value));
  xhr.responseType = config.responseType === 'blob' ? 'blob' : 'text';
  if (config.onUploadProgress && xhr.upload) {
    xhr.upload.onprogress = (event) => config.onUploadProgress({
      loaded: event.loaded,
      total: event.lengthComputable ? event.total : undefined,
      lengthComputable: event.lengthComputable
    });
  }
  const abort = () => xhr.abort();
  signal.addEventListener('abort', abort, { once: true });
  xhr.onload = () => {
    signal.removeEventListener('abort', abort);
    resolve({
      data: config.responseType === 'blob' ? xhr.response : parseText(xhr.responseText),
      status: xhr.status,
      statusText: xhr.statusText,
      headers: parseXhrHeaders(xhr.getAllResponseHeaders())
    });
  };
  xhr.onerror = () => {
    signal.removeEventListener('abort', abort);
    reject(new TypeError('Network Error'));
  };
  xhr.onabort = () => {
    const error = new Error('aborted');
    error.name = 'AbortError';
    reject(error);
  };
  xhr.send(body ?? null);
});

export function createHttpClient(defaults = {}) {
  const hooks = {
    request: defaults.onRequest,
    response: defaults.onResponse,
    error: defaults.onError
  };

  const request = async (input) => {
    const config = {
      timeout: defaults.timeout ?? 0,
      ...input,
      method: (input.method || 'get').toUpperCase(),
      headers: { ...(defaults.headers || {}), ...(input.headers || {}) }
    };
    // 统一使用小写请求头键，方便拦截逻辑增删
    config.headers = Object.fromEntries(
      Object.entries(config.headers).map(([key, value]) => [key.toLowerCase(), value])
    );

    await hooks.request?.(config);

    const headers = { ...config.headers };
    const body = prepareBody(config, headers);
    const url = joinUrl(defaults.baseURL, config.url) + buildQuery(config.params);

    const controller = new AbortController();
    let timedOut = false;
    const timer = config.timeout > 0
      ? setTimeout(() => {
        timedOut = true;
        controller.abort();
      }, config.timeout)
      : null;
    const external = config.signal;
    const forwardAbort = () => controller.abort();
    if (external) {
      if (external.aborted) controller.abort();
      else external.addEventListener('abort', forwardAbort, { once: true });
    }

    const transport = config.onUploadProgress && typeof XMLHttpRequest !== 'undefined'
      ? xhrTransport
      : fetchTransport;

    let raw;
    try {
      raw = await transport({ url, method: config.method, headers, body, signal: controller.signal, config });
    } catch (cause) {
      let error;
      if (timedOut) {
        error = createError(`timeout of ${config.timeout}ms exceeded`, 'ECONNABORTED', config);
      } else if (cause?.name === 'AbortError') {
        error = createError('canceled', 'ERR_CANCELED', config);
      } else {
        error = createError('Network Error', 'ERR_NETWORK', config);
      }
      error.cause = cause;
      return hooks.error ? hooks.error(error) : Promise.reject(error);
    } finally {
      if (timer) clearTimeout(timer);
      external?.removeEventListener('abort', forwardAbort);
    }

    const response = { ...raw, config };
    if (raw.status >= 200 && raw.status < 300) {
      return hooks.response ? hooks.response(response) : response;
    }

    const error = createError(
      `Request failed with status code ${raw.status}`,
      raw.status >= 500 ? 'ERR_BAD_RESPONSE' : 'ERR_BAD_REQUEST',
      config,
      response
    );
    return hooks.error ? hooks.error(error) : Promise.reject(error);
  };

  const withoutBody = (method) => (url, config = {}) => request({ ...config, method, url });
  const withBody = (method) => (url, data, config = {}) => request({ ...config, method, url, data });

  return {
    // 与 axios 实例的 defaults 对应，供需要直接拼接地址的调用方（如 SSE 流）读取
    defaults: { baseURL: defaults.baseURL || '', timeout: defaults.timeout ?? 0, headers: { ...(defaults.headers || {}) } },
    request,
    get: withoutBody('get'),
    delete: withoutBody('delete'),
    post: withBody('post'),
    put: withBody('put'),
    patch: withBody('patch')
  };
}
