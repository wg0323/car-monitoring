<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Aim, Link, Refresh, Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import {
  fetchCommandCandidates,
  fetchCommands,
  fetchGovCommands,
  fetchLinkState,
  sendGovLinkCommand,
  sendPositionQuery,
  type CommandRow,
  type CommandVehicleOption,
  type GovCommandRow,
  type LinkState,
} from '@/api/command';

const STATUS_OPTIONS = [
  { value: 'ACCEPTED', label: '已受理' },
  { value: 'SENT_MQ', label: '已发 MQ' },
  { value: 'SENT_TERMINAL', label: '已发终端' },
  { value: 'ACKED', label: '终端已应答' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMEOUT', label: '超时' },
];

const SOURCE_OPTIONS = [
  { value: 'SEND', label: '本端下发' },
  { value: 'RECEIVE', label: '上级下发' },
];

const REFRESH_INTERVAL_MS = 5000;

const activeTab = ref('terminal');

const candidates = ref<CommandVehicleOption[]>([]);
const candidateLoading = ref(false);
const selectedVehicleId = ref('');
const sending = ref(false);

const commandFilters = reactive<{ plateNo: string; status: string; timeRange: [string, string] | null }>({
  plateNo: '',
  status: '',
  timeRange: defaultTimeRange(),
});
const commandRows = ref<CommandRow[]>([]);
const commandTotal = ref(0);
const commandPage = ref(1);
const commandSize = ref(20);
const commandLoading = ref(false);

const govFilters = reactive<{ source: string; plateNo: string; timeRange: [string, string] | null }>({
  source: '',
  plateNo: '',
  timeRange: defaultTimeRange(),
});
const govRows = ref<GovCommandRow[]>([]);
const govTotal = ref(0);
const govPage = ref(1);
const govSize = ref(20);
const govLoading = ref(false);

const linkState = ref<LinkState | null>(null);
const linkLoading = ref(false);
const linkSending = ref(false);

const errorText = ref('');
const messageText = ref('');

const selectedVehicle = computed(() =>
  candidates.value.find((item) => item.id === selectedVehicleId.value),
);

let refreshTimer: number | undefined;

// 首次加载完成前就切走路由时，卸载钩子会比 setInterval 先执行，用这个标记阻止创建。
let disposed = false;

function defaultTimeRange(): [string, string] {
  // 默认取当天 00:00:00~23:59:59，与旧系统监管命令页一致：
  // 若结束时间固定为打开页面的时刻，之后新下发的命令会落在窗口外，永远刷不出来。
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
  return [formatDateTime(start), formatDateTime(end)];
}

function formatDateTime(value: Date): string {
  const pad = (input: number): string => String(input).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} `
    + `${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}

function displayText(value: string | null): string {
  return value && value.trim() ? value : '—';
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') {
    return 'success';
  }
  if (status === 'FAILED' || status === 'TIMEOUT' || status === '发送失败' || status === '失败') {
    return 'danger';
  }
  if (status === 'ACCEPTED' || status === '待下发' || status === '执行中') {
    return 'warning';
  }
  return 'info';
}

function linkTagType(state: string): 'success' | 'warning' | 'info' {
  if (state === 'LOGGED_IN') {
    return 'success';
  }
  return state === 'CONNECTED' ? 'warning' : 'info';
}

function linkStateText(state: string): string {
  if (state === 'LOGGED_IN') {
    return '已登录';
  }
  return state === 'CONNECTED' ? '已连接' : '未连接';
}

async function searchVehicles(keyword: string): Promise<void> {
  candidateLoading.value = true;
  try {
    candidates.value = await fetchCommandCandidates(keyword.trim());
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '车辆候选加载失败';
  } finally {
    candidateLoading.value = false;
  }
}

async function handlePositionQuery(): Promise<void> {
  if (!selectedVehicle.value) {
    ElMessage.warning('请先选择在线车辆');
    return;
  }
  sending.value = true;
  errorText.value = '';
  messageText.value = '';
  try {
    await sendPositionQuery(selectedVehicle.value.id);
    messageText.value = `已向 ${selectedVehicle.value.plateNo} 下发点名，终端应答后状态自动回填`;
    await loadCommands();
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '点名下发失败';
  } finally {
    sending.value = false;
  }
}

async function loadCommands(): Promise<void> {
  commandLoading.value = true;
  try {
    const range = commandFilters.timeRange ?? [];
    const result = await fetchCommands({
      page: commandPage.value,
      size: commandSize.value,
      plateNo: commandFilters.plateNo.trim(),
      status: commandFilters.status,
      startTime: range[0] || undefined,
      endTime: range[1] || undefined,
    });
    commandRows.value = result.records;
    commandTotal.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '命令结果加载失败';
    commandRows.value = [];
    commandTotal.value = 0;
  } finally {
    commandLoading.value = false;
  }
}

