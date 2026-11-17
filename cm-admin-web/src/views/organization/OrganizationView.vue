<script setup lang="ts">
import { ref } from 'vue';
import UserListView from './UserListView.vue';
import DepartmentListView from './DepartmentListView.vue';

const activeTab = ref('user');
</script>

<template>
  <el-tabs v-model="activeTab" class="cm-organization">
    <el-tab-pane label="用户列表" name="user">
      <UserListView />
    </el-tab-pane>
    <el-tab-pane label="车辆分组" name="department">
      <DepartmentListView />
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped lang="scss">
.cm-organization {
  :deep(.el-tabs__header) {
    margin-bottom: var(--cm-space-3);
  }
}
</style>

