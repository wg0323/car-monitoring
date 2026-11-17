<script setup lang="ts">
import { Close, Location } from '@element-plus/icons-vue';
import type { AlarmNotice } from '@/composables/useAlarmNotify';

/**
 * 右下角报警弹窗。非模态、不遮罩，与旧系统 #alarmWindow 的形态一致：
 * 累计最近 20 条报警文本，点击「定位」把报警点交给实时监控地图。
 */
defineProps<{ notices: AlarmNotice[] }>();

const emit = defineEmits<{ focus: [notice: AlarmNotice]; dismiss: [] }>();
</script>

<template>
  <div v-if="notices.length > 0" class="cm-alarm-window" role="dialog" aria-label="车辆报警消息">
    <div class="cm-alarm-window-header">
      <span class="cm-alarm-window-title">车辆报警消息</span>
      <el-button :icon="Close" text size="small" aria-label="关闭报警弹窗" @click="emit('dismiss')" />
    </div>
    <ul class="cm-alarm-window-body">
      <li v-for="notice in notices" :key="notice.id" class="cm-alarm-window-item">
        <span class="cm-alarm-window-time">{{ notice.timeText }}</span>
        <span class="cm-alarm-window-plate">[{{ notice.plateNo }}]</span>
        <span class="cm-alarm-window-type">{{ notice.alarmTypeName }}</span>
        <el-button
          v-if="notice.latitude !== null && notice.longitude !== null"
          class="cm-alarm-window-focus"
          :icon="Location"
          link
          type="primary"
          size="small"
          @click="emit('focus', notice)"
        >
          定位
        </el-button>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.cm-alarm-window {
  position: fixed;
  right: var(--cm-space-3);
  bottom: 44px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  width: 340px;
  max-height: 260px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
  background: var(--cm-color-card);
  box-shadow: 0 8px 24px rgb(15 23 42 / 18%);
}

.cm-alarm-window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--cm-space-1) var(--cm-space-2);
  border-bottom: 1px solid var(--cm-color-border);
  background: var(--cm-color-alarm);
  border-radius: var(--cm-radius) var(--cm-radius) 0 0;
  color: var(--cm-color-card);
}

.cm-alarm-window-title {
  font-size: 13px;
  font-weight: 600;
}

.cm-alarm-window-body {
  flex: 1;
  margin: 0;
  padding: var(--cm-space-1) var(--cm-space-2);
  overflow-y: auto;
  list-style: none;
}

.cm-alarm-window-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 0;
  border-bottom: 1px dashed var(--cm-color-border);
  font-size: 12px;
  line-height: 20px;

  &:last-child {
    border-bottom: 0;
  }
}

.cm-alarm-window-time {
  color: var(--cm-color-text-secondary);
  font-family: var(--cm-font-mono);
}

.cm-alarm-window-plate {
  font-family: var(--cm-font-mono);
  font-weight: 600;
}

.cm-alarm-window-type {
  flex: 1;
  overflow: hidden;
  color: var(--cm-color-alarm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cm-alarm-window-focus {
  flex-shrink: 0;
}
</style>
