<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Promotion, Refresh } from '@element-plus/icons-vue';
import { fetchOnlineUsers, publishBroadcast, type OnlineUser } from '@/api/message';
import { ApiError } from '@/api/request';
import { useMessageSocket } from '@/composables/useMessageSocket';
import { useAppStore } from '@/stores/app';

const appStore = useAppStore();

const onlineUsers = ref<OnlineUser[]>([]);
const listError = ref('');
const loadingUsers = ref(false);
const activeUserId = ref('');
const draft = ref('');
const messageBox = ref<HTMLElement | null>(null);

const broadcastVisible = ref(false);
const broadcastText = ref('');
const broadcastSending = ref(false);
const broadcastError = ref('');

const { connected, errorText, messages, connect, sendChat } = useMessageSocket(
  () => appStore.user?.id ?? '',
);

const activeUser = computed(() => onlineUsers.value.find((item) => item.userId === activeUserId.value));

async function loadOnlineUsers(): Promise<void> {
  loadingUsers.value = true;
  listError.value = '';
  try {
    onlineUsers.value = await fetchOnlineUsers();
    // 目标下线后不能继续发消息，选中项跟着清掉。
    if (activeUserId.value && !onlineUsers.value.some((item) => item.userId === activeUserId.value)) {
      activeUserId.value = '';
    }
  } catch (error) {
    listError.value = error instanceof ApiError ? error.message : '在线用户加载失败';
    onlineUsers.value = [];
  } finally {
    loadingUsers.value = false;
  }
}

function selectUser(userId: string): void {
  activeUserId.value = userId;
}

function handleSend(): void {
  if (!activeUserId.value) {
    ElMessage.warning('请先选择接收人');
    return;
  }
  if (!sendChat(activeUserId.value, draft.value)) {
    ElMessage.warning('连接未就绪或消息为空');
    return;
  }
  draft.value = '';
}

function openBroadcast(): void {
  broadcastText.value = '';
  broadcastError.value = '';
  broadcastVisible.value = true;
}

async function submitBroadcast(): Promise<void> {
  const content = broadcastText.value.trim();
  if (!content) {
    broadcastError.value = '广播内容不能为空';
    return;
  }
  broadcastSending.value = true;
  broadcastError.value = '';
  try {
    await publishBroadcast(content);
    ElMessage.success('系统广播已发布');
    broadcastVisible.value = false;
  } catch (error) {
    broadcastError.value = error instanceof ApiError ? error.message : '广播发布失败';
  } finally {
    broadcastSending.value = false;
  }
}

/** 新消息到达后滚到底部，避免用户手动下拉。 */
watch(
  () => messages.value.length,
  async () => {
    await nextTick();
    if (messageBox.value) {
      messageBox.value.scrollTop = messageBox.value.scrollHeight;
    }
  },
);

onMounted(async () => {
  connect();
  await loadOnlineUsers();
});
</script>

