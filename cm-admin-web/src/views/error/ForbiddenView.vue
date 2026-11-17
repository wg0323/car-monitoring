<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

function goHome(): void {
  void router.push({ path: '/' });
}
</script>

<template>
  <div class="cm-forbidden">
    <el-result
      icon="warning"
      title="403"
      sub-title="当前账号没有该页面的访问权限，请切换到有权限的账号或联系管理员。"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.cm-forbidden {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--cm-color-page);
}
</style>
