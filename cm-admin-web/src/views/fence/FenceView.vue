<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import {
  Aim,
  Crop,
  Delete,
  Edit,
  Location,
  Odometer,
  Plus,
  Pointer,
  Refresh,
  RefreshLeft,
  Search,
  Share,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ApiError } from '@/api/request';
import { fetchDepartments, type DepartmentOption } from '@/api/vehicle';
import { fetchMapConfig, saveMapPreference, type MapConfig } from '@/api/map';
import {
  createFence,
  deleteFence,
  fetchFences,
  updateFence,
  type FenceAreaType,
  type FenceRow,
  type FenceSavePayload,
} from '@/api/fence';
import { loadAmap } from '@/map/amap';
import { createFenceMapAdapter, type FenceDraft, type FenceMapAdapter, type FenceShape } from '@/map/amapFence';

const AREA_TYPE_TEXT: Record<FenceAreaType, string> = {
  circle: '圆形',
  rect: '矩形',
  polygon: '多边形',
};

const DEFAULT_MAX_SPEED = 80;

const DEFAULT_RADIUS = 500;

type ToolName = 'drag' | 'circle' | 'rect' | 'polygon' | 'measure';

interface FenceForm {
  areaType: FenceAreaType;
  points: string;
  radius: number;
  centerLat: number;
  centerLng: number;
  pointCount: number;
  name: string;
  departmentId: string;
  alarmEnterDriver: boolean;
  alarmEnterPlatform: boolean;
  alarmExitDriver: boolean;
  alarmExitPlatform: boolean;
  byTime: boolean;
  startDate: string;
  endDate: string;
  limitSpeed: boolean;
  maxSpeed: number;
  delaySeconds: number;
  remark: string;
}

const filters = reactive({ name: '', departmentId: '' });
const rows = ref<FenceRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');
const departmentError = ref('');
const departments = ref<DepartmentOption[]>([]);

const mapContainer = ref<HTMLElement | null>(null);
const mapConfig = ref<MapConfig | null>(null);
const mapError = ref('');
const selectedId = ref<string | null>(null);
const activeTool = ref<ToolName>('drag');

const drawerVisible = ref(false);
const saving = ref(false);
const formError = ref('');
const editingId = ref<string | null>(null);
const form = reactive<FenceForm>(emptyForm());

let adapter: FenceMapAdapter | null = null;

const mapHint = computed(() => {
  if (!mapConfig.value) {
    return '地图配置加载中…';
  }
  if (!mapConfig.value.configured) {
    return '未配置地图 key（服务端 CM_MAP_KEY），地图不可用，区域列表仍可维护';
  }
  if (mapError.value) {
    return mapError.value;
  }
  return '';
});

const drawerTitle = computed(() => (editingId.value ? '编辑区域' : '新建区域'));

function emptyForm(): FenceForm {
  return {
    areaType: 'circle',
    points: '',
    radius: DEFAULT_RADIUS,
    centerLat: 0,
    centerLng: 0,
    pointCount: 0,
    name: '',
    departmentId: '',
    alarmEnterDriver: false,
    alarmEnterPlatform: true,
    alarmExitDriver: false,
    alarmExitPlatform: true,
    byTime: false,
    startDate: '',
    endDate: '',
    limitSpeed: false,
    maxSpeed: DEFAULT_MAX_SPEED,
    delaySeconds: 0,
    remark: '',
  };
}

function areaTypeText(type: FenceAreaType): string {
  return AREA_TYPE_TEXT[type] ?? type;
}

function geometryText(pointCount: number, radius: number): string {
  return pointCount > 1 ? `${pointCount} 个点` : `${radius} 米`;
}

function alarmSummary(row: FenceRow): string {
  const parts: string[] = [];
  if (row.alarmEnterDriver) {
    parts.push('进-驾驶员');
  }
  if (row.alarmEnterPlatform) {
    parts.push('进-平台');
  }
  if (row.alarmExitDriver) {
    parts.push('出-驾驶员');
  }
  if (row.alarmExitPlatform) {
    parts.push('出-平台');
  }
  return parts.join(' / ') || '—';
}