<template>
  <el-card shadow="never" class="cm-message-card">
    <div class="cm-message-toolbar">
      <el-tag :type="connected ? 'success' : 'info'" effect="plain">
        {{ connected ? '已连接' : '连接中…' }}
      </el-tag>
      <span class="cm-message-hint">在线 {{ onlineUsers.length }} 人</span>
      <el-button :icon="Refresh" :loading="loadingUsers" @click="loadOnlineUsers">刷新在线</el-button>
      <el-button v-permission="'message:broadcast'" type="primary" :icon="Promotion" @click="openBroadcast">
        系统广播
      </el-button>
    </div>

    <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
    <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

    <div class="cm-message-body">
      <aside class="cm-message-users" aria-label="在线用户">
        <div class="cm-message-users-title">在线用户</div>
        <el-scrollbar class="cm-message-users-scroll">
          <div
            v-for="item in onlineUsers"
            :key="item.userId"
            class="cm-message-user"
            :class="{ 'is-active': item.userId === activeUserId }"
            @click="selectUser(item.userId)"
          >
            <span class="cm-message-user-name">{{ item.displayName || item.userId }}</span>
            <span v-if="item.userId === appStore.user?.id" class="cm-message-user-self">本机</span>
          </div>
          <div v-if="onlineUsers.length === 0" class="cm-message-empty">当前没有其他在线账号</div>
        </el-scrollbar>
      </aside>

      <section class="cm-message-chat" aria-label="会话">
        <div class="cm-message-chat-title">
          <template v-if="activeUser">与 {{ activeUser.displayName }} 的会话</template>
          <template v-else>选择左侧在线用户开始聊天</template>
        </div>
        <div ref="messageBox" class="cm-message-list">
          <div
            v-for="item in messages"
            :key="item.id"
            class="cm-message-item"
            :class="{ 'is-mine': item.mine, 'is-system': item.kind === 'system' }"
          >
            <div class="cm-message-meta">
              <span>{{ item.fromName }}</span>
              <span class="cm-mono">{{ item.timestamp }}</span>
            </div>
            <div class="cm-message-bubble">{{ item.text }}</div>
          </div>
          <div v-if="messages.length === 0" class="cm-message-empty">
            还没有消息。消息只在当前会话内保留，刷新页面即清空。
          </div>
        </div>
        <div class="cm-message-input">
          <el-input
            v-model="draft"
            placeholder="输入消息，回车发送"
            :disabled="!activeUserId"
            maxlength="500"
            @keyup.enter="handleSend"
          />
          <el-button type="primary" :icon="Promotion" :disabled="!activeUserId" @click="handleSend">
            发送
          </el-button>
        </div>
      </section>
    </div>
  </el-card>

  <el-dialog v-model="broadcastVisible" title="发布系统广播" width="520px" destroy-on-close>
    <el-alert v-if="broadcastError" :title="broadcastError" type="error" :closable="false" show-icon />
    <el-input
      v-model="broadcastText"
      type="textarea"
      :rows="4"
      maxlength="500"
      show-word-limit
      placeholder="广播会立即推送给全部在线账号，不落库、不补发"
    />
    <template #footer>
      <el-button @click="broadcastVisible = false">取消</el-button>
      <el-button type="primary" :loading="broadcastSending" @click="submitBroadcast">发布</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.cm-message-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-message-hint {
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-message-body {
  display: flex;
  gap: var(--cm-space-3);
  min-height: 480px;
}

.cm-message-users {
  width: 220px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
  display: flex;
  flex-direction: column;
}

.cm-message-users-title,
.cm-message-chat-title {
  padding: var(--cm-space-1) var(--cm-space-2);
  border-bottom: 1px solid var(--cm-color-border);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-message-users-scroll {
  flex: 1;
}

.cm-message-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--cm-row-height);
  padding: 0 var(--cm-space-2);
  cursor: pointer;
  transition: background-color var(--cm-transition);

  &:hover {
    background-color: var(--cm-color-page);
  }

  &.is-active {
    background-color: var(--cm-color-page);
    color: var(--cm-color-brand);
  }
}

.cm-message-user-self {
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-message-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
}

.cm-message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--cm-space-2);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.cm-message-item {
  max-width: 70%;

  &.is-mine {
    align-self: flex-end;
    text-align: right;
  }

  &.is-system {
    align-self: center;
    max-width: 90%;
    text-align: center;
  }
}

.cm-message-meta {
  display: flex;
  gap: var(--cm-space-1);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-message-item.is-mine .cm-message-meta {
  justify-content: flex-end;
}

.cm-message-bubble {
  display: inline-block;
  margin-top: 2px;
  padding: var(--cm-space-1) var(--cm-space-2);
  border-radius: var(--cm-radius);
  background-color: var(--cm-color-page);
  color: var(--cm-color-text);
  text-align: left;
  word-break: break-all;
}

.cm-message-item.is-mine .cm-message-bubble {
  background-color: var(--cm-color-brand);
  color: var(--cm-color-card);
}

.cm-message-item.is-system .cm-message-bubble {
  background-color: var(--cm-color-warning);
  color: var(--cm-color-card);
}

.cm-message-empty {
  padding: var(--cm-space-2);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
  text-align: center;
}

.cm-message-input {
  display: flex;
  gap: var(--cm-space-2);
  padding: var(--cm-space-2);
  border-top: 1px solid var(--cm-color-border);
}

.cm-mono {
  font-family: var(--cm-font-mono);
  font-size: 12px;
}
</style>
