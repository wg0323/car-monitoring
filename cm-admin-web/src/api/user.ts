import request, { type ApiEnvelope, type PageResult } from './request';
// 部门下拉复用 P4-1 已有的 GET /api/v1/departments，不重复实现。
import type { DepartmentOption } from './vehicle';

export interface UserRow {
  id: string;
  username: string;
  displayName: string;
  enabled: boolean;
  phoneNo: string;
  createTime: string;
}

export interface UserRole {
  roleId: string;
  roleCode: string;
  roleName: string;
}

export interface UserDetail extends UserRow {
  userType: string;
  roles: UserRole[];
  departments: DepartmentOption[];
}

export interface UserQuery {
  page: number;
  size: number;
  keyword?: string;
  departmentId?: string;
}

export async function fetchUsers(query: UserQuery): Promise<PageResult<UserRow>> {
  const response = await request.get<ApiEnvelope<PageResult<UserRow>>>('/users', {
    params: {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      departmentId: query.departmentId || undefined,
    },
  });
  return response.data.data;
}

export async function fetchUserDetail(id: string): Promise<UserDetail> {
  const response = await request.get<ApiEnvelope<UserDetail>>(`/users/${id}`);
  return response.data.data;
}
