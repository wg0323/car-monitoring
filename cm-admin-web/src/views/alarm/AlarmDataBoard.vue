<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchAlarms, type AlarmNotifyRow } from '@/api/alarm';

/** 底部「报警数据」页签：当天报警列表，双击一行把该报警点交给实时监控地图。 */
const emit = defineEmits<{ focus: [row: AlarmNotifyRow] }>();

const AUTO_REFRESH_MS = 5000;

const rows = ref<AlarmNotifyRow[]>([]);
const total = ref(0);
const loading = ref(false);
const errorText = ref('');
const autoRefresh = ref(true);

let timer: number | null = null;
let disposed = false;

function todayRange(): [string, string] {
  const now = new Date();
  const pad = (value: number): string => String(value).padStart(2, '0');
  const day = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  return [`${day} 00:00:00`, `${day} 23:59:59`];
}

async function load(): Promise<void> {
  if (disposed) {
    return;
  }
  loading.value = true;
  errorText.value = '';
  const [startTime, endTime] = todayRange();
  try {
    const result = await fetchAlarms({ page: 1, size: 10, startTime, endTime });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '报警数据加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function toggleAutoRefresh(): void {
  if (autoRefresh.value) {
    startTimer();
  } else {
    stopTimer();
  }
}

function startTimer(): void {
  stopTimer();
  timer = window.setInterval(() => void load(), AUTO_REFRESH_MS);
}

function stopTimer(): void {
  if (timer !== null) {
    window.clearInterval(timer);
    timer = null;
  }
}

function handleRowDblClick(row: AlarmNotifyRow): void {
  emit('focus', row);
}

onMounted(() => {
  void load();
  if (autoRefresh.value) {
    startTimer();
  }
});

onBeforeUnmount(() => {
  disposed = true;
  stopTimer();
});
</script>

<template>
  <div class="cm-alarm-board">
    <div class="cm-alarm-board-toolbar">
      <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
      <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" @change="toggleAutoRefresh" />
      <span class="cm-alarm-board-total">
        今日报警 <b class="cm-mono">{{ total }}</b> 条，双击行可在地图上定位
      </span>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

    <el-table v-loading="loading" :data="rows" :height="104" size="small" border row-key="id"
      @row-dblclick="handleRowDblClick">
      <el-table-column prop="plateNo" label="车牌号" width="110">
        <template #default="{ row }">
          <span class="cm-mono">{{ row.plateNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="plateColorName" label="颜色" width="64" />
      <el-table-column prop="alarmTypeName" label="报警类型" width="120" />
      <el-table-column prop="alarmSourceName" label="报警来源" width="110" />
      <el-table-column prop="startTimeText" label="报警时间" width="160">
        <template #default="{ row }">
          <span class="cm-mono">{{ row.startTimeText }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="simNo" label="SIM" width="120" />
      <el-table-column label="处理" width="80">
        <template #default="{ row }">
          <el-tag :type="row.processed ? 'success' : 'warning'" effect="plain" size="small">
            {{ row.processed ? '已处理' : '未处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="location" label="报警地点" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.location || '—' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.cm-alarm-board {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);
}

.cm-alarm-board-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.cm-alarm-board-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 13px;
}
</style>
