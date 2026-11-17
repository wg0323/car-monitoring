<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  Bell,
  DataLine,
  Guide,
  MapLocation,
  Monitor,
  OfficeBuilding,
  Promotion,
  Search,
  Setting,
  SwitchButton,
  User,
  Van,
} from '@element-plus/icons-vue';
import type { AlarmNotifyRow } from '@/api/alarm';
import type { UrgeTodoRow } from '@/api/gov';
import { fetchMonitorSummary } from '@/api/monitor';
import { useAlarmNotify } from '@/composables/useAlarmNotify';
import { useUrgeTodoNotify } from '@/composables/useUrgeTodoNotify';
import AlarmDataBoard from '@/views/alarm/AlarmDataBoard.vue';
import AlarmNotifyWindow from '@/views/alarm/AlarmNotifyWindow.vue';
import UrgeTodoBoard from '@/views/gov/UrgeTodoBoard.vue';
import UrgeTodoDialog from '@/views/gov/UrgeTodoDialog.vue';
import { useAppStore } from '@/stores/app';

const MIN_WIDTH = 1280;

/** 状态栏轮询间隔，与服务端状态栏缓存（cm.monitor.summary-cache-seconds=10）对齐。 */
const STATUS_REFRESH_MS = 10000;

/** 底部页签按权限过滤：没有对应功能码的账号既看不到页签，也不会发起轮询。 */
const BOTTOM_TABS = [
  { label: '实时数据', name: 'realtime', permission: 'monitor:view' },
  { label: '报警数据', name: 'alarm', permission: 'alarm:view' },
  { label: '终端命令', name: 'command', permission: 'command:view' },
  { label: '报警督办', name: 'supervise', permission: 'command:view' },
  { label: '上级政府平台', name: 'gov', permission: 'command:view' },
];

/** 菜单图标由后端 icon 字段标识，前端只维护这张映射表。 */
const MENU_ICONS: Record<string, Component> = {
  organization: OfficeBuilding,
  vehicle: Van,
  monitor: Monitor,
  track: Guide,
  fence: MapLocation,
  alarm: Bell,
  command: Promotion,
  report: DataLine,
  system: Setting,
};

const appStore = useAppStore();
const route = useRoute();
const router = useRouter();
const narrowScreen = ref(false);
const keyword = ref('');
let statusTimer: number | null = null;

/**
 * 底部状态栏接 TC-UI-03：全局按当前账号数据范围取实时统计，数字随终端上下线变化。
 * 没有 monitor:view 的账号不轮询，避免每 10 秒产生一个注定 403 的请求。
 */
async function refreshStatusBar(): Promise<void> {
  if (!appStore.hasPermission('monitor:view')) {
    return;
  }
  try {
    const summary = await fetchMonitorSummary();
    appStore.statusBar = {
      total: summary.total,
      online: summary.online,
      offline: summary.offline,
      parked: summary.parking,
      onlineRate: `${summary.onlineRate}%`,
      version: appStore.statusBar.version,
    };
  } catch {
    // 状态栏失败不打断主流程；下一轮轮询会重试。
  }
}

const visibleBottomTabs = computed(() =>
  BOTTOM_TABS.filter((tab) => appStore.hasPermission(tab.permission)),
);

const { notices: alarmNotices, start: startAlarmNotify, dismiss: dismissAlarmNotices } = useAlarmNotify();

const urgeDialogVisible = ref(false);
const urgeDialogTodo = ref<UrgeTodoRow | null>(null);

/** 新督办到达时自动打开详情窗口，与旧系统「督办报文到达自动弹窗」一致。 */
const urgeNotify = useUrgeTodoNotify((row) => openUrgeTodo(row));

const menuItems = computed(() =>
  appStore.menus.map((item) => ({
    path: item.url,
    title: item.name,
    icon: MENU_ICONS[item.icon] ?? Monitor,
  })),
);

const displayName = computed(() => appStore.user?.displayName || appStore.user?.username || '未登录');

const currentTitle = computed(() => route.meta.title ?? '车辆监控平台');

function checkWidth(): void {
  narrowScreen.value = window.innerWidth < MIN_WIDTH;
}

/** 本轮只填了报警与督办两个底部页签，其余指向已有结果页。 */
function emptyTextOf(tabName: string): string {
  if (tabName === 'realtime') {
    return '实时定位见「实时监控」页';
  }
  if (tabName === 'command') {
    return '终端命令结果见「终端指令」页';
  }
  if (tabName === 'gov') {
    return '上级下发报文见「终端指令」页';
  }
  return '暂无数据';
}

function openUrgeTodo(row: UrgeTodoRow): void {
  urgeDialogTodo.value = row;
  urgeDialogVisible.value = true;
}

/**
 * 报警定位：报警行/弹窗把坐标交给实时监控页，由它在地图上打点并居中。
 * 没有坐标的报警（定位帧缺失）只提示，不跳转。
 */
function handleFocusAlarm(row: AlarmNotifyRow): void {
  if (row.latitude === null || row.longitude === null) {
    ElMessage.warning('该报警没有定位坐标');
    return;
  }
  if (!appStore.hasPermission('monitor:view')) {
    ElMessage.warning('当前账号无实时监控权限，无法在地图上定位');
    return;
  }
  appStore.requestFocusVehicle({
    vehicleId: row.vehicleId ?? '',
    plateNo: row.plateNo,
    latitude: row.latitude,
    longitude: row.longitude,
  });
  void router.push('/monitor');
}

async function handleLogout(): Promise<void> {
  await appStore.signOut();
  await router.push({ path: '/login' });
}

