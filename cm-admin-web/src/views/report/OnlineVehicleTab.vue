<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Download, Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchDepartments, type DepartmentOption } from '@/api/vehicle';
import {
  buildExportUrl,
  fetchOnlineVehicleReport,
  type ReportOnlineVehicleRow,
  type ReportOption,
} from '@/api/report';

defineProps<{
  statuses: ReportOption[];
}>();

const OFFLINE_DAY_OPTIONS = [1, 2, 3, 4, 7, 10];

const filters = reactive({
  plateNo: '',
  simNo: '',
  departmentId: '',
  status: '',
  offlineDays: undefined as number | undefined,
});
const rows = ref<ReportOnlineVehicleRow[]>([]);
const departments = ref<DepartmentOption[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');

async function loadRows(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchOnlineVehicleReport({
      page: page.value,
      size: size.value,
      plateNo: filters.plateNo.trim(),
      simNo: filters.simNo.trim(),
      departmentId: filters.departmentId,
      status: filters.status,
      offlineDays: filters.offlineDays,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '当前在线情况加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  page.value = 1;
  void loadRows();
}

function handleReset(): void {
  filters.plateNo = '';
  filters.simNo = '';
  filters.departmentId = '';
  filters.status = '';
  filters.offlineDays = undefined;
  page.value = 1;
  void loadRows();
}

function handlePageChange(next: number): void {
  page.value = next;
  void loadRows();
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
  void loadRows();
}

function handleExport(): void {
  window.location.href = buildExportUrl('/online-vehicles/export', {
    plateNo: filters.plateNo.trim(),
    simNo: filters.simNo.trim(),
    departmentId: filters.departmentId,
    status: filters.status,
    offlineDays: filters.offlineDays,
  });
}

onMounted(async () => {
  try {
    departments.value = await fetchDepartments();
  } catch {
    departments.value = [];
  }
  await loadRows();
});
</script>

<template>
  <div class="cm-report-tab">
    <div class="cm-report-toolbar">
      <el-input v-model="filters.plateNo" placeholder="车牌前缀" clearable class="cm-report-field" />
      <el-input v-model="filters.simNo" placeholder="SIM 前缀" clearable class="cm-report-field" />
      <el-select v-model="filters.departmentId" placeholder="全部部门" clearable class="cm-report-field">
        <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="cm-report-field">
        <el-option v-for="item in statuses" :key="item.code" :label="item.name" :value="item.code" />
      </el-select>
      <el-select
        v-model="filters.offlineDays"
        placeholder="离线天数"
        clearable
        class="cm-report-field"
      >
        <el-option v-for="day in OFFLINE_DAY_OPTIONS" :key="day" :label="`超过${day}天`" :value="day" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button :icon="Download" @click="handleExport">导出</el-button>
      <span class="cm-report-total">
        <template v-if="loading">加载中…</template>
        <template v-else>共 <b class="cm-mono">{{ total }}</b> 辆</template>
      </span>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

    <el-table v-loading="loading" :data="rows" size="small" border>
      <el-table-column prop="plateNo" label="车牌号" width="120" />
      <el-table-column prop="plateColorName" label="颜色" width="70" />
      <el-table-column prop="simNo" label="终端卡号" width="140" class-name="cm-mono" />
      <el-table-column prop="departmentName" label="车组" width="150" show-overflow-tooltip />
      <el-table-column label="上线状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.onlineStatusName === '在线' ? 'success' : 'info'" effect="plain">
            {{ row.onlineStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上线时间" width="160">
        <template #default="{ row }">{{ row.onlineTimeText || '—' }}</template>
      </el-table-column>
      <el-table-column label="离线时长(分钟)" width="130">
        <template #default="{ row }">{{ row.offlineMinutes ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="runStatus" label="运行状态" width="100" />
      <el-table-column prop="vehicleType" label="车辆类型" width="120" show-overflow-tooltip />
      <el-table-column label="入网时间" width="160">
        <template #default="{ row }">{{ row.installDateText || '—' }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="cm-report-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="size"
      :page-sizes="[20, 50, 100]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped lang="scss">
.cm-report-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-report-field {
  width: 180px;
}

.cm-report-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-report-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}
</style>

