import request, { type ApiEnvelope } from './request';

/** 在线用户行，与后端 MessageOnlineUserVO 同构；用户 ID 是 19 位雪花，一律走字符串。 */
export interface OnlineUser {
  userId: string;
  displayName: string;
}

export async function fetchOnlineUsers(): Promise<OnlineUser[]> {
  const response = await request.get<ApiEnvelope<OnlineUser[]>>('/messages/online-users');
  return response.data.data;
}

/** 系统广播：全平台在线账号立即收到，不落库、不补发。 */
export async function publishBroadcast(text: string): Promise<void> {
  await request.post<ApiEnvelope<null>>('/messages/broadcast', { text });
}
