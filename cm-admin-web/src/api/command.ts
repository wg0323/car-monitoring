import request, { type ApiEnvelope, type PageResult } from './request';

/** 可下发点名的车辆候选，online 与实时监控页同一份在线租约口径。 */
export interface CommandVehicleOption {
  id: string;
  plateNo: string;
  simNo: string;
  online: boolean;
}

/** 终端命令行：状态原值与中文文案都由服务端给出。 */
export interface CommandRow {
  commandId: string;
  vehicleId: string;
  plateNo: string;
  simNo: string;
  commandCode: string;
  commandName: string;
  commandData: string;
  commandStatus: string;
  statusText: string;
  sentAtText: string | null;
  ackAtText: string | null;
  createTimeText: string;
  remark: string;
}

export interface CommandQuery {
  page: number;
  size: number;
  plateNo?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/** 809 指令与报文行：本端下发与上级下发共用。 */
export interface GovCommandRow {
  id: string;
  platformId: number;
  sn: number;
  cmd: number;
  cmdText: string;
  subCmd: number;
  descr: string;
  plateNo: string;
  plateColor: number;
  cmdData: string;
  source: string;
  sourceText: string;
  commandStatus: string;
  createTimeText: string;
  updateTimeText: string;
}

export interface GovCommandQuery {
  page: number;
  size: number;
  source?: string;
  plateNo?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

export interface LinkState {
  platformId: number;
  mainLinkState: string;
  subLinkState: string;
  pendingCount: number;
  lastError: string;
}

export async function fetchCommandCandidates(keyword: string): Promise<CommandVehicleOption[]> {
  const response = await request.get<ApiEnvelope<CommandVehicleOption[]>>('/commands/candidates', {
    params: { keyword: keyword || undefined },
  });
  return response.data.data;
}

export async function sendPositionQuery(vehicleId: string): Promise<string> {
  const response = await request.post<ApiEnvelope<string>>('/commands/position-query', { vehicleId });
  return response.data.data;
}

export async function fetchCommands(query: CommandQuery): Promise<PageResult<CommandRow>> {
  const response = await request.get<ApiEnvelope<PageResult<CommandRow>>>('/commands', {
    params: {
      page: query.page,
      size: query.size,
      plateNo: query.plateNo || undefined,
      status: query.status || undefined,
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
    },
  });
  return response.data.data;
}

export async function fetchGovCommands(query: GovCommandQuery): Promise<PageResult<GovCommandRow>> {
  const response = await request.get<ApiEnvelope<PageResult<GovCommandRow>>>('/gov809/commands', {
    params: {
      page: query.page,
      size: query.size,
      source: query.source || undefined,
      plateNo: query.plateNo || undefined,
      status: query.status || undefined,
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
    },
  });
  return response.data.data;
}

export async function sendGovLinkCommand(linkCmd: number): Promise<number> {
  const response = await request.post<ApiEnvelope<number>>('/gov809/link-commands', { linkCmd });
  return response.data.data;
}

export async function fetchLinkState(): Promise<LinkState> {
  const response = await request.get<ApiEnvelope<LinkState>>('/gov809/link-state');
  return response.data.data;
}
