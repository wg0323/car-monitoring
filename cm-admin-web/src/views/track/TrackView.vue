<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { Close, Download, Expand, Fold, Search, VideoPause, VideoPlay } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchVehicles, type VehicleRow } from '@/api/vehicle';
import { fetchMonitorMapConfig, type MonitorMapConfig } from '@/api/monitor';
import { buildTrackExportUrl, fetchTrack, type TrackPointRow } from '@/api/track';
import { todayEnd, todayStart } from '@/api/report';
import { loadAmap } from '@/map/amap';
import { createAmapTrackAdapter, type TrackMapAdapter, type TrackMapPoint } from '@/map/amapTrack';

const PLAY_SPEED_MAX = 110;

const PLAY_BASE_MS = 300;

const PLAY_MIN_INTERVAL_MS = 30;

const DEFAULT_PLAY_SPEED = 50;

const DAY_MILLIS = 86400000;

const VEHICLE_PAGE_SIZE = 20;

const VEHICLE_OPTION_LIMIT = 20;

const filters = reactive({
  vehicleId: '',
  startTime: todayStart(),
  endTime: todayEnd(),
  quickRange: 'today',
});
const vehicleOptions = ref<VehicleRow[]>([]);
const vehicleLoading = ref(false);
const rows = ref<TrackPointRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');
const hintText = ref('');

const mapCollapsed = ref(false);
const mapContainer = ref<HTMLElement | null>(null);
const mapConfig = ref<MonitorMapConfig | null>(null);
const mapError = ref('');

const playState = ref<'idle' | 'playing' | 'paused'>('idle');
const currentIndex = ref(-1);
const playSpeed = ref(DEFAULT_PLAY_SPEED);

let adapter: TrackMapAdapter | null = null;
let playTimer: number | null = null;

const pagedRows = computed(() => rows.value.slice((page.value - 1) * size.value, page.value * size.value));

const totalMileage = computed(() => {
  if (rows.value.length < 2) {
    return 0;
  }
  return rows.value[rows.value.length - 1].mileage - rows.value[0].mileage;
});

const mapHint = computed(() => {
  if (!mapConfig.value) {
    return '地图配置加载中…';
  }
  if (!mapConfig.value.configured) {
    return '未配置地图 key（服务端 CM_MAP_KEY），地图不可用，表格与导出不受影响';
  }
  if (mapError.value) {
    return mapError.value;
  }
  return '';
});

const playDisabled = computed(() => rows.value.length === 0 || playState.value === 'playing');

const pauseDisabled = computed(() => playState.value !== 'playing');

const stopDisabled = computed(() => playState.value === 'idle');

const exportDisabled = computed(() => rows.value.length === 0);

function toMapPoint(point: TrackPointRow): TrackMapPoint {
  return {
    latitude: point.latitude,
    longitude: point.longitude,
    label: point.deviceTimeText,
  };
}

function drawTrackOnMap(): void {
  adapter?.drawTrack(rows.value.map(toMapPoint));
}

async function searchVehicles(keyword: string): Promise<void> {
  vehicleLoading.value = true;
  try {
    const result = await fetchVehicles({
      page: 1,
      size: VEHICLE_PAGE_SIZE,
      plateNo: keyword.trim(),
    });
    vehicleOptions.value = result.records.slice(0, VEHICLE_OPTION_LIMIT);
  } catch {
    vehicleOptions.value = [];
  } finally {
    vehicleLoading.value = false;
  }
}