function toShape(row: FenceRow): FenceShape {
  return {
    id: row.id,
    name: row.name,
    areaType: row.areaType,
    points: row.points,
    radius: row.radius,
  };
}

function redraw(): void {
  adapter?.drawShapes(rows.value.map(toShape), selectedId.value, handleMapSelect);
}

function handleMapSelect(id: string): void {
  selectedId.value = id;
  redraw();
}

async function loadDepartments(): Promise<void> {
  departments.value = await fetchDepartments();
}

async function loadFences(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchFences({
      page: page.value,
      size: size.value,
      name: filters.name.trim(),
      departmentId: filters.departmentId,
    });
    rows.value = result.records;
    total.value = result.total;
    if (selectedId.value && !rows.value.some((row) => row.id === selectedId.value)) {
      selectedId.value = null;
    }
    redraw();
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '区域列表加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  page.value = 1;
  selectedId.value = null;
  void loadFences();
}

function handleReset(): void {
  filters.name = '';
  filters.departmentId = '';
  page.value = 1;
  selectedId.value = null;
  void loadFences();
}

function handlePageChange(next: number): void {
  page.value = next;
  selectedId.value = null;
  void loadFences();
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
  selectedId.value = null;
  void loadFences();
}

function handleRowClick(row: FenceRow): void {
  selectedId.value = row.id;
  redraw();
  adapter?.focusShape(row.id);
}

function fillForm(row: FenceRow): void {
  form.areaType = row.areaType;
  form.points = row.points;
  form.radius = row.radius;
  form.centerLat = row.centerLat;
  form.centerLng = row.centerLng;
  form.pointCount = row.points.split(';').filter((item) => item.trim().length > 0).length;
  form.name = row.name;
  form.departmentId = row.departmentId;
  form.alarmEnterDriver = row.alarmEnterDriver;
  form.alarmEnterPlatform = row.alarmEnterPlatform;
  form.alarmExitDriver = row.alarmExitDriver;
  form.alarmExitPlatform = row.alarmExitPlatform;
  form.byTime = row.byTime;
  form.startDate = row.startTimeText ?? '';
  form.endDate = row.endTimeText ?? '';
  form.limitSpeed = row.limitSpeed;
  form.maxSpeed = row.maxSpeed ?? DEFAULT_MAX_SPEED;
  form.delaySeconds = row.delaySeconds;
  form.remark = row.remark;
}

/** 绘制完成即打开抽屉，几何只读展示，名称与报警参数由用户补全。 */
function openCreate(draft: FenceDraft): void {
  editingId.value = null;
  Object.assign(form, emptyForm(), {
    areaType: draft.areaType,
    points: draft.points,
    radius: draft.radius,
    centerLat: draft.centerLat,
    centerLng: draft.centerLng,
    pointCount: draft.points.split(';').filter((item) => item.trim().length > 0).length,
    departmentId: filters.departmentId || departments.value[0]?.id || '',
  });
  formError.value = '';
  drawerVisible.value = true;
}

function openEdit(row: FenceRow): void {
  editingId.value = row.id;
  Object.assign(form, emptyForm());
  fillForm(row);
  formError.value = '';
  drawerVisible.value = true;
}

function closeDrawer(): void {
  if (saving.value) {
    return;
  }
  drawerVisible.value = false;
  adapter?.cancelDraw();
}

