import request, { type ApiEnvelope, type PageResult } from './request';

/** 与后端 MonitorVehicleVO 同构。没有定位时经纬度为 null，不是 0。 */
export interface MonitorVehicleRow {
  id: string;
  plateNo: string;
  simNo: string;
  departmentName: string;
  online: boolean;
  latitude: number | null;
  longitude: number | null;
  speedKph: number | null;
  course: number | null;
  deviceTime: string | null;
}

export interface MonitorSummary {
  total: number;
  online: number;
  offline: number;
  parking: number;
  onlineRate: number;
}

export interface MonitorMapConfig {
  configured: boolean;
  engine: string;
  key: string;
  securityCode: string;
  centerLat: number;
  centerLng: number;
  centerZoom: number;
}

export interface MonitorQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: string;
  /** '' 不筛；'true' 只查在线；'false' 只查离线。 */
  online?: '' | 'true' | 'false';
}

export async function fetchMonitorVehicles(query: MonitorQuery): Promise<PageResult<MonitorVehicleRow>> {
  const response = await request.get<ApiEnvelope<PageResult<MonitorVehicleRow>>>('/monitor/vehicles', {
    params: {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      departmentId: query.departmentId || undefined,
      online: query.online || undefined,
    },
  });
  return response.data.data;
}

export async function fetchMonitorSummary(): Promise<MonitorSummary> {
  const response = await request.get<ApiEnvelope<MonitorSummary>>('/monitor/summary');
  return response.data.data;
}

export async function fetchMonitorMapConfig(): Promise<MonitorMapConfig> {
  const response = await request.get<ApiEnvelope<MonitorMapConfig>>('/monitor/map-config');
  return response.data.data;
}
