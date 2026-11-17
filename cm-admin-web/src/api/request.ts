import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const REQUEST_TIMEOUT_MS = 10000;
const DEFAULT_API_BASE = '/api/v1';

const request = axios.create({
  baseURL: DEFAULT_API_BASE,
  timeout: REQUEST_TIMEOUT_MS,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 从 XSRF-TOKEN cookie 取令牌写进请求头。
 * 不依赖 axios 的同源自动判定：开发期经 Vite 代理、生产经 Nginx 同源，两种形态都要带上。
 */
function readXsrfToken(): string {
  const matched = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return matched ? matched[1] : '';
}

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = readXsrfToken();
  if (token) {
    config.headers.set('X-XSRF-TOKEN', token);
  }
  return config;
});

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

/** 统一分页响应，与后端 PageResult 同构；各功能域的列表接口共用这一份。 */
export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

/** 业务错误：非 0 code 或 HTTP 非 2xx 都抛这个，页面只处理一种异常。 */
export class ApiError extends Error {
  readonly status: number;

  readonly code: number;

  constructor(message: string, status: number, code: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

request.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<null>>) => {
    const status = error.response?.status ?? 0;
    if (status === 401) {
      const current = `${window.location.pathname}${window.location.search}`;
      if (window.location.pathname !== '/login') {
        window.location.href = `/login?redirect=${encodeURIComponent(current)}`;
      }
      return Promise.reject(new ApiError('未登录或会话已过期', status, 401));
    }
    const message = error.response?.data?.message ?? error.message ?? '请求失败';
    const code = error.response?.data?.code ?? -1;
    return Promise.reject(new ApiError(message, status, code));
  },
);

export default request;
