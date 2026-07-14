<script setup lang="ts">
import type { AuthRole, AuthUser, AuthUserUpdatePayload } from '@/api/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import apiUser from '@/api/modules/user'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'

defineOptions({ name: 'UsersPage' })

type MetricTone = 'blue' | 'green' | 'danger' | 'violet'

interface SummaryCard {
  label: string
  value: number
  note: string
  tone: MetricTone
  icon: string
}

const { auth } = useAuth()
const userStore = useUserStore()
const canManage = computed(() => auth('user:manage'))

const loading = ref(false)
const users = ref<AuthUser[]>([])
const error = ref('')
const roles = ref<AuthRole[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive({
  keyword: '',
  roleCode: '',
  status: '',
})

const editVisible = ref(false)
const editSaving = ref(false)
const editTarget = ref<AuthUser | null>(null)
const editForm = reactive<AuthUserUpdatePayload>({
  displayName: '',
  roleCode: '',
  status: '',
})
const editFormRef = ref()

const editRules = {
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'disabled' },
]

const summaryCards = computed<SummaryCard[]>(() => {
  const activeCount = users.value.filter(user => normalizeStatus(user.status) === 'active').length
  const disabledCount = users.value.filter(user => normalizeStatus(user.status) === 'disabled').length
  return [
    {
      label: '当前页用户',
      value: users.value.length,
      note: `系统共 ${total.value.toLocaleString('zh-CN')} 个账号`,
      tone: 'blue',
      icon: 'i-ant-design:user-outlined',
    },
    {
      label: '已启用',
      value: activeCount,
      note: '当前页可正常登录账号',
      tone: 'green',
      icon: 'i-ant-design:check-circle-outlined',
    },
    {
      label: '已禁用',
      value: disabledCount,
      note: '当前页已停止登录账号',
      tone: 'danger',
      icon: 'i-ant-design:stop-outlined',
    },
    {
      label: '可分配角色',
      value: roles.value.length,
      note: '当前系统角色配置数量',
      tone: 'violet',
      icon: 'i-ant-design:safety-certificate-outlined',
    },
  ]
})

async function loadData() {
  loading.value = true
  try {
    error.value = ''
    const [usersRes, rolesRes] = await Promise.all([
      apiUser.getUsers({
        page: page.value,
        size: size.value,
        keyword: filters.keyword.trim(),
        roleCode: filters.roleCode,
        status: filters.status,
      }),
      apiUser.getRoles(),
    ])
    const pageData = usersRes.data
    users.value = Array.isArray(pageData?.list) ? pageData.list : []
    total.value = Number(pageData?.total ?? users.value.length)
    roles.value = Array.isArray(rolesRes.data) ? rolesRes.data : []
  }
  catch (err: unknown) {
    users.value = []
    total.value = 0
    const message = err instanceof Error ? err.message : '用户列表加载失败'
    error.value = message
  }
  finally {
    loading.value = false
  }
}

function normalizeStatus(status: string | undefined) {
  return String(status || '').toLowerCase()
}

function handleSearch() {
  page.value = 1
  void loadData()
}

function resetFilters() {
  filters.keyword = ''
  filters.roleCode = ''
  filters.status = ''
  handleSearch()
}

function openEdit(row: AuthUser) {
  editTarget.value = row
  editForm.displayName = row.displayName ?? ''
  editForm.roleCode = row.roleCode ?? ''
  editForm.status = normalizeStatus(row.status) || 'active'
  editVisible.value = true
}

async function handleSaveEdit() {
  if (!editFormRef.value) { return }
  await editFormRef.value.validate()
  if (!editTarget.value?.id) { return }

  if (isSelf(editTarget.value.id) && normalizeStatus(editForm.status) === 'disabled') {
    ElMessage.warning('不能禁用当前登录账号')
    return
  }

  editSaving.value = true
  try {
    const res = await apiUser.updateUser(editTarget.value.id, {
      displayName: editForm.displayName?.trim() || undefined,
      roleCode: editForm.roleCode,
      status: editForm.status,
    })
    const updated: AuthUser = res.data || {}
    const index = users.value.findIndex(user => user.id === editTarget.value!.id)
    if (index !== -1) {
      users.value[index] = { ...users.value[index], ...updated }
    }
    ElMessage.success('用户信息已更新')
    editVisible.value = false
  }
  catch (err: any) {
    ElMessage.error(err?.message || '更新失败')
  }
  finally {
    editSaving.value = false
  }
}

function isSelf(rowId: number | string | undefined): boolean {
  const profileId = userStore.profile.id
  if (rowId == null || profileId == null) { return false }
  return Number(rowId) === Number(profileId)
}

