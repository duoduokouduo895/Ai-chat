const DEFAULT_ERROR_MESSAGE = '操作失败，请稍后重试';

const hasServerMessage = (error) => {
  const message = error?.response?.data?.message;
  return typeof message === 'string' && message.trim().length > 0;
};

const getServerMessage = (error) => error.response.data.message.trim();

export const getApiErrorStatus = (error) => error?.response?.status || 0;

export const getApiErrorType = (error) => {
  if (error?.code === 'ECONNABORTED' || /timeout/i.test(error?.message || '')) {
    return 'timeout';
  }

  if (!error?.response) {
    return 'network';
  }

  const status = getApiErrorStatus(error);

  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not_found';
  if (status === 409) return 'conflict';
  if (status === 413) return 'payload_too_large';
  if (status === 429) return 'rate_limited';
  if (status >= 500) return 'server';

  return 'unknown';
};

export const getApiErrorMessage = (error, fallbackMessage = DEFAULT_ERROR_MESSAGE) => {
  if (hasServerMessage(error)) {
    return getServerMessage(error);
  }

  switch (getApiErrorType(error)) {
    case 'timeout':
      return '请求超时，请检查网络后重试';
    case 'network':
      return '无法连接到服务，请确认网络或后端服务状态';
    case 'unauthorized':
      return '登录状态已过期，请重新登录';
    case 'forbidden':
      return '当前账号没有权限执行此操作';
    case 'not_found':
      return '请求的资源不存在';
    case 'conflict':
      return '当前操作与已有数据冲突，请刷新后重试';
    case 'payload_too_large':
      return '上传内容过大，请调整后重试';
    case 'rate_limited':
      return '操作过于频繁，请稍后再试';
    case 'server':
      return '服务暂时不可用，请稍后重试';
    default:
      return fallbackMessage;
  }
};

export const normalizeApiError = (error, fallbackMessage) => ({
  status: getApiErrorStatus(error),
  type: getApiErrorType(error),
  message: getApiErrorMessage(error, fallbackMessage),
  raw: error
});

export default {
  getApiErrorMessage,
  getApiErrorStatus,
  getApiErrorType,
  normalizeApiError
};
