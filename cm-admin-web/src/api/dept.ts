import request, { type ApiEnvelope } from './request';

/** 车辆分组管理行，与后端 DepartmentDetailVO 同构。 */
export interface DepartmentDetail {
  id: string;
  parentId: string;
  parentName: string | null;
  name: string;
  departmentType: string | null;
  memberNo: string | null;
  roadPermitWord: string | null;
  roadPermitNo: string | null;
  businessScope: string | null;
  region: string | null;
  contactPerson: string | null;
  contactPhone: string | null;
  remark: string | null;
  createTime: string | null;
}

/** 新建与修改共用的请求体，字段对齐旧 editDep 表单。 */
export interface DepartmentSavePayload {
  name: string;
  parentId: string;
  departmentType?: string;
  memberNo?: string;
  roadPermitWord?: string;
  roadPermitNo?: string;
  businessScope?: string;
  region?: string;
  contactPerson?: string;
  contactPhone?: string;
  remark?: string;
}

export async function fetchDepartmentDetails(keyword?: string): Promise<DepartmentDetail[]> {
  const response = await request.get<ApiEnvelope<DepartmentDetail[]>>('/departments/manage', {
    params: { keyword: keyword || undefined },
  });
  return response.data.data;
}

export async function createDepartment(payload: DepartmentSavePayload): Promise<DepartmentDetail> {
  const response = await request.post<ApiEnvelope<DepartmentDetail>>('/departments', payload);
  return response.data.data;
}

export async function updateDepartment(id: string, payload: DepartmentSavePayload): Promise<DepartmentDetail> {
  const response = await request.put<ApiEnvelope<DepartmentDetail>>(`/departments/${id}`, payload);
  return response.data.data;
}

export async function deleteDepartment(id: string): Promise<void> {
  await request.delete<ApiEnvelope<null>>(`/departments/${id}`);
}

