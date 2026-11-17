<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { ApiError } from '@/api/request';
import {
  createDepartment,
  deleteDepartment,
  fetchDepartmentDetails,
  updateDepartment,
  type DepartmentDetail,
  type DepartmentSavePayload,
} from '@/api/dept';

const ROOT_PARENT_ID = '0';

interface DepartmentTreeNode extends DepartmentDetail {
  children?: DepartmentTreeNode[];
}

const filters = reactive({ keyword: '' });
const rows = ref<DepartmentDetail[]>([]);
const loading = ref(false);
const errorText = ref('');

const drawerVisible = ref(false);
const editingId = ref<string | null>(null);
const saving = ref(false);
const formError = ref('');
const form = reactive<DepartmentSavePayload>({ name: '', parentId: ROOT_PARENT_ID });

/** 平铺列表按 parentId 拼树；父分组不在当前范围时提升为根，避免整枝消失。 */
const treeRows = computed<DepartmentTreeNode[]>(() => {
  const nodeMap = new Map<string, DepartmentTreeNode>();
  rows.value.forEach((row) => nodeMap.set(row.id, { ...row }));
  const roots: DepartmentTreeNode[] = [];
  nodeMap.forEach((node) => {
    const parent = node.parentId === ROOT_PARENT_ID ? undefined : nodeMap.get(node.parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), node];
    } else {
      roots.push(node);
    }
  });
  const sortTree = (list: DepartmentTreeNode[]): void => {
    list.sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'));
    list.forEach((node) => {
      if (node.children) {
        sortTree(node.children);
      }
    });
  };
  sortTree(roots);
  return roots;
});

/** 上级下拉：编辑时排除自己与全部下级，防止把上级改成自己的子孙。 */
const parentOptions = computed(() => {
  const excluded = new Set<string>();
  if (editingId.value) {
    excluded.add(editingId.value);
    const childrenOf = new Map<string, string[]>();
    rows.value.forEach((row) => {
      const list = childrenOf.get(row.parentId) ?? [];
      list.push(row.id);
      childrenOf.set(row.parentId, list);
    });
    const queue = [editingId.value];
    while (queue.length > 0) {
      const current = queue.pop() as string;
      (childrenOf.get(current) ?? []).forEach((childId) => {
        if (!excluded.has(childId)) {
          excluded.add(childId);
          queue.push(childId);
        }
      });
    }
  }
  return rows.value
    .filter((row) => !excluded.has(row.id))
    .map((row) => ({ id: row.id, label: pathLabel(row.id) }));
});

function pathLabel(id: string): string {
  const byId = new Map(rows.value.map((row) => [row.id, row]));
  const names: string[] = [];
  let current = byId.get(id);
  while (current) {
    names.unshift(current.name);
    current = current.parentId === ROOT_PARENT_ID ? undefined : byId.get(current.parentId);
  }
  return names.join(' / ');
}

