/* 文件作用：统一 axios 请求、令牌携带、响应解析与认证异常处理。 */
import axios from 'axios';
import { readToken } from '@/utils/session';

let handlers = {};
/** 注册会话失效、首次改密和无权限处理器。 */
export function configureAuthHandlers(next) {
  handlers = next;
}

export const http = axios.create({ baseURL: '/api', timeout: 12000 });

http.interceptors.request.use(
  /* 为受保护请求附加令牌及对应会话标记。 */ (config) => {
    if (!config.public) {
      const token = readToken();
      config.crmToken = token;
      if (token) config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
);

export class ApiError extends Error {
  /** 保存接口错误的消息、业务码及 HTTP 状态。 */
  constructor(message, code, status) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

/** 统一处理业务或网络失败并触发必要的认证跳转。 */
function rejectResponse(response, config, networkError) {
  const status = response?.status || 0;
  const code = response?.data?.code || 0;
  const serverMessage = response?.data?.message;
  const message =
    typeof serverMessage === 'string'
      ? serverMessage
      : networkError?.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : status === 503
          ? '服务暂不可用，请稍后重试'
          : '连接失败，请检查网络或稍后重试';
  if (!config?.public && config?.crmToken && readToken() === config.crmToken) {
    if (status === 401) handlers.expired?.();
    else if (code === 40301) handlers.passwordRequired?.();
    else if (status === 403) handlers.forbidden?.();
  }
  return Promise.reject(new ApiError(message, code, status));
}

http.interceptors.response.use(
  /** 提取成功响应数据或转换业务错误。 */
  (response) =>
    response.data?.code === 0 ? response.data.data : rejectResponse(response, response.config),
  /** 将 HTTP 或网络异常交给统一错误处理。 */
  (error) => rejectResponse(error.response, error.config, error),
);
