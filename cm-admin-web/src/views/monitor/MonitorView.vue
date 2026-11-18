<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { Expand, Fold, Search } from '@element-plus/icons-vue';
import type { TableInstance } from 'element-plus';
import { ApiError } from '@/api/request';
import { fetchDepartments, type DepartmentOption } from '@/api/vehicle';
import {
  fetchMonitorMapConfig,
  fetchMonitorSummary,
  fetchMonitorVehicles,
  type MonitorMapConfig,
  type MonitorSummary,
  type MonitorVehicleRow,
} from '@/api/monitor';
import { useMonitorSocket, type PositionFrame } from '@/composables/useMonitorSocket';
import { createAmapAdapter, loadAmap } from '@/map/amap';
import type { MapAdapter, MapVehicle } from '@/map/types';
import { useAppStore } from '@/stores/app';

const SUMMARY_REFRESH_MS = 30000;

const appStore = useAppStore();

const filters = reactive({ keyword: '', departmentId: '', online: '' as '' | 'true' | 'false' });
const rows = ref<MonitorVehicleRow[]>([]);
const departments = ref<DepartmentOption[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');
const departmentError = ref('');

const summary = ref<MonitorSummary | null>(null);
const summaryError = ref('');

const selectedIds = ref<string[]>([]);
const mapCollapsed = ref(false);
const mapContainer = ref<HTMLElement | null>(null);
const tableRef = ref<TableInstance>();
const mapConfig = ref<MonitorMapConfig | null>(null);
const mapError = ref('');
/** 跟随车辆：只跟第一辆勾选的车，多选时不会每来一帧就跳一次。 */
const following = ref(false);

let adapter: MapAdapter | null = null;
let summaryTimer: number | null = null;

const {
  connected: socketConnected,
  errorText: socketError,
  connect: connectSocket,
  disconnect: disconnectSocket,
  subscribe: subscribeVehicles,
  unsubscribe: unsubscribeVehicles,
  reset: resetSubscriptions,
} = useMonitorSocket(applyPositionFrame);

const mapHint = computed(() => {
  if (!mapConfig.value) {
    return '地图配置加载中…';
  }
  if (!mapConfig.value.configured) {
    return '未配置地图 key（服务端 CM_MAP_KEY），地图不可用，表格与推送不受影响';
  }
  if (mapError.value) {
    return mapError.value;
  }
  return '';
});

function vehicleWithPosition(row: MonitorVehicleRow): boolean {
  return row.latitude !== null && row.longitude !== null;
}

/** 表格里的车标来源：只画有定位的行，无定位的车不参与地图与视野自适应。 */
function mapVehicles(): MapVehicle[] {
  return rows.value
    .filter(vehicleWithPosition)
    .map((row) => ({
      id: row.id,
      plateNo: row.plateNo,
      online: row.online,
      latitude: row.latitude as number,
      longitude: row.longitude as number,
      course: row.course ?? 0,
    }));
}

function refreshMap(fit = false): void {
  if (!adapter) {
    return;
  }
  adapter.drawVehicles(mapVehicles(), { fit });
}

async function loadDepartments(): Promise<void> {
  departments.value = await fetchDepartments();
}

async function loadVehicles(fit = true): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchMonitorVehicles({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim(),
      departmentId: filters.departmentId,
      online: filters.online,
    });
    rows.value = result.records;
    total.value = result.total;
    // 先重画再自适应：翻页后旧车标必须先被清掉，而 fit 只能在 marker 落地后生效。
    refreshMap(fit);
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '实时列表加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/**
 * 消费报警弹窗交给地图的定位请求：先按车牌把该车拉到列表，再把视野定到报警点。
 * 车辆已删除或不在数据范围时不阻断定位，报警点仍然落在地图上。
 */
async function consumeFocusVehicle(): Promise<void> {
  const target = appStore.focusVehicle;
  if (!target) {
    return;
  }
  appStore.clearFocusVehicle();
  filters.keyword = target.plateNo;
  page.value = 1;
  selectedIds.value = [];
  resetSubscriptions();
  // 这里不叠自适应视野：异步的 fitView 会把后面的报警定位覆盖掉。
  await loadVehicles(false);
  if (mapCollapsed.value) {
    mapCollapsed.value = false;
    await nextTick();
    adapter?.resize();
  }
  adapter?.focusPoint(target.latitude, target.longitude, 15);
}

async function loadSummary(): Promise<void> {
  try {
    summary.value = await fetchMonitorSummary();
    summaryError.value = '';
  } catch (error) {
    summaryError.value = error instanceof ApiError ? error.message : '状态栏加载失败';
  }
}

function applyPositionFrame(frame: PositionFrame): void {
  const row = rows.value.find((item) => item.id === frame.vehicleId);
  if (!row) {
    return;
  }
  row.latitude = frame.latitude;
  row.longitude = frame.longitude;
  row.speedKph = frame.speedKph;
  row.course = frame.course;
  row.deviceTime = frame.deviceTime;
  row.online = frame.online;
  refreshMap();
  if (following.value && selectedIds.value[0] === frame.vehicleId) {
    adapter?.centerOnVehicle(frame.vehicleId);
  }
}

function handleSelectionChange(selection: MonitorVehicleRow[]): void {
  const next = selection.map((row) => row.id);
  const added = next.filter((id) => !selectedIds.value.includes(id));
  const removed = selectedIds.value.filter((id) => !next.includes(id));
  selectedIds.value = next;
  if (added.length > 0) {
    subscribeVehicles(added);
    // 勾选即跟随：勾上就是为了盯住这辆车，省得再点一次开关；要看全图时手动停止即可。
    following.value = true;
    adapter?.centerOnVehicle(next[0]);
  }
  if (removed.length > 0) {
    unsubscribeVehicles(removed);
  }
  if (selectedIds.value.length === 0) {
    following.value = false;
  }
}

/** 点复选框以外的任意单元格都切换勾选：勾选是订阅推送的唯一入口，少一次瞄准。 */
function handleRowClick(row: MonitorVehicleRow, _column: unknown, event: Event): void {
  if ((event.target as HTMLElement | null)?.closest('.el-checkbox')) {
    return;
  }
  tableRef.value?.toggleRowSelection(row);
}

/** 手动切换跟随；开启时先居中一次，不用等下一帧。 */
function toggleFollow(): void {
  if (selectedIds.value.length === 0) {
    return;
  }
  following.value = !following.value;
  if (following.value) {
    adapter?.centerOnVehicle(selectedIds.value[0]);
  }
}

function handleSearch(): void {
  page.value = 1;
  selectedIds.value = [];
  resetSubscriptions();
  void loadVehicles();
}

function handleReset(): void {
  filters.keyword = '';
  filters.departmentId = '';
  filters.online = '';
  page.value = 1;
  selectedIds.value = [];
  resetSubscriptions();
  void loadVehicles();
}

function handlePageChange(next: number): void {
  page.value = next;
  // 翻页后旧订阅指向的车已不在表里，先退订再拉新页，避免白收帧。
  selectedIds.value = [];
  resetSubscriptions();
  void loadVehicles();
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
  selectedIds.value = [];
  resetSubscriptions();
  void loadVehicles();
}

function toggleMap(): void {
  mapCollapsed.value = !mapCollapsed.value;
  // 容器尺寸变了要重新量；展开后顺便回到车标视野，否则会停在收起前的位置。
  void nextTick(() => {
    adapter?.resize();
    if (!mapCollapsed.value) {
      adapter?.fitVehicles(mapVehicles());
    }
  });
}

/**
 * 定位时间来自 808 上报，跨进程统一存 ISO-8601：带时区标识（Z 或 +08:00）的是 UTC，
 * 必须转成本地时区再显示，否则 18:12 的点会显示成 10:12，看着像八小时没刷新。
 * 不带时区的是后端已格式化的本地时间，直接截断，避免二次偏移。
 */
function formatTime(value: string | null): string {
  if (!value) {
    return '—';
  }
  if (!/(Z|[+-]\d{2}:?\d{2})$/.test(value)) {
    return value.replace('T', ' ').slice(0, 19);
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 19);
  }
  const pad = (part: number): string => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function formatSpeed(value: number | null): string {
  return value === null ? '—' : value.toFixed(1);
}

function formatCoordinate(latitude: number | null, longitude: number | null): string {
  if (latitude === null || longitude === null) {
    return '—';
  }
  return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
}

async function initMap(): Promise<void> {
  try {
    mapConfig.value = await fetchMonitorMapConfig();
  } catch (error) {
    mapError.value = error instanceof ApiError ? error.message : '地图配置加载失败';
    return;
  }
  if (!mapConfig.value.configured || !mapContainer.value) {
    return;
  }
  // 等容器从 v-show 里显示出来再建实例：在 display:none 的容器上建图宽高为 0，首屏会空白。
  await nextTick();
  try {
    const amap = await loadAmap(mapConfig.value.key, mapConfig.value.securityCode);
    if (!mapContainer.value) {
      return;
    }
    adapter = createAmapAdapter(mapContainer.value, {
      latitude: mapConfig.value.centerLat,
      longitude: mapConfig.value.centerLng,
      zoom: mapConfig.value.centerZoom,
    }, amap);
    adapter.resize();
    refreshMap(true);
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : '地图初始化失败';
  }
}

watch(mapCollapsed, () => {
  void nextTick(() => adapter?.resize());
});

watch(
  () => appStore.focusVehicle,
  (target) => {
    if (target) {
      void consumeFocusVehicle();
    }
  },
);

onMounted(async () => {
  try {
    await loadDepartments();
  } catch (error) {
    departmentError.value = error instanceof ApiError ? error.message : '部门下拉加载失败';
  }
  await loadVehicles();
  await loadSummary();
  summaryTimer = window.setInterval(() => void loadSummary(), SUMMARY_REFRESH_MS);
  connectSocket();
  await initMap();
  // 从报警弹窗跳进来时，地图建好后再定位。
  await consumeFocusVehicle();
});

onBeforeUnmount(() => {
  if (summaryTimer !== null) {
    window.clearInterval(summaryTimer);
    summaryTimer = null;
  }
  adapter?.destroy();
  adapter = null;
  disconnectSocket();
});
</script>

<template>
  <div class="cm-monitor">
    <el-card shadow="never">
      <div class="cm-monitor-toolbar">
        <el-input v-model="filters.keyword" placeholder="车牌号前缀" clearable class="cm-monitor-field" />
        <el-select v-model="filters.departmentId" placeholder="全部部门（数据范围内）" clearable class="cm-monitor-field">
          <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="filters.online" placeholder="全部车辆" class="cm-monitor-field">
          <el-option label="全部车辆" value="" />
          <el-option label="在线车辆" value="true" />
          <el-option label="离线车辆" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>

        <div class="cm-monitor-status">
          <el-tag :type="socketConnected ? 'success' : 'info'" effect="plain" size="small">
            {{ socketConnected ? '推送已连接' : '推送未连接' }}
          </el-tag>
          <template v-if="summary">
            <span>总数 <b class="cm-mono">{{ summary.total }}</b></span>
            <span>上线 <b class="cm-mono">{{ summary.online }}</b></span>
            <span>离线 <b class="cm-mono">{{ summary.offline }}</b></span>
            <span>停车 <b class="cm-mono">{{ summary.parking }}</b></span>
            <span>上线率 <b class="cm-mono">{{ summary.onlineRate }}%</b></span>
          </template>
        </div>

        <el-button size="small" :type="following ? 'primary' : 'default'"
          :disabled="selectedIds.length === 0" @click="toggleFollow">
          {{ following ? '停止跟随' : '跟随车辆' }}
        </el-button>

        <el-button :icon="mapCollapsed ? Expand : Fold" text @click="toggleMap">
          {{ mapCollapsed ? '展开地图' : '收起地图' }}
        </el-button>
      </div>

      <el-alert v-if="departmentError" :title="departmentError" type="warning" :closable="false" show-icon />
      <el-alert v-if="summaryError" :title="summaryError" type="warning" :closable="false" show-icon />
      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
      <el-alert v-if="socketError" :title="socketError" type="warning" :closable="false" show-icon />

      <div class="cm-monitor-body" :class="{ 'cm-monitor-body--collapsed': mapCollapsed }">
        <div class="cm-monitor-table">
          <el-table ref="tableRef" v-loading="loading" :data="rows" size="small" border row-key="id"
            @selection-change="handleSelectionChange" @row-click="handleRowClick">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="plateNo" label="车牌号" width="120">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.plateNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="simNo" label="SIM" width="130">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.simNo }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.online ? 'success' : 'info'" effect="plain">
                  {{ row.online ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="经纬度" width="190">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatCoordinate(row.latitude, row.longitude) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="速度" width="90">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatSpeed(row.speedKph) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="departmentName" label="部门" min-width="120" />
            <el-table-column label="定位时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.deviceTime) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination class="cm-monitor-pagination" layout="total, sizes, prev, pager, next" :total="total"
            :current-page="page" :page-size="size" :page-sizes="[20, 50, 100]" @current-change="handlePageChange"
            @size-change="handleSizeChange" />
        </div>

        <div v-show="!mapCollapsed" class="cm-monitor-map">
          <el-alert v-if="mapHint" :title="mapHint" :type="mapConfig?.configured ? 'error' : 'warning'"
            :closable="false" show-icon />
          <div ref="mapContainer" v-show="mapConfig?.configured" class="cm-monitor-map-canvas" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.cm-monitor-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-monitor-field {
  width: 200px;
}

.cm-monitor-status {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-monitor-body {
  display: flex;
  gap: var(--cm-space-3);
  align-items: stretch;
}

.cm-monitor-body--collapsed .cm-monitor-table {
  flex: 1;
}

.cm-monitor-table {
  display: flex;
  flex-direction: column;
  flex: 3;
  min-width: 0;

  :deep(.el-table__row) {
    cursor: pointer;
  }
}

.cm-monitor-map {
  display: flex;
  flex-direction: column;
  flex: 2;
  gap: var(--cm-space-1);
  min-width: 0;
}

.cm-monitor-map-canvas {
  height: 560px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
}

.cm-monitor-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}
</style>
