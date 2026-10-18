import axios from 'axios'
import { readToken } from '@/utils/session'

let handlers = {}
export function configureAuthHandlers(next) { handlers = next }

export const http = axios.create({ baseURL: '/api', timeout: 12000 })

http.interceptors.request.use((config) => {
  if (!config.public) {
    const token = readToken()
    config.crmToken = token
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

function rejectResponse(response, config, networkError) {
  const status = response?.status || 0
  const code = response?.data?.code || 0
  const serverMessage = response?.data?.message
  const message = typeof serverMessage === 'string' ? serverMessage
    : networkError?.code === 'ECONNABORTED' ? '请求超时，请稍后重试'
      : status === 503 ? '服务暂不可用，请稍后重试' : '连接失败，请检查网络或稍后重试'
  if (!config?.public && config?.crmToken && readToken() === config.crmToken) {
    if (status === 401) handlers.expired?.()
    else if (code === 40301) handlers.passwordRequired?.()
    else if (status === 403) handlers.forbidden?.()
  }
  return Promise.reject(new ApiError(message, code, status))
}

http.interceptors.response.use(
  (response) => response.data?.code === 0 ? response.data.data
    : rejectResponse(response, response.config),
  (error) => rejectResponse(error.response, error.config, error),
)
