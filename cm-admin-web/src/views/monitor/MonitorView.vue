<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { Expand, Fold, Search } from '@element-plus/icons-vue';
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
const mapConfig = ref<MonitorMapConfig | null>(null);
const mapError = ref('');

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
}

function handleSelectionChange(selection: MonitorVehicleRow[]): void {
  const next = selection.map((row) => row.id);
  const added = next.filter((id) => !selectedIds.value.includes(id));
  const removed = selectedIds.value.filter((id) => !next.includes(id));
  selectedIds.value = next;
  if (added.length > 0) {
    subscribeVehicles(added);
  }
  if (removed.length > 0) {
    unsubscribeVehicles(removed);
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

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
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
          <el-table v-loading="loading" :data="rows" size="small" border row-key="id" @selection-change="handleSelectionChange">
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