watch(
  visibleBottomTabs,
  (tabs) => {
    // 权限变更后当前页签可能已不可见，退回第一个可见页签。
    if (!tabs.some((tab) => tab.name === appStore.activeBottomTab) && tabs.length > 0) {
      appStore.activeBottomTab = tabs[0].name;
    }
  },
  { immediate: true },
);

onMounted(() => {
  checkWidth();
  window.addEventListener('resize', checkWidth);
  void refreshStatusBar();
  if (appStore.hasPermission('monitor:view')) {
    statusTimer = window.setInterval(() => void refreshStatusBar(), STATUS_REFRESH_MS);
  }
  if (appStore.hasPermission('alarm:view')) {
    startAlarmNotify();
  }
  if (appStore.hasPermission('command:view')) {
    urgeNotify.start();
  }
});

onUnmounted(() => {
  window.removeEventListener('resize', checkWidth);
  if (statusTimer !== null) {
    window.clearInterval(statusTimer);
    statusTimer = null;
  }
});
</script>

<template>
  <div class="cm-layout">
    <div v-if="narrowScreen" class="cm-narrow-tip" role="status">
      当前窗口宽度低于 1280px，调度台不支持小屏，请放大窗口后使用。
    </div>

    <header class="cm-header">
      <div class="cm-brand">车辆监控平台</div>
      <div class="cm-header-search">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="全局车辆搜索（P4 接入）" disabled />
      </div>
      <div class="cm-header-actions">
        <el-tag type="info" effect="plain">809 主链路：未接入</el-tag>
        <el-tag type="info" effect="plain">809 从链路：未接入</el-tag>
        <el-button v-permission="'alarm:view'" text :icon="Bell">报警</el-button>
        <span class="cm-user"><el-icon><User /></el-icon>{{ displayName }}</span>
        <el-button text :icon="SwitchButton" @click="handleLogout">退出</el-button>
      </div>
    </header>

    <div class="cm-body">
      <aside class="cm-sidebar" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
        <el-menu :default-active="route.path" :collapse="appStore.sidebarCollapsed" router>
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
        <el-button class="cm-sidebar-toggle" text @click="appStore.toggleSidebar()">
          {{ appStore.sidebarCollapsed ? '展开' : '收起' }}
        </el-button>
      </aside>

      <main class="cm-main">
        <div class="cm-page-title">{{ currentTitle }}</div>
        <div class="cm-page-content">
          <RouterView />
        </div>
        <section class="cm-bottom-panel" aria-label="底部实时数据面板">
          <el-tabs v-model="appStore.activeBottomTab">
            <el-tab-pane v-for="tab in visibleBottomTabs" :key="tab.name" :label="tab.label" :name="tab.name">
              <AlarmDataBoard v-if="tab.name === 'alarm'" @focus="handleFocusAlarm" />
              <UrgeTodoBoard v-else-if="tab.name === 'supervise'" @open="openUrgeTodo" />
              <el-table v-else :data="[]" :height="104" size="small" :empty-text="emptyTextOf(tab.name)" />
            </el-tab-pane>
          </el-tabs>
        </section>
      </main>
    </div>

    <AlarmNotifyWindow :notices="alarmNotices" @focus="handleFocusAlarm" @dismiss="dismissAlarmNotices" />
    <UrgeTodoDialog v-model="urgeDialogVisible" :todo="urgeDialogTodo" />

    <footer class="cm-status-bar" aria-label="全局状态栏">
      <span>总数 <b class="cm-mono">{{ appStore.statusBar.total }}</b></span>
      <span>上线 <b class="cm-mono">{{ appStore.statusBar.online }}</b></span>
      <span>离线 <b class="cm-mono">{{ appStore.statusBar.offline }}</b></span>
      <span>停车 <b class="cm-mono">{{ appStore.statusBar.parked }}</b></span>
      <span>上线率 <b class="cm-mono">{{ appStore.statusBar.onlineRate }}</b></span>
      <span>版本 <b class="cm-mono">{{ appStore.statusBar.version }}</b></span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.cm-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.cm-narrow-tip {
  padding: var(--cm-space-1) var(--cm-space-3);
  background: var(--cm-color-warning);
  color: var(--cm-color-card);
}

.cm-header {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  height: 48px;
  padding: 0 var(--cm-space-3);
  background: var(--cm-color-brand);
  color: var(--cm-color-card);
}

.cm-brand {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.cm-header-search {
  width: 280px;
}

.cm-header-actions {
  display: flex;
  align-items: center;
  gap: var(--cm-space-1);
  margin-left: auto;
}

.cm-user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cm-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.cm-sidebar {
  display: flex;
  flex-direction: column;
  width: 200px;
  border-right: 1px solid var(--cm-color-border);
  background: var(--cm-color-card);
  transition: width var(--cm-transition);

  &.is-collapsed {
    width: 64px;
  }

  .el-menu {
    flex: 1;
    border-right: 0;
  }
}

.cm-sidebar-toggle {
  height: var(--cm-control-height);
}

.cm-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.cm-page-title {
  height: 36px;
  padding: 0 var(--cm-space-3);
  border-bottom: 1px solid var(--cm-color-border);
  background: var(--cm-color-card);
  font-weight: 600;
  line-height: 36px;
}

.cm-page-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--cm-space-3);
}

.cm-bottom-panel {
  height: 190px;
  padding: 0 var(--cm-space-3);
  border-top: 1px solid var(--cm-color-border);
  background: var(--cm-color-card);
}

.cm-status-bar {
  display: flex;
  gap: var(--cm-space-4);
  height: 28px;
  padding: 0 var(--cm-space-3);
  border-top: 1px solid var(--cm-color-border);
  background: var(--cm-color-card);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
  line-height: 28px;
}
</style>
