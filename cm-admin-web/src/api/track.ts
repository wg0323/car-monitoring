import request, { type ApiEnvelope } from './request';

export interface TrackPointRow {
  plateNo: string;
  plateColorName: string;
  departmentName: string;
  deviceTimeText: string;
  longitude: number;
  latitude: number;
  speedKph: number;
  recorderSpeedKph: number;
  mileage: number;
  oil: number;
  directionText: string;
  altitude: number;
  statusText: string;
  alarmFlagText: string;
  validText: string;
}

export interface TrackResult {
  vehicleId: string;
  plateNo: string;
  simNo: string;
  departmentName: string;
  total: number;
  points: TrackPointRow[];
}

export interface TrackQuery {
  vehicleId: string;
  startTime: string;
  endTime: string;
}

const TRACK_BASE = '/tracks';

const API_BASE = '/api/v1/tracks';

export async function fetchTrack(query: TrackQuery): Promise<TrackResult> {
  const response = await request.get<ApiEnvelope<TrackResult>>(TRACK_BASE, {
    params: {
      vehicleId: query.vehicleId,
      startTime: query.startTime,
      endTime: query.endTime,
    },
  });
  return response.data.data;
}

/** 导出走浏览器直连 GET，与查询共用同一组参数；CSRF 只拦非 GET，无需令牌。 */
export function buildTrackExportUrl(query: TrackQuery): string {
  const search = new URLSearchParams({
    vehicleId: query.vehicleId,
    startTime: query.startTime,
    endTime: query.endTime,
  });
  return `${API_BASE}/export?${search.toString()}`;
}

