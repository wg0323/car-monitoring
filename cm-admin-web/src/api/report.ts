import request, { type ApiEnvelope, type PageResult } from './request';

export interface ReportOption {
  code: string;
  name: string;
}

export interface ReportOptions {
  alarmTypes: ReportOption[];
  alarmSources: ReportOption[];
  plateColors: ReportOption[];
  onlineRecordTypes: ReportOption[];
  onlineStatuses: ReportOption[];
}

export interface ReportAlarmRow {
  plateNo: string;
  plateColorName: string;
  alarmTypeName: string;
  alarmSourceName: string;
  location: string;
  startTimeText: string;
  endTimeText: string;
  timeSpan: number | null;
  departmentName: string;
  velocity: number | null;
}

export interface ReportOnlineRecordRow {
  plateNo: string;
  plateColorName: string;
  recordTypeName: string;
  location: string;
  startTimeText: string;
  endTimeText: string;
  timeSpan: number | null;
  departmentName: string;
}

export interface ReportOnlineVehicleRow {
  plateNo: string;
  plateColorName: string;
  simNo: string;
  departmentName: string;
  onlineStatusName: string;
  onlineTimeText: string;
  offlineMinutes: number | null;
  runStatus: string;
  vehicleType: string;
  installDateText: string;
}

export interface AlarmReportQuery {
  page: number;
  size: number;
  plateNo?: string;
  departmentId?: string;
  alarmType?: string;
  alarmSource?: string;
  startTime: string;
  endTime: string;
}

export interface OnlineRecordReportQuery {
  page: number;
  size: number;
  plateNo?: string;
  departmentId?: string;
  eventType?: string;
  startTime: string;
  endTime: string;
}

export interface OnlineVehicleReportQuery {
  page: number;
  size: number;
  plateNo?: string;
  simNo?: string;
  departmentId?: string;
  status?: string;
  offlineDays?: number;
}

export interface ParkingReportQuery {
  page: number;
  size: number;
  plateNo?: string;
  departmentId?: string;
  alarmSource?: string;
  startTime: string;
  endTime: string;
}

const REPORT_BASE = '/reports';

const API_BASE = '/api/v1/reports';

function compact(params: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value;
    }
  });
  return result;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function todayStart(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} 00:00:00`;
}

export function todayEnd(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} 23:59:59`;
}

/** 导出走浏览器直连 GET，与列表共用同一组筛选参数；CSRF 只拦非 GET，无需令牌。 */
export function buildExportUrl(path: string, params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  Object.entries(compact(params)).forEach(([key, value]) => {
    search.set(key, String(value));
  });
  return `${API_BASE}${path}?${search.toString()}`;
}

export async function fetchReportOptions(): Promise<ReportOptions> {
  const response = await request.get<ApiEnvelope<ReportOptions>>(`${REPORT_BASE}/options`);
  return response.data.data;
}

export async function fetchAlarmReport(query: AlarmReportQuery): Promise<PageResult<ReportAlarmRow>> {
  const response = await request.get<ApiEnvelope<PageResult<ReportAlarmRow>>>(`${REPORT_BASE}/alarms`, {
    params: compact({ ...query }),
  });
  return response.data.data;
}

export async function fetchOnlineRecordReport(
  query: OnlineRecordReportQuery,
): Promise<PageResult<ReportOnlineRecordRow>> {
  const response = await request.get<ApiEnvelope<PageResult<ReportOnlineRecordRow>>>(
    `${REPORT_BASE}/online-records`,
    { params: compact({ ...query }) },
  );
  return response.data.data;
}

export async function fetchOnlineVehicleReport(
  query: OnlineVehicleReportQuery,
): Promise<PageResult<ReportOnlineVehicleRow>> {
  const response = await request.get<ApiEnvelope<PageResult<ReportOnlineVehicleRow>>>(
    `${REPORT_BASE}/online-vehicles`,
    { params: compact({ ...query }) },
  );
  return response.data.data;
}

export async function fetchParkingReport(query: ParkingReportQuery): Promise<PageResult<ReportAlarmRow>> {
  const response = await request.get<ApiEnvelope<PageResult<ReportAlarmRow>>>(`${REPORT_BASE}/parkings`, {
    params: compact({ ...query }),
  });
  return response.data.data;
}

