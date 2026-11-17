import request, { type ApiEnvelope, type PageResult } from './request';

/** 809 报警督办行，与后端 UrgeTodoVO 同构。 */
export interface UrgeTodoRow {
  id: string;
  superviseId: number;
  plateNo: string;
  plateColorName: string;
  warnSourceText: string;
  warnTypeText: string;
  warnTimeText: string;
  supervisionEndTimeText: string;
  supervisionLevelText: string;
  supervisor: string;
  supervisorTel: string;
  supervisorEmail: string;
  acknowledged: boolean;
  ackResult: number | null;
  ackResultText: string;
  ackTimeText: string | null;
  createTimeText: string;
}

export interface UrgeTodoQuery {
  page: number;
  size: number;
  plateNo?: string;
  acknowledged?: boolean;
  startTime?: string;
  endTime?: string;
}

/** 旧系统督办应答下拉的四项取值。 */
export const URGE_ACK_RESULTS = [
  { value: 0, label: '处理中' },
  { value: 1, label: '已处理完毕' },
  { value: 2, label: '不做处理' },
  { value: 3, label: '将来处理' },
];

export async function fetchUrgeTodos(query: UrgeTodoQuery): Promise<PageResult<UrgeTodoRow>> {
  const response = await request.get<ApiEnvelope<PageResult<UrgeTodoRow>>>('/gov809/urge-todos', {
    params: {
      page: query.page,
      size: query.size,
      plateNo: query.plateNo || undefined,
      acknowledged: query.acknowledged === undefined ? undefined : query.acknowledged,
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
    },
  });
  return response.data.data;
}

/** 增量督办轮询：只返回未应答的新督办，用于自动弹出详情窗口。 */
export async function fetchUrgeTodoNotifications(sinceId: string, minutes = 60): Promise<UrgeTodoRow[]> {
  const response = await request.get<ApiEnvelope<UrgeTodoRow[]>>('/gov809/urge-todos/notifications', {
    params: { sinceId: sinceId || '0', minutes },
  });
  return response.data.data;
}

/** 受理应答：后端写「待下发」指令，由企业侧链路进程发送 0x1401。 */
export async function acknowledgeUrgeTodo(id: string, result: number): Promise<string> {
  const response = await request.post<ApiEnvelope<string>>(`/gov809/urge-todos/${id}/ack`, { result });
  return response.data.data;
}
