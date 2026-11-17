<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import { fetchDepartments, type DepartmentOption } from '@/api/vehicle';
import { fetchUserDetail, fetchUsers, type UserDetail, type UserRow } from '@/api/user';

const USER_TYPE_LABELS: Record<string, string> = {
  admin: '超级管理员',
  operation: '运营',
  monitor: '监控',
};

const filters = reactive({ keyword: '', departmentId: '' });
const rows = ref<UserRow[]>([]);
const departments = ref<DepartmentOption[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorText = ref('');
// 部门下拉与列表的错误分开存：loadUsers 开头会清空 errorText，共用一份会把刚提示的下拉失败抹掉。
const departmentError = ref('');

const drawerVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<UserDetail | null>(null);

async function loadDepartments(): Promise<void> {
  departments.value = await fetchDepartments();
}

async function loadUsers(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    const result = await fetchUsers({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim(),
      departmentId: filters.departmentId,
    });
    rows.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '用户列表加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: UserRow): Promise<void> {
  drawerVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    detail.value = await fetchUserDetail(row.id);
  } catch (error) {
    detailError.value = error instanceof ApiError ? error.message : '用户详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

function handleSearch(): void {
  page.value = 1;
  void loadUsers();
}

function handleReset(): void {
  filters.keyword = '';
  filters.departmentId = '';
  page.value = 1;
  void loadUsers();
}

function handlePageChange(next: number): void {
  page.value = next;
  void loadUsers();
}

function handleSizeChange(next: number): void {
  size.value = next;
  page.value = 1;
  void loadUsers();
}

/** 后端返回 ISO-8601，表格与抽屉统一显示到秒。 */
function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}

function typeLabel(value: string): string {
  return USER_TYPE_LABELS[value] ?? value;
}

onMounted(async () => {
  try {
    await loadDepartments();
  } catch (error) {
    departmentError.value = error instanceof ApiError ? error.message : '部门下拉加载失败';
  }
  await loadUsers();
});
</script>

<template>
  <div class="cm-user">
    <el-card shadow="never">
      <div class="cm-user-toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="登录名或用户名称前缀"
          clearable
          class="cm-user-field"
        />
        <el-select
          v-model="filters.departmentId"
          placeholder="全部部门（数据范围内）"
          clearable
          class="cm-user-field"
        >
          <el-option v-for="item in departments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="cm-user-total">
          <template v-if="loading">加载中…</template>
          <template v-else>共 <b class="cm-mono">{{ total }}</b> 人</template>
        </span>
      </div>

      <el-alert v-if="departmentError" :title="departmentError" type="warning" :closable="false" show-icon />
      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

      <el-table v-loading="loading" :data="rows" size="small" border>
        <el-table-column prop="username" label="登录名" min-width="150" />
        <el-table-column prop="displayName" label="用户名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phoneNo" label="手机号" min-width="150" />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            <span class="cm-mono">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-permission="'organization:user:detail'" text type="primary" @click="openDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="cm-user-pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        :page-sizes="[20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-drawer v-model="drawerVisible" title="用户详情" size="520px" destroy-on-close>
      <div v-loading="detailLoading" class="cm-user-detail">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon />

        <template v-if="detail">
          <el-descriptions title="基本信息" :column="2" border size="small">
            <el-descriptions-item label="登录名">{{ detail.username }}</el-descriptions-item>
            <el-descriptions-item label="用户名称">{{ detail.displayName }}</el-descriptions-item>
            <el-descriptions-item label="用户类型">{{ typeLabel(detail.userType) }}</el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag :type="detail.enabled ? 'success' : 'info'" effect="plain">
                {{ detail.enabled ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.phoneNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">
              <span class="cm-mono">{{ formatTime(detail.createTime) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="用户 ID" :span="2">
              <span class="cm-mono">{{ detail.id }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="cm-user-block-title">角色（{{ detail.roles.length }}）</div>
          <el-table :data="detail.roles" size="small" border empty-text="未分配角色">
            <el-table-column prop="roleName" label="角色名称" min-width="160" />
            <el-table-column prop="roleCode" label="角色编码" width="160" />
          </el-table>

          <div class="cm-user-block-title">可访问部门（{{ detail.departments.length }}）</div>
          <el-table :data="detail.departments" size="small" border empty-text="未绑定部门">
            <el-table-column prop="name" label="部门名称" min-width="200" />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.cm-user-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-user-field {
  width: 200px;
}

.cm-user-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-mono {
  color: var(--cm-color-text);
  font-family: var(--cm-font-mono);
  font-size: 14px;
}

.cm-user-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-3);
}

.cm-user-detail {
  min-height: 200px;
}

.cm-user-block-title {
  margin: var(--cm-space-4) 0 var(--cm-space-2);
  color: var(--cm-color-text);
  font-weight: 600;
}
</style>
