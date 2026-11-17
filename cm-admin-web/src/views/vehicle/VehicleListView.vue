<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import {
  fetchDepartments,
  fetchVehicleDetail,
  fetchVehicles,
  type DepartmentOption,
  type VehicleDetail,
  type VehicleRow,
} from '@/api/vehicle';

const filters = reactive({ plateNo: '', simNo: '', departmentId: '', online: '' as '' | 'true' | 'false' });
const rows = ref<VehicleRow[]>([]);
const departments = ref<DepartmentOption[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');

const drawerVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<VehicleDetail | null>(null);

async function loadDepartments(): Promise<void> {
  departments.value = await fetchDepartments();
}

async function loadVehicles(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchVehicles({
      page: page.value,
      size: size.value,
      plateNo: filters.plateNo.trim(),
      simNo: filters.simNo.trim(),
      departmentId: filters.departmentId,
      online: filters.online,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '车辆列表加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  page.value = 1;
  void loadVehicles();
}

function handleReset(): void {
  filters.plateNo = '';
  filters.simNo = '';
  filters.departmentId = '';
  filters.online = '';
  page.value = 1;
  void loadVehicles();
}

async function openDetail(row: VehicleRow): Promise<void> {
  drawerVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    detail.value = await fetchVehicleDetail(row.id);
  } catch (error) {
    detailError.value = error instanceof ApiError ? error.message : '车辆详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

function text(value: string | null): string {
  return value && value.trim().length > 0 ? value : '—';
}

function formatTime(value: string | null): string {
  return value ? `${value.replace('T', ' ').slice(0, 19)}` : '—';
}

function handlePageChange(next: number): void {
  page.value = next;
  void loadVehicles();
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
  void loadVehicles();
}

onMounted(async () => {
  try {
    await loadDepartments();
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '部门下拉加载失败';
  }
  await loadVehicles();
});
</script>

<template>
  <div class="cm-vehicle">
    <el-card shadow="never">
      <div class="cm-vehicle-toolbar">
        <el-input v-model="filters.plateNo" placeholder="车牌前缀" clearable class="cm-vehicle-field" />
        <el-input v-model="filters.simNo" placeholder="SIM 前缀" clearable class="cm-vehicle-field" />
        <el-select
          v-model="filters.departmentId"
          placeholder="全部部门（数据范围内）"
          clearable
          class="cm-vehicle-field"
        >
          <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="filters.online" placeholder="全部车辆" class="cm-vehicle-field">
          <el-option label="全部车辆" value="" />
          <el-option label="在线车辆" value="true" />
          <el-option label="离线车辆" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="cm-vehicle-total">
          <template v-if="loading">加载中…</template>
          <template v-else>共 <b class="cm-mono">{{ total }}</b> 辆</template>
        </span>
      </div>

      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

      <el-table v-loading="loading" :data="rows" size="small" border>
        <el-table-column prop="plateNo" label="车牌号" width="140" />
        <el-table-column prop="simNo" label="SIM 号" width="150" />
        <el-table-column prop="departmentName" label="所属部门" width="160" />
        <el-table-column prop="vehicleType" label="车辆类型" min-width="140" />
        <el-table-column prop="terminalNo" label="终端编号" width="150" />
        <el-table-column prop="driver" label="驾驶员" width="120" />
        <el-table-column prop="runStatus" label="运行状态" width="120" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="cm-vehicle-pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        :page-sizes="[20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-drawer v-model="drawerVisible" size="520px" destroy-on-close title="车辆详情">
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="车牌号">{{ detail.plateNo }}</el-descriptions-item>
          <el-descriptions-item label="车牌颜色">{{ text(detail.plateColorName) }}</el-descriptions-item>
          <el-descriptions-item label="所属车组">{{ detail.departmentName }}</el-descriptions-item>
          <el-descriptions-item label="所属业户">{{ text(detail.memberNo) }}</el-descriptions-item>
          <el-descriptions-item label="终端编号">{{ text(detail.terminalNo) }}</el-descriptions-item>
          <el-descriptions-item label="终端卡号">{{ text(detail.simNo) }}</el-descriptions-item>
          <el-descriptions-item label="车辆类型">{{ text(detail.vehicleType) }}</el-descriptions-item>
          <el-descriptions-item label="使用性质">{{ text(detail.useType) }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ text(detail.industry) }}</el-descriptions-item>
          <el-descriptions-item label="车籍地">{{ text(detail.region) }}</el-descriptions-item>
          <el-descriptions-item label="运行状态">{{ text(detail.runStatus) }}</el-descriptions-item>
          <el-descriptions-item label="车辆状态">{{ text(detail.vehicleStatus) }}</el-descriptions-item>
          <el-descriptions-item label="驾驶员">{{ text(detail.driver) }}</el-descriptions-item>
          <el-descriptions-item label="驾驶员电话">{{ text(detail.driverMobile) }}</el-descriptions-item>
          <el-descriptions-item label="监控联系人">{{ text(detail.monitor) }}</el-descriptions-item>
          <el-descriptions-item label="监控电话">{{ text(detail.monitorMobile) }}</el-descriptions-item>
          <el-descriptions-item label="发动机号">{{ text(detail.motorId) }}</el-descriptions-item>
          <el-descriptions-item label="发动机编号">{{ text(detail.engineNo) }}</el-descriptions-item>
          <el-descriptions-item label="车架号">{{ text(detail.frameNo) }}</el-descriptions-item>
          <el-descriptions-item label="制造商">{{ text(detail.manufacture) }}</el-descriptions-item>
          <el-descriptions-item label="车辆型号">{{ text(detail.modelNo) }}</el-descriptions-item>
          <el-descriptions-item label="服务商">{{ text(detail.vendor) }}</el-descriptions-item>
          <el-descriptions-item label="GPS 终端类型">{{ text(detail.gpsTerminalType) }}</el-descriptions-item>
          <el-descriptions-item label="道路运输证">{{ text(detail.operPermit) }}</el-descriptions-item>
          <el-descriptions-item label="运营线路">{{ text(detail.routes) }}</el-descriptions-item>
          <el-descriptions-item label="购置时间">{{ formatTime(detail.buyTime) }}</el-descriptions-item>
          <el-descriptions-item label="安装日期">{{ formatTime(detail.installDate) }}</el-descriptions-item>
          <el-descriptions-item label="最后检车">{{ formatTime(detail.lastCheckTime) }}</el-descriptions-item>
          <el-descriptions-item label="最近上线">{{ formatTime(detail.onlineTime) }}</el-descriptions-item>
          <el-descriptions-item label="最近离线">{{ formatTime(detail.offlineTime) }}</el-descriptions-item>
          <el-descriptions-item label="服务开始">{{ formatTime(detail.startDate) }}</el-descriptions-item>
          <el-descriptions-item label="服务结束">{{ formatTime(detail.endDate) }}</el-descriptions-item>
          <el-descriptions-item label="累计工时">{{ detail.workHour ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ text(detail.remark) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.cm-vehicle-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-vehicle-field {
  width: 200px;
}

.cm-vehicle-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-vehicle-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}
</style>
