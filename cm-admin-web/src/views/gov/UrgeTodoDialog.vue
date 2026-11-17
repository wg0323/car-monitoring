<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/request';
import { acknowledgeUrgeTodo, URGE_ACK_RESULTS, type UrgeTodoRow } from '@/api/gov';
import { useAppStore } from '@/stores/app';

/**
 * 督办详情与应答。字段口径与旧系统 jt809AlarmTodo.jsp 一致：
 * 车牌、督办 ID、报警来源、报警时间、督办截止、级别、督办人与联系方式。
 */
const props = defineProps<{ modelValue: boolean; todo: UrgeTodoRow | null }>();

const emit = defineEmits<{ 'update:modelValue': [value: boolean]; acked: [] }>();

const appStore = useAppStore();
const ackResult = ref(1);
const submitting = ref(false);
const errorText = ref('');

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const canAck = computed(() => appStore.hasPermission('command:gov809:urge'));

watch(
  () => props.todo,
  () => {
    ackResult.value = 1;
    errorText.value = '';
  },
);

async function submit(): Promise<void> {
  if (!props.todo) {
    return;
  }
  submitting.value = true;
  errorText.value = '';
  try {
    await acknowledgeUrgeTodo(props.todo.id, ackResult.value);
    ElMessage.success('督办应答已受理，结果见「上级政府平台」页签的 0x1401 记录');
    emit('acked');
    visible.value = false;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '督办应答失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="上级平台报警督办消息" width="520px" destroy-on-close>
    <template v-if="todo">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="车牌号">
          <span class="cm-mono">{{ todo.plateNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="车牌颜色">{{ todo.plateColorName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="督办 ID">
          <span class="cm-mono">{{ todo.superviseId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="督办级别">{{ todo.supervisionLevelText }}</el-descriptions-item>
        <el-descriptions-item label="报警来源">{{ todo.warnSourceText }}</el-descriptions-item>
        <el-descriptions-item label="报警类型">{{ todo.warnTypeText }}</el-descriptions-item>
        <el-descriptions-item label="报警时间">{{ todo.warnTimeText }}</el-descriptions-item>
        <el-descriptions-item label="督办截至">{{ todo.supervisionEndTimeText || '—' }}</el-descriptions-item>
        <el-descriptions-item label="督办人">{{ todo.supervisor || '—' }}</el-descriptions-item>
        <el-descriptions-item label="督办电话">{{ todo.supervisorTel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="督办邮箱" :span="2">{{ todo.supervisorEmail || '—' }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">{{ todo.ackResultText }}</el-descriptions-item>
        <el-descriptions-item label="应答时间">{{ todo.ackTimeText || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="errorText" class="cm-urge-dialog-alert" :title="errorText" type="error" :closable="false"
        show-icon />

      <div v-if="!todo.acknowledged" class="cm-urge-dialog-ack">
        <span class="cm-urge-dialog-label">报警处理结果</span>
        <el-select v-model="ackResult" size="small" class="cm-urge-dialog-select" :disabled="!canAck">
          <el-option v-for="item in URGE_ACK_RESULTS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span v-if="!canAck" class="cm-urge-dialog-hint">当前账号无督办应答权限</span>
      </div>
    </template>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="todo && !todo.acknowledged && canAck" type="primary" :loading="submitting" @click="submit">
        确定应答
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.cm-urge-dialog-alert {
  margin-top: var(--cm-space-2);
}

.cm-urge-dialog-ack {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.cm-urge-dialog-label {
  color: var(--cm-color-text-secondary);
  font-size: 13px;
}

.cm-urge-dialog-select {
  width: 160px;
}

.cm-urge-dialog-hint {
  color: var(--cm-color-warning);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
}
</style>