async function loadGovCommands(): Promise<void> {
  govLoading.value = true;
  try {
    const range = govFilters.timeRange ?? [];
    const result = await fetchGovCommands({
      page: govPage.value,
      size: govSize.value,
      source: govFilters.source,
      plateNo: govFilters.plateNo.trim(),
      startTime: range[0] || undefined,
      endTime: range[1] || undefined,
    });
    govRows.value = result.records;
    govTotal.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '809 命令加载失败';
    govRows.value = [];
    govTotal.value = 0;
  } finally {
    govLoading.value = false;
  }
}

async function loadLinkState(): Promise<void> {
  linkLoading.value = true;
  try {
    linkState.value = await fetchLinkState();
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '链路状态加载失败';
  } finally {
    linkLoading.value = false;
  }
}

async function handleLinkCommand(linkCmd: number): Promise<void> {
  linkSending.value = true;
  errorText.value = '';
  messageText.value = '';
  try {
    await sendGovLinkCommand(linkCmd);
    messageText.value = `链路命令 0x${linkCmd.toString(16)} 已下发，执行结果在下方列表回填`;
    await Promise.all([loadGovCommands(), loadLinkState()]);
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '链路命令下发失败';
  } finally {
    linkSending.value = false;
  }
}

function handleCommandSearch(): void {
  commandPage.value = 1;
  void loadCommands();
}

function handleGovSearch(): void {
  govPage.value = 1;
  void loadGovCommands();
}

function handleCommandPageChange(next: number): void {
  commandPage.value = next;
  void loadCommands();
}

function handleCommandSizeChange(next: number): void {
  commandSize.value = next;
  commandPage.value = 1;
  void loadCommands();
}

function handleGovPageChange(next: number): void {
  govPage.value = next;
  void loadGovCommands();
}

function handleGovSizeChange(next: number): void {
  govSize.value = next;
  govPage.value = 1;
  void loadGovCommands();
}

function handleTabChange(name: string): void {
  if (name === 'gov') {
    void Promise.all([loadGovCommands(), loadLinkState()]);
  }
}

onMounted(async () => {
  await Promise.all([searchVehicles(''), loadCommands()]);
  if (disposed) {
    return;
  }
  refreshTimer = window.setInterval(() => {
    if (activeTab.value === 'terminal') {
      if (!commandLoading.value) {
        void loadCommands();
      }
      return;
    }
    // 809 页签同样定时刷新：链路命令由另一个进程异步执行，
    // 不轮询就只能看到「待下发」，看不到结果回填。
    if (!govLoading.value) {
      void loadGovCommands();
      void loadLinkState();
    }
  }, REFRESH_INTERVAL_MS);
});

onBeforeUnmount(() => {
  disposed = true;
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer);
  }
});
</script>

