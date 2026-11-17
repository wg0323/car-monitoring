import request, { type ApiEnvelope } from './request';

/** 与后端 MonitorMapConfigVO 同构；围栏、监控、轨迹三页共用同一份配置口径。 */
export interface MapConfig {
  configured: boolean;
  engine: string;
  key: string;
  securityCode: string;
  centerLat: number;
  centerLng: number;
  centerZoom: number;
}

export interface MapPreferencePayload {
  centerLat: number;
  centerLng: number;
  zoom: number;
}

export async function fetchMapConfig(): Promise<MapConfig> {
  const response = await request.get<ApiEnvelope<MapConfig>>('/map/config');
  return response.data.data;
}

/** 保存当前账号默认中心；保存后监控、轨迹与围栏页都以它为初始视野。 */
export async function saveMapPreference(payload: MapPreferencePayload): Promise<void> {
  await request.put<ApiEnvelope<unknown>>('/map/preference', payload);
}
