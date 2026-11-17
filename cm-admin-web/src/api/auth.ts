import request, { type ApiEnvelope } from './request';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginUser {
  id: string;
  username: string;
  displayName: string;
  superAdmin: boolean;
}

export interface MenuItem {
  permissionCode: string;
  name: string;
  icon: string;
  menuSort: number;
  url: string;
}

export interface SessionInfo {
  user: LoginUser;
  permissions: string[];
  menus: MenuItem[];
}

/** 令牌值由服务端写入 XSRF-TOKEN cookie，这里只取请求头名称。 */
export interface CsrfToken {
  headerName: string;
  parameterName: string;
}

export async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await request.get<ApiEnvelope<CsrfToken>>('/auth/csrf');
  return response.data.data;
}

export async function login(payload: LoginRequest): Promise<SessionInfo> {
  const response = await request.post<ApiEnvelope<SessionInfo>>('/auth/login', payload);
  return response.data.data;
}

export async function fetchSession(): Promise<SessionInfo> {
  const response = await request.get<ApiEnvelope<SessionInfo>>('/auth/me');
  return response.data.data;
}

export async function logout(): Promise<void> {
  await request.post<ApiEnvelope<null>>('/auth/logout');
}
