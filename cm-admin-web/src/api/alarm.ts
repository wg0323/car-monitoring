import request, { type ApiEnvelope, type PageResult } from './request';

/** 报警通知行，与后端 AlarmNotificationVO 同构；ID 是 19 位雪花，一律走字符串。 */
export interface AlarmNotifyRow {
  id: string;
  plateNo: string;
  plateColorName: string;
  simNo: string;
  alarmTypeName: string;
  alarmSourceName: string;
  location: string;
  startTimeText: string;
  processed: boolean;
  vehicleId: string | null;
  latitude: number | null;
  longitude: number | null;
}

/** 报警处理页行，与后端 AlarmRecordVO 同构：在通知行基础上补处理人与备注。 */
export interface AlarmRecordRow extends AlarmNotifyRow {
  description: string;
  velocity: number | null;
  timeSpan: number | null;
  processedUserName: string;
  processedTimeText: string;
  remark: string;
}

export interface AlarmQuery {
  page: number;
  size: number;
  startTime: string;
  endTime: string;
  /** 处理状态：true 已处理，false 未处理，不传为全部。 */
  processed?: boolean | '';
  plateNo?: string;
}

/** 批量处理结果：成功条数与逐条跳过原因。 */
export interface AlarmProcessResult {
  processed: number;
  skipped: { id: string; reason: string }[];
}

export async function fetchAlarms(query: AlarmQuery): Promise<PageResult<AlarmRecordRow>> {
  const response = await request.get<ApiEnvelope<PageResult<AlarmRecordRow>>>('/alarms', {
    params: {
      page: query.page,
      size: query.size,
      startTime: query.startTime,
      endTime: query.endTime,
      processed: query.processed === '' ? undefined : query.processed,
      plateNo: query.plateNo || undefined,
    },
  });
  return response.data.data;
}

/**
 * 增量报警轮询。sinceId 传上次返回的最大 ID，0 表示只取基线。
 * 返回按主键升序，最后一条的 id 就是下一次的 sinceId。
 */
export async function fetchAlarmNotifications(sinceId: string, minutes = 5): Promise<AlarmNotifyRow[]> {
  const response = await request.get<ApiEnvelope<AlarmNotifyRow[]>>('/alarms/notifications', {
    params: { sinceId: sinceId || '0', minutes },
  });
  return response.data.data;
}

/** 单条处理：已处理、越界或不存在的记录返回 400。 */
export async function processAlarm(alarmId: string, remark: string): Promise<void> {
  await request.post<ApiEnvelope<null>>(`/alarms/${alarmId}/process`, { remark });
}

/** 批量处理：逐条独立，失败项在 skipped 里带原因，不整体回滚。 */
export async function processAlarms(ids: string[], remark: string): Promise<AlarmProcessResult> {
  const response = await request.post<ApiEnvelope<AlarmProcessResult>>('/alarms/batch-process', {
    ids,
    remark,
  });
  return response.data.data;
}
