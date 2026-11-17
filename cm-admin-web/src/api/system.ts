import request, { type ApiEnvelope } from './request';

export interface SystemInfo {
  applicationName: string;
  stage: string;
  activeProfiles: string;
  javaVersion: string;
}

export async function fetchSystemInfo(): Promise<ApiEnvelope<SystemInfo>> {
  const response = await request.get<ApiEnvelope<SystemInfo>>('/system/info');
  return response.data;
}