async function loadDepartments(): Promise<void> {
  loading.value = true;
  errorText.value = '';
  try {
    rows.value = await fetchDepartmentDetails(filters.keyword.trim());
  } catch (error) {
    errorText.value = error instanceof ApiError ? error.message : '车辆分组加载失败';
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  void loadDepartments();
}

function handleReset(): void {
  filters.keyword = '';
  void loadDepartments();
}

function resetForm(): void {
  form.name = '';
  form.parentId = ROOT_PARENT_ID;
  form.departmentType = '';
  form.memberNo = '';
  form.roadPermitWord = '';
  form.roadPermitNo = '';
  form.businessScope = '';
  form.region = '';
  form.contactPerson = '';
  form.contactPhone = '';
  form.remark = '';
  formError.value = '';
}

function openCreate(): void {
  editingId.value = null;
  resetForm();
  drawerVisible.value = true;
}

function openEdit(row: DepartmentDetail): void {
  editingId.value = row.id;
  resetForm();
  form.name = row.name;
  form.parentId = row.parentId === ROOT_PARENT_ID ? ROOT_PARENT_ID : row.parentId;
  form.departmentType = row.departmentType ?? '';
  form.memberNo = row.memberNo ?? '';
  form.roadPermitWord = row.roadPermitWord ?? '';
  form.roadPermitNo = row.roadPermitNo ?? '';
  form.businessScope = row.businessScope ?? '';
  form.region = row.region ?? '';
  form.contactPerson = row.contactPerson ?? '';
  form.contactPhone = row.contactPhone ?? '';
  form.remark = row.remark ?? '';
  drawerVisible.value = true;
}

async function handleSubmit(): Promise<void> {
  if (!form.name.trim()) {
    formError.value = '车组名称不能为空';
    return;
  }
  saving.value = true;
  formError.value = '';
  const payload: DepartmentSavePayload = { ...form, name: form.name.trim() };
  try {
    if (editingId.value) {
      await updateDepartment(editingId.value, payload);
    } else {
      await createDepartment(payload);
    }
    ElMessage.success(editingId.value ? '车组已修改' : '车组已新增');
    drawerVisible.value = false;
    await loadDepartments();
  } catch (error) {
    formError.value = error instanceof ApiError ? error.message : '保存失败';
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: DepartmentDetail): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除车组「${row.name}」吗？有车辆或子车组时会被拒绝。`, '删除确认', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteDepartment(row.id);
    ElMessage.success('车组已删除');
    await loadDepartments();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '删除失败');
  }
}

onMounted(() => {
  void loadDepartments();
});
</script>

<template>
  <div class="cm-department">
    <el-card shadow="never">
      <div class="cm-department-toolbar">
        <el-input v-model="filters.keyword" placeholder="车组名称前缀" clearable class="cm-department-field" />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button v-permission="'organization:department:create'" type="primary" :icon="Plus" @click="openCreate">
          新增
        </el-button>
        <span class="cm-department-total">共 <b class="cm-mono">{{ rows.length }}</b> 个车组</span>
      </div>

      <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />

      <el-table v-loading="loading" :data="treeRows" row-key="id" default-expand-all size="small" border>
        <el-table-column prop="name" label="车组名称" min-width="200" />
        <el-table-column prop="parentName" label="上级车组" min-width="140" />
        <el-table-column prop="departmentType" label="车组类型" width="120" />
        <el-table-column prop="memberNo" label="所属业户" width="140" />
        <el-table-column prop="contactPerson" label="联系人" width="110" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="region" label="所属地区" width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'organization:department:update'" text type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'organization:department:delete'" text type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerVisible" size="520px" destroy-on-close :title="editingId ? '编辑车组' : '新增车组'">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" show-icon />
      <el-form label-width="96px" size="small" class="cm-department-form">
        <el-form-item label="车组名称" required>
          <el-input v-model="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="上级车组">
          <el-select v-model="form.parentId" class="cm-department-wide">
            <el-option label="无（根车组）" value="0" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="车组类型">
          <el-input v-model="form.departmentType" maxlength="32" />
        </el-form-item>
        <el-form-item label="所属业户">
          <el-input v-model="form.memberNo" maxlength="64" />
        </el-form-item>
        <el-form-item label="许可证字">
          <el-input v-model="form.roadPermitWord" maxlength="64" />
        </el-form-item>
        <el-form-item label="许可证号">
          <el-input v-model="form.roadPermitNo" maxlength="64" />
        </el-form-item>
        <el-form-item label="经营范围">
          <el-input v-model="form.businessScope" maxlength="255" />
        </el-form-item>
        <el-form-item label="所属地区">
          <el-input v-model="form.region" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="32" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.cm-department-toolbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cm-department-field {
  width: 200px;
}

.cm-department-total {
  margin-left: auto;
  color: var(--cm-color-text-secondary);
  font-size: 12px;
}

.cm-department-wide {
  width: 100%;
}

.cm-department-form {
  margin-top: var(--cm-space-3);
}
</style>

