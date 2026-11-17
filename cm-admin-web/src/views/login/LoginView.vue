<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchCsrfToken } from '@/api/auth';
import { ApiError } from '@/api/request';
import { useAppStore } from '@/stores/app';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();

const form = reactive({ username: '', password: '' });
const submitting = ref(false);
const errorText = ref('');

onMounted(async () => {
  // 登录前先取一次 CSRF 令牌：服务端会把 token 同时写进 XSRF-TOKEN cookie，登录请求才能过 CSRF 校验。
  try {
    await fetchCsrfToken();
  } catch {
    errorText.value = '无法连接后端服务，请确认 cm-admin-api 已启动（默认 8081）';
  }
});

async function handleSubmit(): Promise<void> {
  if (!form.username || !form.password) {
    errorText.value = '请输入登录名与口令';
    return;
  }
  submitting.value = true;
  errorText.value = '';
  try {
    await appStore.signIn({ username: form.username, password: form.password });
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    await router.push({ path: redirect });
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="cm-login">
    <div class="cm-login-card">
      <div class="cm-login-brand">车辆监控平台</div>
      <p class="cm-login-sub">调度台管理入口</p>

      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

      <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="登录名">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入登录名" />
        </el-form-item>
        <el-form-item label="口令">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入口令"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="cm-login-submit" :loading="submitting" native-type="submit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cm-login {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--cm-color-page);
}

.cm-login-card {
  width: 360px;
  padding: var(--cm-space-4);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius);
  background: var(--cm-color-card);
}

.cm-login-brand {
  color: var(--cm-color-brand);
  font-size: 18px;
  font-weight: 600;
}

.cm-login-sub {
  margin: 4px 0 var(--cm-space-3);
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-login-submit {
  width: 100%;
  margin-top: var(--cm-space-1);
}
</style>
