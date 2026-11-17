<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Download, Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchDepartments, type DepartmentOption } from '@/api/vehicle';
import {
  buildExportUrl,
  fetchOnlineRecordReport,
  todayEnd,
  todayStart,
  type ReportOnlineRecordRow,
  type ReportOption,
} from '@/api/report';

defineProps<{
  recordTypes: ReportOption[];
}>();

const filters = reactive({
  plateNo: '',
  departmentId: '',
  eventType: '',
  startTime: todayStart(),
  endTime: todayEnd(),
});
const rows = ref<ReportOnlineRecordRow[]>([]);
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
    const result = await fetchOnlineRecordReport({
      page: page.value,
      size: size.value,
      plateNo: filters.plateNo.trim(),
      departmentId: filters.departmentId,
      eventType: filters.eventType,
      startTime: filters.startTime,
      endTime: filters.endTime,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '上下线记录加载失败';
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
  filters.departmentId = '';
  filters.eventType = '';
  filters.startTime = todayStart();
  filters.endTime = todayEnd();
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
  window.location.href = buildExportUrl('/online-records/export', {
    plateNo: filters.plateNo.trim(),
    departmentId: filters.departmentId,
    eventType: filters.eventType,
    startTime: filters.startTime,
    endTime: filters.endTime,
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
      <el-select v-model="filters.departmentId" placeholder="全部部门" clearable class="cm-report-field">
        <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="filters.eventType" placeholder="全部记录" clearable class="cm-report-field">
        <el-option v-for="item in recordTypes" :key="item.code" :label="item.name" :value="item.code" />
      </el-select>
      <el-date-picker
        v-model="filters.startTime"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="开始时间"
        class="cm-report-time"
      />
      <el-date-picker
        v-model="filters.endTime"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="结束时间"
        class="cm-report-time"
      />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button :icon="Download" @click="handleExport">导出</el-button>
      <span class="cm-report-total">
        <template v-if="loading">加载中…</template>
        <template v-else>共 <b class="cm-mono">{{ total }}</b> 条</template>
      </span>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

    <el-table v-loading="loading" :data="rows" size="small" border>
      <el-table-column prop="plateNo" label="车牌号" width="120" />
      <el-table-column prop="plateColorName" label="颜色" width="70" />
      <el-table-column prop="recordTypeName" label="记录类型" width="100" />
      <el-table-column prop="location" label="地点" min-width="240" show-overflow-tooltip />
      <el-table-column prop="startTimeText" label="开始时间" width="160" />
      <el-table-column label="结束时间" width="160">
        <template #default="{ row }">{{ row.endTimeText || '—' }}</template>
      </el-table-column>
      <el-table-column label="时长(分钟)" width="100">
        <template #default="{ row }">{{ row.timeSpan ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="departmentName" label="车组" width="160" show-overflow-tooltip />
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

.cm-report-time {
  width: 190px;
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

