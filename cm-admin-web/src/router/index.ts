import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import BasicLayout from '@/layout/BasicLayout.vue';
import type { MenuItem } from '@/api/auth';
import { useAppStore } from '@/stores/app';

export const ROOT_ROUTE_NAME = 'cm-root';

declare module 'vue-router' {
  interface RouteMeta {
    title?: string;
    domain?: string;
    permission?: string;
    icon?: string;
    public?: boolean;
  }
}

export const domainRoutes: RouteRecordRaw[] = [
  {
    // 组织权限的首页是用户列表，路径与后端菜单 url（/organization/user）保持一致；
    // 车辆分组（TC-ADM-05）作为同域第二个页签。
    path: 'organization/user',
    name: 'organization-user',
    component: () => import('@/views/organization/OrganizationView.vue'),
    meta: { title: '组织权限', domain: '组织权限', permission: 'organization:view' },
  },
  {
    path: 'vehicle',
    name: 'vehicle',
    component: () => import('@/views/vehicle/VehicleListView.vue'),
    meta: { title: '车辆与终端', domain: '车辆与终端', permission: 'vehicle:view' },
  },
  {
    path: 'monitor',
    name: 'monitor',
    component: () => import('@/views/monitor/MonitorView.vue'),
    meta: { title: '实时监控', domain: '实时监控', permission: 'monitor:view' },
  },
  {
    path: 'track',
    name: 'track',
    component: () => import('@/views/track/TrackView.vue'),
    meta: { title: '历史轨迹', domain: '历史轨迹', permission: 'track:view' },
  },
  {
    path: 'fence',
    name: 'fence',
    component: () => import('@/views/fence/FenceView.vue'),
    meta: { title: '围栏与线路', domain: '围栏与线路', permission: 'fence:view' },
  },
  {
    path: 'alarm',
    name: 'alarm',
    component: () => import('@/views/alarm/AlarmProcessView.vue'),
    meta: { title: '报警处理', domain: '报警处理', permission: 'alarm:view' },
  },
  {
    // 消息中心：菜单 url 与后端 cm_function 的 /message 一致，TC-EXT-11 的聊天与系统广播入口。
    path: 'message',
    name: 'message',
    component: () => import('@/views/message/MessageView.vue'),
    meta: { title: '消息中心', domain: '消息中心', permission: 'message:view' },
  },
  {
    path: 'command',
    name: 'command',
    component: () => import('@/views/command/CommandView.vue'),
    meta: { title: '终端指令', domain: '终端指令', permission: 'command:view' },
  },
  {
    path: 'report',
    name: 'report',
    component: () => import('@/views/report/ReportView.vue'),
    meta: { title: '报表与导出', domain: '报表与导出', permission: 'report:view' },
  },
  {
    path: 'system',
    name: 'system',
    component: () => import('@/views/PlaceholderView.vue'),
    meta: { title: '系统配置', domain: '系统配置', permission: 'system:view' },
  },
];

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无访问权限', public: true },
  },
  {
    path: '/',
    name: ROOT_ROUTE_NAME,
    component: BasicLayout,
    // 不在路由级做 redirect：重定向在守卫之前解析，此时动态路由尚未注册，
    // 会与兜底路由形成「/ → 未注册菜单 → / → …」的无限循环。落点改由守卫决定。
    children: [],
  },
  {
    // 未匹配路径的兜底。这里同样不写 redirect：redirect 在守卫之前解析，
    // 动态路由尚未注册时会把 /monitor、/vehicle 这类深链（含 F5）直接吞成 /。
    // 落点由守卫在 routesReady 之后决定，所以这个组件实际不会渲染。
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/ForbiddenView.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
});

/**
 * 后端下发菜单 → 本地组件表匹配 → 逐条 addRoute。
 * 只注册当前用户有权限的域，越权入口既不可见也不可达。
 */
export function registerDynamicRoutes(menus: MenuItem[]): void {
  for (const menu of menus) {
    const source = domainRoutes.find((route) => `/${route.path}` === menu.url);
    if (!source || (source.name !== undefined && router.hasRoute(source.name))) {
      continue;
    }
    router.addRoute(ROOT_ROUTE_NAME, {
      ...source,
      meta: {
        ...source.meta,
        title: menu.name,
        icon: menu.icon,
        permission: menu.permissionCode,
      },
    } as RouteRecordRaw);
  }
}

router.beforeEach(async (to) => {
  const appStore = useAppStore();
  if (to.meta.public) {
    // 已登录还去登录页时直接回首页，避免重复登录。
    if (to.path === '/login' && appStore.loaded) {
      return { path: '/' };
    }
    return true;
  }
  if (!appStore.loaded) {
    try {
      await appStore.loadSession();
    } catch {
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }
  // 登录成功与刷新恢复都要在这里补注册：登录接口不经过守卫，漏掉会出现「登录后白屏、刷新即正常」。
  if (!appStore.routesReady) {
    registerDynamicRoutes(appStore.menus);
    appStore.markRoutesReady();
    return { path: to.fullPath, replace: true };
  }
  // 首页没有自己的页面，按第一个可见菜单落地；无任何菜单时去 403。
  if (to.path === '/') {
    return appStore.menus.length > 0
      ? { path: appStore.menus[0].url, replace: true }
      : '/403';
  }
  // 动态路由还没注册时，深链会先落到兜底路由；未注册完的路径由下一轮重新解析。
  if (to.name === 'not-found') {
    return appStore.menus.length > 0
      ? { path: appStore.menus[0].url, replace: true }
      : '/403';
  }
  const permission = to.meta.permission;
  if (permission && !appStore.hasPermission(permission)) {
    return '/403';
  }
  return true;
});

export default router;
