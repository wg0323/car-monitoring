import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  fetchSession,
  login as loginRequest,
  logout as logoutRequest,
  type LoginRequest,
  type LoginUser,
  type MenuItem,
} from '@/api/auth';

const WILDCARD_PERMISSION = '*';

export interface StatusBarState {
  total: number;
  online: number;
  offline: number;
  parked: number;
  onlineRate: string;
  version: string;
}

/** 报警定位目标：底部报警弹窗跳转到实时监控页时交给地图的坐标（WGS-84）。 */
export interface FocusVehicle {
  vehicleId: string;
  plateNo: string;
  latitude: number;
  longitude: number;
}

/**
 * 会话、权限码与菜单。权限码与菜单都来自后端 /auth/me：
 * 菜单已在服务端按权限过滤，前端不再二次判断可见性；按钮仍用 hasPermission。
 */
export const useAppStore = defineStore('app', () => {
  const permissions = ref<string[]>([]);
  const user = ref<LoginUser | null>(null);
  const menus = ref<MenuItem[]>([]);
  const loaded = ref(false);
  /** 动态路由是否已按当前菜单注册。退出登录时必须重置，否则换账号后会缺少新账号的菜单路由。 */
  const routesReady = ref(false);
  const sidebarCollapsed = ref(false);
  const activeBottomTab = ref('realtime');
  const focusVehicle = ref<FocusVehicle | null>(null);
  const statusBar = ref<StatusBarState>({
    total: 0,
    online: 0,
    offline: 0,
    parked: 0,
    onlineRate: '0%',
    version: 'P5',
  });

  function setPermissions(codes: string[]): void {
    permissions.value = [...codes];
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(WILDCARD_PERMISSION) || permissions.value.includes(code);
  }

  function applySession(session: { user: LoginUser; permissions: string[]; menus: MenuItem[] }): void {
    user.value = session.user;
    permissions.value = [...session.permissions];
    menus.value = [...session.menus].sort((left, right) => left.menuSort - right.menuSort);
    loaded.value = true;
  }

  function clearSession(): void {
    user.value = null;
    permissions.value = [];
    menus.value = [];
    loaded.value = false;
    routesReady.value = false;
  }

  function markRoutesReady(): void {
    routesReady.value = true;
  }

  async function loadSession(): Promise<void> {
    const session = await fetchSession();
    applySession(session);
  }

  async function signIn(payload: LoginRequest): Promise<void> {
    const session = await loginRequest(payload);
    applySession(session);
  }

  async function signOut(): Promise<void> {
    try {
      await logoutRequest();
    } finally {
      clearSession();
    }
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  }

  /** 报警定位：先写目标再跳路由，监控页挂载后消费一次并清掉。 */
  function requestFocusVehicle(target: FocusVehicle): void {
    focusVehicle.value = { ...target };
  }

  function clearFocusVehicle(): void {
    focusVehicle.value = null;
  }

  return {
    permissions,
    user,
    menus,
    loaded,
    routesReady,
    sidebarCollapsed,
    activeBottomTab,
    statusBar,
    focusVehicle,
    setPermissions,
    hasPermission,
    applySession,
    clearSession,
    loadSession,
    signIn,
    signOut,
    markRoutesReady,
    toggleSidebar,
    requestFocusVehicle,
    clearFocusVehicle,
  };
});
