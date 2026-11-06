import { useUiStore } from '@/stores/ui'

export class ApiError extends Error {
  constructor(message, { status = 0, data = null } = {}) {
    super(message)

    this.name = 'ApiError'
    this.status = status
    this.data = data
  }
}

async function parseResponse(response) {
  if (response.status === 204) {
    return null
  }

  const contentType = response.headers.get('content-type') ?? ''

  if (contentType.includes('application/json')) {
    return response.json()
  }

  const text = await response.text()

  // 공공데이터포털은 JSON 본문을 text/plain 등으로 내려주는 경우가 있습니다.
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function getErrorMessage(data, status) {
  if (typeof data?.message === 'string' && data.message.trim()) {
    return data.message
  }

  if (status === 401 || status === 403) {
    return '요청 권한이 없습니다. API 인증키를 확인해 주세요.'
  }

  if (status === 404) {
    return '요청한 정보를 찾을 수 없습니다.'
  }

  if (status === 429) {
    return '요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.'
  }

  if (status >= 500) {
    return '서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  }

  return '요청을 처리하지 못했습니다.'
}

/**
 * 외부 API(절대 URL)를 호출하는 fetch 래퍼입니다.
 */
export async function apiRequest(url, options = {}) {
  const { showLoading = true, headers = {}, body, ...fetchOptions } = options

  const uiStore = useUiStore()

  if (showLoading) {
    uiStore.startLoading()
  }

  try {
    const requestHeaders = new Headers(headers)
    let requestBody = body

    const shouldSerializeBody =
      body !== undefined && body !== null && !(body instanceof FormData) && typeof body !== 'string'

    if (shouldSerializeBody) {
      requestHeaders.set('Content-Type', 'application/json')
      requestBody = JSON.stringify(body)
    }

    const response = await fetch(url, {
      ...fetchOptions,
      headers: requestHeaders,
      body: requestBody,
    })

    const data = await parseResponse(response)

    if (!response.ok) {
      throw new ApiError(getErrorMessage(data, response.status), {
        status: response.status,
        data,
      })
    }

    return data
  } catch (error) {
    if (error instanceof ApiError || error.name === 'AbortError') {
      throw error
    }

    throw new ApiError('서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.', {
      data: error,
    })
  } finally {
    if (showLoading) {
      uiStore.stopLoading()
    }
  }
}
