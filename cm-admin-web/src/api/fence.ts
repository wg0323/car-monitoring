import request, { type ApiEnvelope, type PageResult } from './request';

export type FenceAreaType = 'circle' | 'rect' | 'polygon';

/** 与后端 FenceVO 同构；id 由服务端序列化为字符串。 */
export interface FenceRow {
  id: string;
  departmentId: string;
  departmentName: string;
  name: string;
  areaType: FenceAreaType;
  points: string;
  centerLat: number;
  centerLng: number;
  radius: number;
  alarmEnterDriver: boolean;
  alarmEnterPlatform: boolean;
  alarmExitDriver: boolean;
  alarmExitPlatform: boolean;
  byTime: boolean;
  startTimeText: string | null;
  endTimeText: string | null;
  limitSpeed: boolean;
  maxSpeed: number;
  delaySeconds: number;
  remark: string;
  createTimeText: string;
  updateTimeText: string;
}

export interface FenceQuery {
  page: number;
  size: number;
  name?: string;
  departmentId?: string;
}

/** 保存请求：几何由地图绘制产生，圆形带 radius，矩形与多边形忽略 radius。 */
export interface FenceSavePayload {
  name: string;
  departmentId: string;
  areaType: FenceAreaType;
  points: string;
  radius?: number;
  alarmEnterDriver: boolean;
  alarmEnterPlatform: boolean;
  alarmExitDriver: boolean;
  alarmExitPlatform: boolean;
  byTime: boolean;
  startDate: string | null;
  endDate: string | null;
  limitSpeed: boolean;
  maxSpeed: number | null;
  delaySeconds: number;
  remark: string;
}

export async function fetchFences(query: FenceQuery): Promise<PageResult<FenceRow>> {
  const response = await request.get<ApiEnvelope<PageResult<FenceRow>>>('/fences', {
    params: {
      page: query.page,
      size: query.size,
      name: query.name || undefined,
      departmentId: query.departmentId || undefined,
    },
  });
  return response.data.data;
}

export async function createFence(payload: FenceSavePayload): Promise<FenceRow> {
  const response = await request.post<ApiEnvelope<FenceRow>>('/fences', payload);
  return response.data.data;
}

export async function updateFence(id: string, payload: FenceSavePayload): Promise<FenceRow> {
  const response = await request.put<ApiEnvelope<FenceRow>>(`/fences/${id}`, payload);
  return response.data.data;
}

export async function deleteFence(id: string): Promise<void> {
  await request.delete<ApiEnvelope<unknown>>(`/fences/${id}`);
}
