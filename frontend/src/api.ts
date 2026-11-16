type Csrf = { token: string; headerName: string }
type Result<T> = { code: number; msg: string; data: T }

const SUCCESS = 1
const csrfPath = '/api/csrf'
const loginPath = '/api/login'
const logoutPath = '/api/logout'
const mePath = '/api/me'
const passwordPath = '/api/password'
const xlsxMediaType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
let token: Csrf | undefined

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: number) {
    super(message)
  }
}

async function readResult<T>(response: Response): Promise<T> {
  const payload = await response.json().catch(() => null) as Result<T> | null
  if (response.ok && payload?.code === SUCCESS) return payload.data

  const fallback = response.status === 401 ? '请重新登录' : '服务暂时不可用，请稍后重试'
  throw new ApiError(response.status, payload?.msg || fallback, payload?.code)
}

async function csrf(): Promise<Csrf> {
  if (!token) {
    const response = await fetch(csrfPath, { cache: 'no-store', credentials: 'same-origin' })
    token = await readResult<Csrf>(response)
  }
  return token
}

function handleSession(response: Response, path: string, method: string) {
  if (response.status === 401 && path !== loginPath) {
    token = undefined
    if (path !== mePath) window.dispatchEvent(new Event('session-expired'))
  }
  if (response.status === 403 && method !== 'GET') token = undefined
}

export async function api<T>(path: string, method = 'GET', input?: unknown, extraHeaders?: HeadersInit): Promise<T> {
  const headers = new Headers(extraHeaders)
  headers.set('Accept', 'application/json')
  let body: BodyInit | undefined
  if (method !== 'GET') {
    const sessionToken = await csrf()
    headers.set(sessionToken.headerName, sessionToken.token)
  }
  if (input instanceof URLSearchParams || input instanceof FormData) body = input
  else if (input !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(input)
  }

  const response = await fetch(path, { method, headers, body, cache: 'no-store', credentials: 'same-origin' })
  handleSession(response, path, method)

  const data = await readResult<T>(response)
  if (path === loginPath || path === logoutPath || path === passwordPath) token = undefined
  return data
}

export async function downloadWorkbook(path: string, fallbackName: string, file?: FormData) {
  const method = file ? 'POST' : 'GET'
  const headers = new Headers({ Accept: xlsxMediaType + ', application/json' })
  if (file) {
    const sessionToken = await csrf()
    headers.set(sessionToken.headerName, sessionToken.token)
  }
  const response = await fetch(path, {
    method, headers, body: file,
    credentials: 'same-origin', cache: 'no-store',
  })
  handleSession(response, path, method)
  if (!response.ok) await readResult<never>(response)
  if (response.headers.get('Content-Type')?.split(';')[0] !== xlsxMediaType) {
    throw new ApiError(response.status, '没有收到有效的 Excel 文件')
  }

  let filename = fallbackName
  const encoded = response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try { filename = decodeURIComponent(encoded) } catch { /* Keep the fallback file name. */ }
  }
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '无法连接服务，请检查后端是否启动'
}
