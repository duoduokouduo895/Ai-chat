export function extractMessage(payload) {
  if (!payload) {
    return '请求失败'
  }

  if (typeof payload === 'string') {
    return payload
  }

  return payload.message || payload.msg || '请求失败'
}

export function unwrapResponse(payload) {
  if (payload == null) {
    return null
  }

  if (Array.isArray(payload)) {
    return payload
  }

  if (typeof payload !== 'object') {
    return payload
  }

  if ('success' in payload) {
    if (!payload.success) {
      throw new Error(extractMessage(payload))
    }

    return payload
  }

  if ('code' in payload && Number(payload.code) >= 400) {
    throw new Error(extractMessage(payload))
  }

  return payload
}