<template>
  <div class="cm-command">
    <el-card shadow="never">
      <div class="cm-command-send">
        <span class="cm-command-title">终端点名</span>
        <el-select
          v-model="selectedVehicleId"
          class="cm-command-field"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="searchVehicles"
          :loading="candidateLoading"
          placeholder="输入车牌前缀搜索车辆"
        >
          <el-option
            v-for="item in candidates"
            :key="item.id"
            :label="`${item.plateNo}（${item.online ? '在线' : '离线'}）`"
            :value="item.id"
            :disabled="!item.online"
          />
        </el-select>
        <el-button
          v-permission="'command:send'"
          type="primary"
          :icon="Aim"
          :loading="sending"
          :disabled="!selectedVehicle"
          @click="handlePositionQuery"
        >
          点名
        </el-button>
        <span class="cm-command-hint">
          下发 0x8201 位置信息查询；离线车辆不可点名，终端应答后命令状态自动回填
        </span>
      </div>

      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
      <el-alert v-if="messageText" :title="messageText" type="success" :closable="false" show-icon />
    </el-card>

    <el-card shadow="never" class="cm-command-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="终端命令结果" name="terminal">
          <div class="cm-command-toolbar">
            <el-input v-model="commandFilters.plateNo" placeholder="车牌前缀" clearable class="cm-command-field" />
            <el-select v-model="commandFilters.status" placeholder="全部状态" clearable class="cm-command-field">
              <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-date-picker
              v-model="commandFilters.timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              class="cm-command-range"
            />
            <el-button type="primary" :icon="Search" @click="handleCommandSearch">查询</el-button>
            <el-button :icon="Refresh" text @click="loadCommands">刷新</el-button>
            <span class="cm-command-total">
              <template v-if="commandLoading">加载中…</template>
              <template v-else>共 <b class="cm-mono">{{ commandTotal }}</b> 条 · 5 秒自动刷新</template>
            </span>
          </div>

          <el-table v-loading="commandLoading" :data="commandRows" size="small" border>
            <el-table-column prop="plateNo" label="车牌号" width="120">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.plateNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="simNo" label="SIM 号" width="150">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.simNo }}</span>
              </template>
            </el-table-column>
            <el-table-column label="命令" width="150">
              <template #default="{ row }">{{ row.commandName }}（{{ row.commandCode }}）</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag effect="plain" :type="statusTagType(row.commandStatus)">{{ row.statusText }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下发时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.sentAtText) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="应答时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.ackAtText) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="受理时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.createTimeText) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="160">
              <template #default="{ row }">{{ displayText(row.remark) }}</template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="cm-command-pagination"
            layout="total, sizes, prev, pager, next"
            :total="commandTotal"
            :current-page="commandPage"
            :page-size="commandSize"
            :page-sizes="[20, 50, 100]"
            @current-change="handleCommandPageChange"
            @size-change="handleCommandSizeChange"
          />
        </el-tab-pane>

        <el-tab-pane label="上级政府平台" name="gov">
          <div class="cm-command-link">
            <span class="cm-command-title">主链路</span>
            <el-tag v-loading="linkLoading" effect="plain" :type="linkTagType(linkState?.mainLinkState ?? '')">
              {{ linkStateText(linkState?.mainLinkState ?? '') }}
            </el-tag>
            <span class="cm-command-title">从链路</span>
            <el-tag effect="plain" :type="linkTagType(linkState?.subLinkState ?? '')">
              {{ linkStateText(linkState?.subLinkState ?? '') }}
            </el-tag>
            <span class="cm-command-hint">接入码 {{ linkState?.platformId ?? '—' }}，待补发 {{ linkState?.pendingCount ?? 0 }} 条</span>
            <el-alert
              v-if="linkState?.lastError"
              class="cm-command-link-error"
              :title="linkState.lastError"
              type="warning"
              :closable="false"
              show-icon
            />
          </div>

          <div class="cm-command-toolbar">
            <el-button
              v-permission="'command:gov809:link'"
              type="primary"
              :icon="Link"
              :loading="linkSending"
              @click="handleLinkCommand(0x1001)"
            >
              连接主链路 (0x1001)
            </el-button>
            <el-button
              v-permission="'command:gov809:link'"
              :loading="linkSending"
              @click="handleLinkCommand(0x1003)"
            >
              注销主链路 (0x1003)
            </el-button>
            <el-button
              v-permission="'command:gov809:link'"
              :loading="linkSending"
              @click="handleLinkCommand(0x1007)"
            >
              主链路断开通知 (0x1007)
            </el-button>
            <el-button
              v-permission="'command:gov809:link'"
              type="danger"
              :loading="linkSending"
              @click="handleLinkCommand(0x1008)"
            >
              关闭主从链路 (0x1008)
            </el-button>
            <el-button :icon="Refresh" text @click="loadGovCommands">刷新</el-button>
          </div>

          <div class="cm-command-toolbar">
            <el-select v-model="govFilters.source" placeholder="全部来源" clearable class="cm-command-field">
              <el-option v-for="item in SOURCE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="govFilters.plateNo" placeholder="车牌前缀" clearable class="cm-command-field" />
            <el-date-picker
              v-model="govFilters.timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              class="cm-command-range"
            />
            <el-button type="primary" :icon="Search" @click="handleGovSearch">查询</el-button>
            <span class="cm-command-total">
              <template v-if="govLoading">加载中…</template>
              <template v-else>共 <b class="cm-mono">{{ govTotal }}</b> 条 · 5 秒自动刷新</template>
            </span>
          </div>

          <el-table v-loading="govLoading" :data="govRows" size="small" border>
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ row.sourceText }}</template>
            </el-table-column>
            <el-table-column label="命令类型" width="100">
              <template #default="{ row }">
                <span class="cm-mono">{{ row.cmdText }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="descr" label="类型描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="plateNo" label="车牌号" width="120">
              <template #default="{ row }">
                <span class="cm-mono">{{ displayText(row.plateNo) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="命令内容" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ displayText(row.cmdData) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag effect="plain" :type="statusTagType(row.commandStatus)">{{ row.commandStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下发时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.createTimeText) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="执行时间" width="170">
              <template #default="{ row }">
                <span class="cm-mono">{{ formatTime(row.updateTimeText) }}</span>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="cm-command-pagination"
            layout="total, sizes, prev, pager, next"
            :total="govTotal"
            :current-page="govPage"
            :page-size="govSize"
            :page-sizes="[20, 50, 100]"
            @current-change="handleGovPageChange"
            @size-change="handleGovSizeChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.cm-command-card {
  margin-top: var(--cm-space-3);
}

.cm-command-send {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.cm-command-title {
  color: var(--cm-color-text);
  font-size: 14px;
  font-weight: 600;
}

.cm-command-hint {
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-command-field {
  width: 200px;
}

.cm-command-range {
  width: 360px;
}

.cm-command-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-command-link {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-command-link-error {
  width: 100%;
}

.cm-command-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-command-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}
</style>
