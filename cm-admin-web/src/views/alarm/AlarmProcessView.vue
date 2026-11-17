<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Search } from '@element-plus/icons-vue';
import {
  fetchAlarms,
  processAlarm,
  processAlarms,
  type AlarmRecordRow,
} from '@/api/alarm';
import { ApiError } from '@/api/request';
import { todayEnd, todayStart } from '@/api/report';

const filters = reactive({
  plateNo: '',
  processed: '' as boolean | '',
  startTime: todayStart(),
  endTime: todayEnd(),
});

const rows = ref<AlarmRecordRow[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');

const selection = ref<AlarmRecordRow[]>([]);
const dialogVisible = ref(false);
const dialogTitle = ref('');
const dialogRemark = ref('');
const dialogError = ref('');
const dialogSaving = ref(false);
/** 本次要处理的报警 ID；单条与批量共用同一个对话框，只是数量不同。 */
const dialogIds = ref<string[]>([]);

async function loadRows(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchAlarms({
      page: page.value,
      size: size.value,
      plateNo: filters.plateNo.trim(),
      processed: filters.processed,
      startTime: filters.startTime,
      endTime: filters.endTime,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '报警查询失败';
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
  filters.processed = '';
  filters.startTime = todayStart();
  filters.endTime = todayEnd();
  page.value = 1;
  void loadRows();
}

function handleSelectionChange(picked: AlarmRecordRow[]): void {
  selection.value = picked;
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

function openSingle(row: AlarmRecordRow): void {
  if (row.processed) {
    ElMessage.warning('该报警已处理，不能重复处理');
    return;
  }
  dialogIds.value = [row.id];
  dialogTitle.value = `处理报警：${row.plateNo} ${row.alarmTypeName}`;
  openDialog();
}

function openBatch(): void {
  if (selection.value.length === 0) {
    ElMessage.warning('请先勾选要处理的报警');
    return;
  }
  dialogIds.value = selection.value.filter((row) => !row.processed).map((row) => row.id);
  if (dialogIds.value.length === 0) {
    ElMessage.warning('勾选的报警都已处理');
    return;
  }
  dialogTitle.value = `批量处理 ${dialogIds.value.length} 条报警`;
  openDialog();
}

function openDialog(): void {
  dialogRemark.value = '';
  dialogError.value = '';
  dialogVisible.value = true;
}

async function submitProcess(): Promise<void> {
  dialogSaving.value = true;
  dialogError.value = '';
  try {
    if (dialogIds.value.length === 1) {
      await processAlarm(dialogIds.value[0], dialogRemark.value.trim());
      ElMessage.success('报警已处理');
    } else {
      const result = await processAlarms(dialogIds.value, dialogRemark.value.trim());
      ElMessage.success(`已处理 ${result.processed} 条`);
      if (result.skipped.length > 0) {
        dialogError.value = `以下 ${result.skipped.length} 条被跳过：${result.skipped
          .map((item) => `${item.id}（${item.reason}）`)
          .join('；')}`;
        await loadRows();
        return;
      }
    }
    dialogVisible.value = false;
    await loadRows();
  } catch (error) {
    dialogError.value = error instanceof ApiError ? error.message : '报警处理失败';
  } finally {
    dialogSaving.value = false;
  }
}

onMounted(() => {
  void loadRows();
});
</script>

<template>
  <el-card shadow="never" class="cm-alarm-card">
    <div class="cm-alarm-toolbar">
      <el-input v-model="filters.plateNo" placeholder="车牌前缀" clearable class="cm-alarm-field" />
      <el-select v-model="filters.processed" placeholder="全部状态" clearable class="cm-alarm-field">
        <el-option label="未处理" :value="false" />
        <el-option label="已处理" :value="true" />
      </el-select>
      <el-date-picker
        v-model="filters.startTime"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="开始时间"
        class="cm-alarm-time"
      />
      <el-date-picker
        v-model="filters.endTime"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm:ss"
        placeholder="结束时间"
        class="cm-alarm-time"
      />
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
      <el-button
        v-permission="'alarm:process'"
        type="primary"
        :icon="Check"
        :disabled="selection.length === 0"
        @click="openBatch"
      >
        批量处理
      </el-button>
      <span class="cm-alarm-total">
        <template v-if="loading">加载中…</template>
        <template v-else>共 <b class="cm-mono">{{ total }}</b> 条</template>
      </span>
    </div>

    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

    <el-table
      v-loading="loading"
      :data="rows"
      size="small"
      border
      row-key="id"
      empty-text="当天没有报警"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="44" :selectable="(row: AlarmRecordRow) => !row.processed" />
      <el-table-column prop="startTimeText" label="报警时间" width="160" />
      <el-table-column prop="plateNo" label="车牌号" width="120" />
      <el-table-column prop="plateColorName" label="颜色" width="70" />
      <el-table-column prop="alarmTypeName" label="报警类型" width="130" />
      <el-table-column prop="alarmSourceName" label="报警来源" width="110" />
      <el-table-column prop="location" label="报警地点" min-width="180" show-overflow-tooltip />
      <el-table-column label="车速(km/h)" width="100">
        <template #default="{ row }">{{ row.velocity ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.processed ? 'success' : 'warning'" effect="plain">
            {{ row.processed ? '已处理' : '未处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" width="110">
        <template #default="{ row }">{{ row.processedUserName || '—' }}</template>
      </el-table-column>
      <el-table-column label="处理时间" width="160">
        <template #default="{ row }">{{ row.processedTimeText || '—' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="处理备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'alarm:process'"
            text
            type="primary"
            :disabled="row.processed"
            @click="openSingle(row)"
          >
            处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="cm-alarm-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="page"
      :page-size="size"
      :page-sizes="[20, 50, 100]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
    <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon />
    <el-form label-width="96px" size="small">
      <el-form-item label="处理结果">
        <el-tag type="success" effect="plain">已处理</el-tag>
        <span class="cm-alarm-hint">本轮只标记已处理，不向终端与上级平台下发报文</span>
      </el-form-item>
      <el-form-item label="处理备注">
        <el-input
          v-model="dialogRemark"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="可填写核实情况，留空表示仅标记已处理"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="dialogSaving" @click="submitProcess">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.cm-alarm-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-alarm-field {
  width: 180px;
}

.cm-alarm-time {
  width: 190px;
}

.cm-alarm-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-alarm-hint {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-alarm-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}
</style>