function vehicleLabel(row: VehicleRow): string {
  return row.simNo ? `${row.plateNo}（${row.simNo}）` : row.plateNo;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function formatDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function formatDateTime(date: Date): string {
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function applyQuickRange(value: string): void {
  const now = new Date();
  if (value === 'lastDay') {
    filters.startTime = formatDateTime(new Date(now.getTime() - DAY_MILLIS));
    filters.endTime = formatDateTime(now);
    return;
  }
  const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const offset = value === 'yesterday' ? -1 : value === 'beforeYesterday' ? -2 : 0;
  const day = new Date(startOfDay.getTime() + offset * DAY_MILLIS);
  filters.startTime = `${formatDate(day)} 00:00:00`;
  filters.endTime = `${formatDate(day)} 23:59:59`;
}

async function loadTrack(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  hintText.value = '';
  try {
    const result = await fetchTrack({
      vehicleId: filters.vehicleId,
      startTime: filters.startTime,
      endTime: filters.endTime,
    });
    rows.value = result.points;
    total.value = result.total;
    if (result.total === 0) {
      hintText.value = '该时间段没有历史轨迹数据';
    }
    drawTrackOnMap();
    adapter?.fitTrack();
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '轨迹查询失败';
    rows.value = [];
    total.value = 0;
    adapter?.drawTrack([]);
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  if (!filters.vehicleId) {
    errorText.value = '请先选择车辆';
    return;
  }
  stopPlay();
  page.value = 1;
  void loadTrack();
}

function handleReset(): void {
  stopPlay();
  filters.vehicleId = '';
  filters.quickRange = 'today';
  applyQuickRange('today');
  rows.value = [];
  total.value = 0;
  page.value = 1;
  errorText.value = '';
  hintText.value = '';
  adapter?.drawTrack([]);
}

function handlePageChange(next: number): void {
  page.value = next;
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
}

function handleExport(): void {
  if (exportDisabled.value || !filters.vehicleId) {
    return;
  }
  window.location.href = buildTrackExportUrl({
    vehicleId: filters.vehicleId,
    startTime: filters.startTime,
    endTime: filters.endTime,
  });
}

function playIntervalMs(): number {
  return Math.max(PLAY_MIN_INTERVAL_MS,
    Math.round(PLAY_BASE_MS * (PLAY_SPEED_MAX - playSpeed.value) / 100));
}

function clearTimer(): void {
  if (playTimer !== null) {
    window.clearInterval(playTimer);
    playTimer = null;
  }
}

function schedule(): void {
  clearTimer();
  playTimer = window.setInterval(tick, playIntervalMs());
}

function tick(): void {
  if (currentIndex.value >= rows.value.length) {
    stopPlay();
    return;
  }
  const point = rows.value[currentIndex.value];
  const targetPage = Math.floor(currentIndex.value / size.value) + 1;
  if (targetPage !== page.value) {
    page.value = targetPage;
  }
  adapter?.moveMarker(toMapPoint(point));
  currentIndex.value += 1;
}

function startPlay(): void {
  if (rows.value.length === 0 || playState.value === 'playing') {
    return;
  }
  if (playState.value === 'idle') {
    currentIndex.value = 0;
  }
  playState.value = 'playing';
  schedule();
}

function pausePlay(): void {
  if (playState.value !== 'playing') {
    return;
  }
  clearTimer();
  playState.value = 'paused';
}

function stopPlay(): void {
  clearTimer();
  playState.value = 'idle';
  currentIndex.value = -1;
}

watch(playSpeed, () => {
  if (playState.value === 'playing') {
    schedule();
  }
});

function rowClassName({ rowIndex }: { rowIndex: number }): string {
  const globalIndex = (page.value - 1) * size.value + rowIndex;
  return globalIndex === currentIndex.value ? 'cm-track-row--current' : '';
}

function rowNumber(index: number): number {
  return (page.value - 1) * size.value + index + 1;
}

function formatCoordinate(latitude: number, longitude: number): string {
  return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
}

function formatOneDecimal(value: number): string {
  return value.toFixed(1);
}

function toggleMap(): void {
  mapCollapsed.value = !mapCollapsed.value;
  void nextTick(() => {
    adapter?.resize();
    if (!mapCollapsed.value) {
      adapter?.fitTrack();
    }
  });
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
  await nextTick();
  try {
    const amap = await loadAmap(mapConfig.value.key, mapConfig.value.securityCode);
    if (!mapContainer.value) {
      return;
    }
    adapter = createAmapTrackAdapter(mapContainer.value, {
      latitude: mapConfig.value.centerLat,
      longitude: mapConfig.value.centerLng,
      zoom: mapConfig.value.centerZoom,
    }, amap);
    adapter.resize();
    if (rows.value.length > 0) {
      drawTrackOnMap();
    }
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : '地图初始化失败';
  }
}

onMounted(async () => {
  await searchVehicles('');
  await initMap();
});

onBeforeUnmount(() => {
  clearTimer();
  adapter?.destroy();
  adapter = null;
});
</script>

<template>
  <div class="cm-track">
    <el-card shadow="never">
      <div class="cm-track-toolbar">
        <el-select
          v-model="filters.vehicleId"
          filterable
          remote
          :remote-method="searchVehicles"
          :loading="vehicleLoading"
          placeholder="车牌号前缀"
          class="cm-track-vehicle"
        >
          <el-option v-for="item in vehicleOptions" :key="item.id" :label="vehicleLabel(item)" :value="item.id" />
        </el-select>
        <el-date-picker
          v-model="filters.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="开始时间"
          class="cm-track-time"
        />
        <span class="cm-track-sep">至</span>
        <el-date-picker
          v-model="filters.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="结束时间"
          class="cm-track-time"
        />
        <el-select v-model="filters.quickRange" class="cm-track-quick" @change="applyQuickRange">
          <el-option label="当天" value="today" />
          <el-option label="昨天" value="yesterday" />
          <el-option label="前天" value="beforeYesterday" />
          <el-option label="最近一天" value="lastDay" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button :icon="Download" :disabled="exportDisabled" @click="handleExport">导出</el-button>
        <el-button :icon="mapCollapsed ? Expand : Fold" text @click="toggleMap">
          {{ mapCollapsed ? '展开地图' : '收起地图' }}
        </el-button>
      </div>

      <div class="cm-track-toolbar cm-track-playbar">
        <el-button :icon="VideoPlay" :disabled="playDisabled" @click="startPlay">播放</el-button>
        <el-button :icon="VideoPause" :disabled="pauseDisabled" @click="pausePlay">暂停</el-button>
        <el-button :icon="Close" :disabled="stopDisabled" @click="stopPlay">停止</el-button>
        <span class="cm-track-speed-label">播放速度</span>
        <el-select v-model="playSpeed" class="cm-track-speed">
          <el-option label="慢" :value="20" />
          <el-option label="中" :value="50" />
          <el-option label="快" :value="80" />
          <el-option label="极快" :value="100" />
        </el-select>
        <span class="cm-track-stat">总里程 <b class="cm-mono">{{ formatOneDecimal(totalMileage) }}</b> 公里</span>
        <span class="cm-track-stat">共 <b class="cm-mono">{{ total }}</b> 点</span>
      </div>

      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
      <el-alert v-if="hintText" :title="hintText" type="warning" :closable="false" show-icon />

      <div class="cm-track-body" :class="{ 'cm-track-body--collapsed': mapCollapsed }">
        <div class="cm-track-table">
          <el-table
            v-loading="loading"
            :data="pagedRows"
            size="small"
            border
            :row-class-name="rowClassName"
          >
            <el-table-column label="序号" width="60">
              <template #default="{ $index }">
                <span class="cm-mono">{{ rowNumber($index) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="plateNo" label="车牌号" width="110">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.plateNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="deviceTimeText" label="GPS时间" width="160">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.deviceTimeText }}</span>
              </template>
            </el-table-column>
            <el-table-column label="经纬度" width="190">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatCoordinate(row.latitude, row.longitude) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="速度(km/h)" width="100">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatOneDecimal(row.speedKph) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="记录仪速度" width="100">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatOneDecimal(row.recorderSpeedKph) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="里程(km)" width="90">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatOneDecimal(row.mileage) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="油量(L)" width="80">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatOneDecimal(row.oil) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="directionText" label="方向" width="110" />
            <el-table-column label="海拔(米)" width="85">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.altitude }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="statusText" label="状态" width="150" />
            <el-table-column prop="alarmFlagText" label="报警标志" width="100" />
            <el-table-column prop="validText" label="有效性" width="80" />
          </el-table>

          <el-pagination
            class="cm-track-pagination"
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="page"
            :page-size="size"
            :page-sizes="[20, 50, 100]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>

        <div v-show="!mapCollapsed" class="cm-track-map">
          <el-alert v-if="mapHint" :title="mapHint" :type="mapConfig?.configured ? 'error' : 'warning'"
            :closable="false" show-icon />
          <div ref="mapContainer" v-show="mapConfig?.configured" class="cm-track-map-canvas" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.cm-track-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-track-vehicle {
  width: 240px;
}

.cm-track-time {
  width: 190px;
}

.cm-track-quick {
  width: 110px;
}

.cm-track-sep {
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-track-speed-label {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-track-speed {
  width: 110px;
}

.cm-track-stat {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-track-stat + .cm-track-stat {
  margin-left: var(--cm-space-3);
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-track-body {
  display: flex;
  gap: var(--cm-space-3);
  align-items: stretch;
}

.cm-track-body--collapsed .cm-track-table {
  flex: 1;
}

.cm-track-table {
  display: flex;
  flex-direction: column;
  flex: 3;
  min-width: 0;
}

.cm-track-map {
  display: flex;
  flex-direction: column;
  flex: 2;
  gap: var(--cm-space-1);
  min-width: 0;
}

.cm-track-map-canvas {
  height: 560px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
}

.cm-track-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}

:deep(.cm-track-row--current) {
  background: var(--cm-color-brand-secondary);
}
</style>

