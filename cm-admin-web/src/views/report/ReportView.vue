<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { fetchReportOptions, type ReportOptions } from '@/api/report';
import AlarmReportTab from './AlarmReportTab.vue';
import OnlineRecordTab from './OnlineRecordTab.vue';
import OnlineVehicleTab from './OnlineVehicleTab.vue';
import ParkingReportTab from './ParkingReportTab.vue';

const activeTab = ref('alarm');
const errorText = ref('');
const options = ref<ReportOptions>({
  alarmTypes: [],
  alarmSources: [],
  plateColors: [],
  onlineRecordTypes: [],
  onlineStatuses: [],
});

onMounted(async () => {
  try {
    options.value = await fetchReportOptions();
  } catch {
    errorText.value = '报表筛选项加载失败，请刷新页面重试';
  }
});
</script>

<template>
  <div class="cm-report">
    <el-card shadow="never">
      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
      <el-tabs v-model="activeTab">
        <el-tab-pane label="报警查询" name="alarm">
          <AlarmReportTab :alarm-types="options.alarmTypes" :alarm-sources="options.alarmSources" />
        </el-tab-pane>
        <el-tab-pane label="上下线记录" name="online-record">
          <OnlineRecordTab :record-types="options.onlineRecordTypes" />
        </el-tab-pane>
        <el-tab-pane label="当前在线" name="online-vehicle">
          <OnlineVehicleTab :statuses="options.onlineStatuses" />
        </el-tab-pane>
        <el-tab-pane label="停车报警" name="parking">
          <ParkingReportTab :alarm-sources="options.alarmSources" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