function buildPayload(): FenceSavePayload | null {
  if (!form.name.trim()) {
    formError.value = '请填写区域名称';
    return null;
  }
  if (!form.departmentId) {
    formError.value = '请选择所属部门';
    return null;
  }
  if (!form.alarmEnterDriver && !form.alarmEnterPlatform && !form.alarmExitDriver && !form.alarmExitPlatform) {
    formError.value = '报警类型至少要选择一种';
    return null;
  }
  if (form.byTime && (!form.startDate || !form.endDate)) {
    formError.value = '启用时间段时必须填写开始与结束时间';
    return null;
  }
  if (form.limitSpeed && (!form.maxSpeed || form.maxSpeed <= 0)) {
    formError.value = '启用限速时必须填写大于 0 的限速值';
    return null;
  }
  return {
    name: form.name.trim(),
    departmentId: form.departmentId,
    areaType: form.areaType,
    points: form.points,
    radius: form.areaType === 'circle' ? form.radius : undefined,
    alarmEnterDriver: form.alarmEnterDriver,
    alarmEnterPlatform: form.alarmEnterPlatform,
    alarmExitDriver: form.alarmExitDriver,
    alarmExitPlatform: form.alarmExitPlatform,
    byTime: form.byTime,
    startDate: form.byTime ? form.startDate : null,
    endDate: form.byTime ? form.endDate : null,
    limitSpeed: form.limitSpeed,
    maxSpeed: form.limitSpeed ? form.maxSpeed : null,
    delaySeconds: form.delaySeconds > 0 ? form.delaySeconds : 0,
    remark: form.remark.trim(),
  };
}

