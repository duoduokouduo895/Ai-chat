import assert from 'node:assert/strict';
import { createHttpClient } from '../src/services/httpClient.mjs';

const calls = [];
let nextResponse = () => new Response('{}', { status: 200 });
globalThis.fetch = async (url, init) => {
  calls.push({ url, init });
  if (init.signal?.aborted) throw Object.assign(new Error('aborted'), { name: 'AbortError' });
  return nextResponse(init);
};

const seen = [];
assert.equal(createHttpClient({ baseURL: '/api' }).defaults.baseURL, '/api', 'defaults.baseURL is exposed like axios');
const client = createHttpClient({
  baseURL: '/api',
  timeout: 50,
  headers: { 'Content-Type': 'application/json' },
  onRequest(config) {
    seen.push(config.headers);
    if (!config.skipAuth) config.headers.authorization = 'Bearer t';
  },
  onResponse: (response) => ({ ...response, intercepted: true }),
  onError: (error) => Promise.reject(Object.assign(error, { normalized: true }))
});

// GET：URL 拼接、参数序列化（数组为重复键、跳过空值）、拦截器
nextResponse = () => new Response(JSON.stringify([{ id: 1 }]), {
  status: 200,
  headers: { 'X-Refreshed-Token': 'new-token' }
});
const list = await client.get('/search', { params: { query: 'a b', models: ['m1', 'm2'], empty: [], skip: null, page: 0 } });
assert.equal(calls[0].url, '/api/search?query=a+b&models=m1&models=m2&page=0');
assert.equal(calls[0].init.method, 'GET');
assert.equal(calls[0].init.body, undefined);
assert.equal(calls[0].init.headers.authorization, 'Bearer t');
assert.deepEqual(list.data, [{ id: 1 }]);
assert.equal(list.status, 200);
assert.equal(list.headers['x-refreshed-token'], 'new-token', 'headers are a lowercase plain object');
assert.equal(list.intercepted, true);
assert.equal(seen[0]['content-type'], 'application/json', 'header keys are lowercased before hooks run');

// POST JSON 与 null 请求体
nextResponse = () => new Response('', { status: 200 });
const created = await client.post('/users/guest', null, { skipAuth: true });
assert.equal(calls[1].init.body, undefined);
assert.equal(calls[1].init.headers.authorization, undefined);
assert.equal(created.data, '', 'empty body parses to empty string like axios');
await client.put('/knowledge/bases/1', { name: '手册' });
assert.equal(calls[2].init.body, '{"name":"手册"}');
assert.equal(calls[2].init.headers['content-type'], 'application/json');

// FormData：不手动设置 Content-Type
const form = new FormData();
form.append('file', new Blob(['x']), 'a.txt');
await client.post('/knowledge/documents/upload', form);
assert.equal(calls[3].init.body, form);
assert.equal('content-type' in calls[3].init.headers, false);

// 非 JSON 文本原样返回；blob 响应
nextResponse = () => new Response('plain text', { status: 200 });
assert.equal((await client.get('/text')).data, 'plain text');
nextResponse = () => new Response('binary', { status: 200 });
const blob = await client.get('/file', { responseType: 'blob' });
assert.equal(await blob.data.text(), 'binary');

// HTTP 错误：error.response 结构与 axios 一致，并经过错误拦截器
nextResponse = () => new Response(JSON.stringify({ message: '无权限' }), { status: 403 });
await assert.rejects(client.delete('/conversations/1'), (error) => {
  assert.equal(error.message, 'Request failed with status code 403');
  assert.equal(error.code, 'ERR_BAD_REQUEST');
  assert.equal(error.response.status, 403);
  assert.equal(error.response.data.message, '无权限');
  assert.equal(error.config.url, '/conversations/1');
  assert.equal(error.normalized, true);
  return true;
});
nextResponse = () => new Response('Bad Gateway', { status: 502 });
await assert.rejects(client.get('/x'), (error) => error.code === 'ERR_BAD_RESPONSE' && error.response.data === 'Bad Gateway');

// 网络错误：无 response
nextResponse = () => { throw new TypeError('Failed to fetch'); };
await assert.rejects(client.get('/x'), (error) => error.code === 'ERR_NETWORK' && !error.response && error.message === 'Network Error');

// 超时：ECONNABORTED，且消息包含 timeout（apiErrorService 依赖这两点）
nextResponse = (init) => new Promise((_, reject) => {
  init.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
});
await assert.rejects(client.get('/slow', { timeout: 10 }), (error) => (
  error.code === 'ECONNABORTED' && /timeout/.test(error.message) && !error.response
));

// 外部取消
const controller = new AbortController();
const pending = client.get('/slow', { timeout: 0, signal: controller.signal });
controller.abort();
await assert.rejects(pending, (error) => error.code === 'ERR_CANCELED');

// 上传进度：改用 XHR 并转发进度事件与响应头
class FakeXHR {
  constructor() { this.headers = {}; this.upload = {}; FakeXHR.last = this; }
  open(method, url) { this.method = method; this.url = url; }
  setRequestHeader(key, value) { this.headers[key] = value; }
  getAllResponseHeaders() { return 'X-Refreshed-Token: xhr-token\r\ncontent-type: application/json\r\n'; }
  abort() { this.onabort?.(); }
  send(body) {
    this.body = body;
    this.upload.onprogress?.({ loaded: 5, total: 10, lengthComputable: true });
    this.status = 201;
    this.statusText = 'Created';
    this.responseText = '{"id":7}';
    this.onload();
  }
}
globalThis.XMLHttpRequest = FakeXHR;
const progress = [];
const uploaded = await client.post('/knowledge/documents/upload', form, {
  onUploadProgress: (event) => progress.push(event.loaded / event.total)
});
assert.deepEqual(progress, [0.5]);
assert.equal(FakeXHR.last.method, 'POST');
assert.equal(FakeXHR.last.url, '/api/knowledge/documents/upload');
assert.equal(FakeXHR.last.body, form);
assert.equal(FakeXHR.last.headers.authorization, 'Bearer t');
assert.equal('content-type' in FakeXHR.last.headers, false);
assert.equal(uploaded.status, 201);
assert.deepEqual(uploaded.data, { id: 7 });
assert.equal(uploaded.headers['x-refreshed-token'], 'xhr-token');

console.log('http_client_test=PASS');