async function handleDisable(row: AuthUser) {
  if (!row.id) { return }
  if (isSelf(row.id)) {
    ElMessage.warning('不能禁用当前登录账号')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认要禁用用户「${row.displayName || row.username}」吗？禁用后该用户将无法登录。`,
      '禁用确认',
      { confirmButtonText: '确认禁用', cancelButtonText: '取消', type: 'warning' },
    )
    await apiUser.disableUser(row.id)
    const index = users.value.findIndex(user => user.id === row.id)
    if (index !== -1) {
      users.value[index] = { ...users.value[index], status: 'disabled' }
    }
    ElMessage.success('已禁用用户')
  }
  catch (err: any) {
    if (err === 'cancel' || err === 'close') { return }
    ElMessage.error(err?.message || '禁用操作失败')
  }
}

function formatDateTime(value: string | undefined) {
  if (!value) { return '-' }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadData)
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      title="用户管理"
      description="管理系统账号、角色分配、启停状态与最近登录信息。"
      icon="i-ant-design:team-outlined"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <FaIcon name="i-ri:refresh-line" />
          刷新数据
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid">
      <MrrMetricCard
        v-for="item in summaryCards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :note="item.note"
        :tone="item.tone"
        :icon="item.icon"
      />
    </section>

    <MrrFilterBar class="users-filter-bar">
      <el-input
        v-model="filters.keyword"
        class="users-filter__keyword"
        clearable
        name="user-keyword"
        autocomplete="off"
        aria-label="搜索用户名、显示名或角色"
        placeholder="搜索用户名、显示名或角色…"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <FaIcon name="i-ri:search-line" />
        </template>
      </el-input>

      <el-select
        v-model="filters.roleCode"
        class="users-filter__select"
        clearable
        aria-label="按角色筛选"
        placeholder="全部角色"
      >
        <el-option
          v-for="role in roles"
          :key="role.code"
          :label="role.name || role.code"
          :value="role.code!"
        />
      </el-select>

      <el-select
        v-model="filters.status"
        class="users-filter__select users-filter__select--status"
        clearable
        aria-label="按状态筛选"
        placeholder="全部状态"
      >
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <template #actions>
        <el-button type="primary" :loading="loading" @click="handleSearch">
          <FaIcon name="i-ri:search-line" />
          查询
        </el-button>
        <el-button @click="resetFilters">
          <FaIcon name="i-ri:restart-line" />
          重置
        </el-button>
      </template>
    </MrrFilterBar>

    <MrrDataTablePanel
      title="账号列表"
      description="展示用户账号、角色、启停状态与最近登录信息。"
      icon="i-ant-design:unordered-list-outlined"
      :count="total"
    >
      <div v-if="loading" class="users-state">
        <AppLoading type="table" :rows="8" />
      </div>
      <div v-else-if="error" class="users-state">
        <AppError :message="error" @retry="loadData" />
      </div>
      <div v-else-if="!users.length" class="users-state">
        <AppEmpty description="暂无符合条件的用户记录" />
      </div>

      <el-table v-else :data="users" row-key="id">
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">
            <div class="user-identity">
              <span class="user-avatar">{{ String(row.displayName || row.username || '?').slice(0, 1) }}</span>
              <strong>{{ row.username || '-' }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名称" min-width="140">
          <template #default="{ row }">
            {{ row.displayName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" round>
              {{ row.roleName || row.roleCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <MrrStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="190">
          <template #default="{ row }">
            <span class="mrr-tabular-number">{{ formatDateTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <div v-if="canManage" class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">
                <FaIcon name="i-ri:edit-line" />
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="normalizeStatus(row.status) === 'disabled' || isSelf(row.id)"
                @click="handleDisable(row)"
              >
                禁用
              </el-button>
            </div>
            <span v-else class="no-perm">无操作权限</span>
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </template>
    </MrrDataTablePanel>

    <el-dialog
      v-model="editVisible"
      title="编辑用户"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        class="user-edit-form"
        :model="editForm"
        :rules="editRules"
        label-position="top"
      >
        <el-form-item label="用户名">
          <div class="readonly-field">
            <FaIcon name="i-ri:user-3-line" />
            <span>{{ editTarget?.username }}</span>
          </div>
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="editForm.displayName" placeholder="默认使用用户名" clearable />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="editForm.roleCode" placeholder="请选择角色" class="full-width">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="role.name || role.code"
              :value="role.code!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" placeholder="请选择状态" class="full-width">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="editSaving" @click="handleSaveEdit">
          <FaIcon name="i-ri:save-3-line" />
          保存修改
        </el-button>
      </template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.users-filter__keyword {
  flex: 1 1 320px;
  min-width: 260px;
}

.users-filter__select {
  flex: 0 1 190px;
  width: 190px;
}

.users-filter__select--status {
  flex-basis: 160px;
  width: 160px;
}

.users-state {
  display: grid;
  min-height: 280px;
  padding: var(--mrr-space-5);
  place-items: center;
}

.user-identity {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.user-identity strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
}

.user-avatar {
  display: grid;
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 9%, var(--surface));
  border: 1px solid color-mix(in srgb, var(--color-primary) 14%, var(--divider));
  border-radius: 50%;
  place-items: center;
}

.row-actions {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  justify-content: flex-end;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.no-perm {
  font-size: 12px;
  color: var(--text-tertiary);
}

.user-edit-form {
  display: grid;
  gap: 2px;
}

.readonly-field {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  width: 100%;
  min-height: var(--mrr-control-height);
  padding: 0 12px;
  color: var(--text-secondary);
  background: var(--surface-muted);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-md);
}

.full-width {
  width: 100%;
}

@media (width <= 1100px) {
  .users-filter__keyword {
    flex-basis: 100%;
    max-width: none;
  }

  .users-filter__select,
  .users-filter__select--status {
    flex: 1 1 180px;
    width: auto;
  }
}

@media (width <= 640px) {
  .users-filter__keyword,
  .users-filter__select,
  .users-filter__select--status {
    flex: 1 1 100%;
    width: 100%;
    max-width: none;
  }
}
</style>