async function handleSave(): Promise<void> {
  const payload = buildPayload();
  if (!payload) {
    return;
  }
  saving.value = true;
  formError.value = '';
  try {
    let savedId: string | null = null;
    if (editingId.value) {
      const saved = await updateFence(editingId.value, payload);
      savedId = saved.id;
      ElMessage.success('区域已修改');
    } else {
      const saved = await createFence(payload);
      savedId = saved.id;
      ElMessage.success('区域已创建');
    }
    adapter?.cancelDraw();
    drawerVisible.value = false;
    selectedId.value = savedId;
    await loadFences();
    if (savedId) {
      adapter?.focusShape(savedId);
    }
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '保存失败';
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: FenceRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除区域「${row.name}」？`, '删除区域', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteFence(row.id);
    ElMessage.success('区域已删除');
    if (selectedId.value === row.id) {
      selectedId.value = null;
    }
    await loadFences();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '删除失败');
  }
}

function startDraw(type: FenceAreaType): void {
  if (!adapter) {
    return;
  }
  activeTool.value = type;
  adapter.startDraw(type, (draft) => {
    activeTool.value = 'drag';
    openCreate(draft);
  });
}

function startMeasure(): void {
  activeTool.value = 'measure';
  adapter?.startMeasure();
}

function enableDrag(): void {
  activeTool.value = 'drag';
  adapter?.enableDrag();
}

async function handleSetCenter(): Promise<void> {
  const view = adapter?.getView();
  if (!view) {
    return;
  }
  try {
    await saveMapPreference({
      centerLat: Number(view.centerLat.toFixed(6)),
      centerLng: Number(view.centerLng.toFixed(6)),
      zoom: Math.round(view.zoom),
    });
    ElMessage.success('已设为当前账号的默认地图中心');
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '保存地图中心失败');
  }
}

function handleBackToCenter(): void {
  if (!mapConfig.value) {
    return;
  }
  adapter?.setCenter(mapConfig.value.centerLat, mapConfig.value.centerLng, mapConfig.value.centerZoom);
}

function formatCenter(latitude: number, longitude: number): string {
  return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
}

async function initMap(): Promise<void> {
  try {
    mapConfig.value = await fetchMapConfig();
  } catch (error) {
    mapError.value = error instanceof ApiError ? error.message : '地图配置加载失败';
    return;
  }
  if (!mapConfig.value.configured || !mapContainer.value) {
    return;
  }
  // 等容器完成布局再建实例：在 display:none 的容器上建图宽高为 0，首屏会空白。
  await nextTick();
  try {
    const amap = await loadAmap(mapConfig.value.key, mapConfig.value.securityCode, ['AMap.MouseTool']);
    if (!mapContainer.value) {
      return;
    }
    adapter = createFenceMapAdapter(mapContainer.value, {
      centerLat: mapConfig.value.centerLat,
      centerLng: mapConfig.value.centerLng,
      zoom: mapConfig.value.centerZoom,
    }, amap);
    adapter.resize();
    redraw();
  } catch (error) {
    mapError.value = error instanceof Error ? error.message : '地图初始化失败';
  }
}

onMounted(async () => {
  try {
    await loadDepartments();
  } catch (error) {
    departmentError.value = error instanceof ApiError ? error.message : '部门下拉加载失败';
  }
  await loadFences();
  await initMap();
});

onBeforeUnmount(() => {
  adapter?.destroy();
  adapter = null;
});
</script>

<template>
  <div class="cm-monitor">
    <el-card shadow="never">
      <div class="cm-fence-toolbar">
        <el-input v-model="filters.name" placeholder="区域名称前缀" clearable class="cm-fence-field" />
        <el-select v-model="filters.departmentId" placeholder="全部部门（数据范围内）" clearable class="cm-fence-field">
          <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button :icon="Refresh" text @click="loadFences">刷新</el-button>

        <div class="cm-fence-status">
          <span>共 <b class="cm-mono">{{ total }}</b> 个区域</span>
        </div>
      </div>

      <el-alert v-if="departmentError" :title="departmentError" type="warning" :closable="false" show-icon />
      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

      <div class="cm-fence-body">
        <div class="cm-fence-table">
          <el-table v-loading="loading" :data="rows" size="small" border row-key="id" highlight-current-row
            :current-row-key="selectedId ?? undefined" @row-click="handleRowClick">
            <el-table-column prop="name" label="区域名称" min-width="120" />
            <el-table-column label="类型/大小" width="110">
              <template #default="{ row }">
                <el-tag effect="plain" size="small">{{ areaTypeText(row.areaType) }}</el-tag>
                <span class="cm-fence-hint cm-fence-inline">
                  {{ geometryText(row.areaType === 'circle' ? 1 : row.points.split(';').length, row.radius) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="departmentName" label="部门" min-width="90" />
            <el-table-column label="报警" min-width="120">
              <template #default="{ row }">
                <span class="cm-fence-alarm">{{ alarmSummary(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="时间/限速" min-width="110">
              <template #default="{ row }">
                <span class="cm-fence-hint">
                  {{ row.byTime ? `${row.startTimeText} ~ ${row.endTimeText}` : '不限时' }}
                  <br />
                  {{ row.limitSpeed ? `限速 ${row.maxSpeed} km/h` : '不限速' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-permission="'fence:update'" :icon="Edit" text size="small" @click.stop="openEdit(row)">
                  编辑
                </el-button>
                <el-button v-permission="'fence:delete'" :icon="Delete" text size="small" type="danger"
                  @click.stop="handleDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination class="cm-fence-pagination" layout="total, sizes, prev, pager, next" :total="total"
            :current-page="page" :page-size="size" :page-sizes="[20, 50, 100]" @current-change="handlePageChange"
            @size-change="handleSizeChange" />
        </div>

        <div class="cm-fence-map">
          <div class="cm-fence-tools">
            <el-button :type="activeTool === 'drag' ? 'primary' : 'default'" :icon="Pointer" size="small"
              @click="enableDrag">
              拖动
            </el-button>
            <el-button :icon="ZoomIn" size="small" @click="adapter?.zoomIn()">放大</el-button>
            <el-button :icon="ZoomOut" size="small" @click="adapter?.zoomOut()">缩小</el-button>
            <el-button :type="activeTool === 'measure' ? 'primary' : 'default'" :icon="Odometer" size="small"
              @click="startMeasure">
              测距
            </el-button>
            <el-button v-permission="'fence:create'" :type="activeTool === 'circle' ? 'primary' : 'default'"
              :icon="Aim" size="small" @click="startDraw('circle')">
              圆形
            </el-button>
            <el-button v-permission="'fence:create'" :type="activeTool === 'rect' ? 'primary' : 'default'"
              :icon="Crop" size="small" @click="startDraw('rect')">
              矩形
            </el-button>
            <el-button v-permission="'fence:create'" :type="activeTool === 'polygon' ? 'primary' : 'default'"
              :icon="Share" size="small" @click="startDraw('polygon')">
              多边形
            </el-button>
            <el-button :icon="Location" size="small" @click="handleSetCenter">设为默认中心</el-button>
            <el-button :icon="RefreshLeft" size="small" @click="handleBackToCenter">回到默认中心</el-button>
          </div>
          <el-alert v-if="mapHint" :title="mapHint" :type="mapConfig?.configured ? 'error' : 'warning'"
            :closable="false" show-icon />
          <div ref="mapContainer" v-show="mapConfig?.configured" class="cm-fence-map-canvas" />
        </div>
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="520px" destroy-on-close @close="closeDrawer">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon />
      <el-form label-width="96px" label-position="right" size="small">
        <el-form-item label="区域类型">
          <el-tag effect="plain">{{ areaTypeText(form.areaType) }}</el-tag>
          <span class="cm-fence-hint cm-fence-inline">中心 {{ formatCenter(form.centerLat, form.centerLng) }}</span>
        </el-form-item>
        <el-form-item v-if="form.areaType === 'circle'" label="半径（米）">
          <el-input-number v-model="form.radius" :min="1" :max="50000" controls-position="right" />
        </el-form-item>
        <el-form-item label="区域名称">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="例如：大连港作业区" />
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="form.departmentId" placeholder="选择部门（数据范围内）" class="cm-fence-drawer-field">
            <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="进入报警">
          <el-checkbox v-model="form.alarmEnterDriver">给驾驶员</el-checkbox>
          <el-checkbox v-model="form.alarmEnterPlatform">给平台</el-checkbox>
        </el-form-item>
        <el-form-item label="离开报警">
          <el-checkbox v-model="form.alarmExitDriver">给驾驶员</el-checkbox>
          <el-checkbox v-model="form.alarmExitPlatform">给平台</el-checkbox>
        </el-form-item>
        <el-form-item label="时间段">
          <el-checkbox v-model="form.byTime">按时间段生效</el-checkbox>
        </el-form-item>
        <template v-if="form.byTime">
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择开始时间" class="cm-fence-drawer-field" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endDate" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择结束时间" class="cm-fence-drawer-field" />
          </el-form-item>
        </template>
        <el-form-item label="区域限速">
          <el-checkbox v-model="form.limitSpeed">启用限速</el-checkbox>
          <el-input-number v-if="form.limitSpeed" v-model="form.maxSpeed" :min="1" :max="200"
            controls-position="right" />
        </el-form-item>
        <el-form-item label="延时报警">
          <el-input-number v-model="form.delaySeconds" :min="0" :max="3600" controls-position="right" />
          <span class="cm-fence-hint cm-fence-inline">秒</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="几何预览">
          <span class="cm-fence-hint cm-fence-points">{{ form.points }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.cm-fence-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-fence-field {
  width: 200px;
}

.cm-fence-status {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-fence-body {
  display: flex;
  gap: var(--cm-space-3);
  align-items: stretch;
}

.cm-fence-table {
  display: flex;
  flex-direction: column;
  flex: 3;
  min-width: 0;
}

.cm-fence-map {
  display: flex;
  flex-direction: column;
  flex: 0 0 500px;
  gap: var(--cm-space-1);
  min-width: 0;
}

.cm-fence-tools {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-1);
}

.cm-fence-map-canvas {
  flex: none;
  height: 560px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
}

.cm-fence-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}

.cm-fence-alarm {
  color: var(--cm-color-text);
}

.cm-fence-hint {
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-fence-inline {
  margin-left: var(--cm-space-2);
}

.cm-fence-points {
  word-break: break-all;
  font-family: var(--cm-font-mono);
}

.cm-fence-drawer-field {
  width: 100%;
}
</style>
