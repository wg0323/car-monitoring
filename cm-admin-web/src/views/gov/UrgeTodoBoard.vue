<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { Refresh, Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchUrgeTodos, type UrgeTodoRow } from '@/api/gov';

/** 底部「报警督办」页签：上级平台下发的督办列表，双击打开详情与应答窗口。 */
const emit = defineEmits<{ open: [row: UrgeTodoRow] }>();

const AUTO_REFRESH_MS = 10000;

const filters = reactive<{ plateNo: string; acknowledged: string }>({ plateNo: '', acknowledged: '' });
const rows = ref<UrgeTodoRow[]>([]);
const total = ref(0);
const loading = ref(false);
const errorText = ref('');
const autoRefresh = ref(true);

let timer: number | null = null;
let disposed = false;

async function load(): Promise<void> {
  if (disposed) {
    return;
  }
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchUrgeTodos({
      page: 1,
      size: 10,
      plateNo: filters.plateNo.trim(),
      acknowledged: filters.acknowledged === '' ? undefined : filters.acknowledged === 'true',
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '督办列表加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  void load();
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

function handleRowDblClick(row: UrgeTodoRow): void {
  emit('open', row);
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
  <div class="cm-urge-board">
    <div class="cm-urge-board-toolbar">
      <el-input v-model="filters.plateNo" placeholder="车牌前缀" clearable size="small" class="cm-urge-board-field" />
      <el-select v-model="filters.acknowledged" placeholder="全部状态" clearable size="small" class="cm-urge-board-field">
        <el-option label="未应答" value="false" />
        <el-option label="已应答" value="true" />
      </el-select>
      <el-button :icon="Search" size="small" type="primary" @click="handleSearch">查询</el-button>
      <el-button :icon="Refresh" size="small" @click="load">刷新</el-button>
      <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" @change="toggleAutoRefresh" />
      <span class="cm-urge-board-total">
        共 <b class="cm-mono">{{ total }}</b> 条，双击行查看详情并应答
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
      <el-table-column prop="warnTypeText" label="报警类型" width="100" />
      <el-table-column prop="warnTimeText" label="报警时间" width="160">
        <template #default="{ row }">
          <span class="cm-mono">{{ row.warnTimeText }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ackResultText" label="处理" width="100">
        <template #default="{ row }">
          <el-tag :type="row.acknowledged ? 'success' : 'warning'" effect="plain" size="small">
            {{ row.ackResultText }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="supervisionEndTimeText" label="督办截至" width="160">
        <template #default="{ row }">
          <span class="cm-mono">{{ row.supervisionEndTimeText || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="supervisionLevelText" label="级别" width="70" />
      <el-table-column prop="supervisor" label="督办人" min-width="90" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.cm-urge-board {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);
}

.cm-urge-board-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.cm-urge-board-field {
  width: 140px;
}

.cm-urge-board-total {
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
