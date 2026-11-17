<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { fetchSystemInfo, type SystemInfo } from '@/api/system';

const route = useRoute();
const info = ref<SystemInfo | null>(null);
const errorText = ref('');

const domain = computed(() => route.meta.domain ?? '功能域');

onMounted(async () => {
  try {
    const result = await fetchSystemInfo();
    if (result.code === 0) {
      info.value = result.data;
    } else {
      errorText.value = result.message;
    }
  } catch {
    errorText.value = '后端探针接口暂不可用，请先启动 cm-admin-api（默认 8081）';
  }
});
</script>

<template>
  <div class="cm-placeholder">
    <el-card>
      <template #header>{{ domain }}</template>
      <p class="cm-hint">
        P1 工程骨架占位页。该域的页面、API 与按钮权限在 P4 按 docs/feature-mapping.csv 逐项迁移。
      </p>

      <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

      <el-descriptions v-if="info" :column="2" border title="后端探针 /api/v1/system/info">
        <el-descriptions-item label="应用名">{{ info.applicationName }}</el-descriptions-item>
        <el-descriptions-item label="升级阶段">{{ info.stage }}</el-descriptions-item>
        <el-descriptions-item label="激活 Profile">{{ info.activeProfiles }}</el-descriptions-item>
        <el-descriptions-item label="JDK 版本">{{ info.javaVersion }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.cm-placeholder {
  max-width: 960px;
}

.cm-hint {
  margin: 0 0 var(--cm-space-3);
  color: var(--cm-color-text-secondary);
}
</style>
