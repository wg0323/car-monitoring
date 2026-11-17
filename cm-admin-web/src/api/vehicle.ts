import request, { type ApiEnvelope, type PageResult } from './request';

export interface VehicleRow {
  id: string;
  plateNo: string;
  simNo: string;
  departmentId: string;
  departmentName: string;
  vehicleType: string;
  terminalNo: string;
  driver: string;
  runStatus: string;
}

export interface VehicleQuery {
  page: number;
  size: number;
  plateNo?: string;
  simNo?: string;
  departmentId?: string;
  /** '' 不筛；'true' 只查在线；'false' 只查离线。 */
  online?: '' | 'true' | 'false';
}

export interface VehicleDetail {
  id: string;
  plateNo: string;
  plateColor: number | null;
  plateColorName: string;
  departmentId: string;
  departmentName: string;
  memberNo: string | null;
  terminalNo: string | null;
  simNo: string | null;
  vehicleType: string | null;
  useType: string | null;
  industry: string | null;
  region: string | null;
  runStatus: string | null;
  vehicleStatus: string | null;
  driver: string | null;
  driverMobile: string | null;
  monitor: string | null;
  monitorMobile: string | null;
  motorId: string | null;
  engineNo: string | null;
  frameNo: string | null;
  manufacture: string | null;
  modelNo: string | null;
  vendor: string | null;
  gpsTerminalType: string | null;
  operPermit: string | null;
  routes: string | null;
  buyTime: string | null;
  installDate: string | null;
  lastCheckTime: string | null;
  onlineTime: string | null;
  offlineTime: string | null;
  startDate: string | null;
  endDate: string | null;
  workHour: number | null;
  remark: string | null;
}

export interface DepartmentOption {
  id: string;
  parentId: string;
  name: string;
}

export async function fetchVehicles(query: VehicleQuery): Promise<PageResult<VehicleRow>> {
  const response = await request.get<ApiEnvelope<PageResult<VehicleRow>>>('/vehicles', {
    params: {
      page: query.page,
      size: query.size,
      plateNo: query.plateNo || undefined,
      simNo: query.simNo || undefined,
      departmentId: query.departmentId || undefined,
      online: query.online || undefined,
    },
  });
  return response.data.data;
}

export async function fetchVehicleDetail(id: string): Promise<VehicleDetail> {
  const response = await request.get<ApiEnvelope<VehicleDetail>>(`/vehicles/${id}`);
  return response.data.data;
}

export async function fetchDepartments(): Promise<DepartmentOption[]> {
  const response = await request.get<ApiEnvelope<DepartmentOption[]>>('/departments');
  return response.data.data;
}
