<script setup lang="ts">
import type { AuthRole, AuthUser, AuthUserUpdatePayload } from '@/api/types'
import { Edit, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import apiUser from '@/api/modules/user'

defineOptions({ name: 'UsersPage' })

const { auth } = useAuth()
const userStore = useUserStore()
const canManage = computed(() => auth('user:manage'))

const loading = ref(false)
const users = ref<AuthUser[]>([])
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

const summaryCards = computed(() => {
  const activeCount = users.value.filter(user => normalizeStatus(user.status) === 'active').length
  const disabledCount = users.value.filter(user => normalizeStatus(user.status) === 'disabled').length
  return [
    { label: '当前页用户', value: users.value.length, note: `共 ${total.value} 个账号` },
    { label: '已启用', value: activeCount, note: '当前页可登录账号' },
    { label: '已禁用', value: disabledCount, note: '当前页停用账号' },
    { label: '角色数', value: roles.value.length, note: '可分配角色配置' },
  ]
})

async function loadData() {
  loading.value = true
  try {
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
  catch (error: any) {
    users.value = []
    total.value = 0
    ElMessage.error(error?.message || '用户列表加载失败')
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
  loadData()
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
    const idx = users.value.findIndex(user => user.id === editTarget.value!.id)
    if (idx !== -1) {
      users.value[idx] = { ...users.value[idx], ...updated }
    }
    ElMessage.success('用户信息已更新')
    editVisible.value = false
  }
  catch (error: any) {
    ElMessage.error(error?.message || '更新失败')
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
    const idx = users.value.findIndex(user => user.id === row.id)
    if (idx !== -1) {
      users.value[idx] = { ...users.value[idx], status: 'disabled' }
    }
    ElMessage.success('已禁用用户')
  }
  catch (error: any) {
    if (error === 'cancel' || error === 'close') { return }
    ElMessage.error(error?.message || '禁用操作失败')
  }
}

function statusTagType(status: string | undefined) {
  const value = normalizeStatus(status)
  if (value === 'active') { return 'success' }
  if (value === 'disabled') { return 'danger' }
  return 'info'
}

function statusLabel(status: string | undefined) {
  const option = statusOptions.find(item => item.value === normalizeStatus(status))
  return option?.label || status || '-'
}

function formatDateTime(value: string | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          User Management
        </p>
        <h2>用户管理</h2>
        <p class="subtitle">
          管理系统账号、角色分配、启停状态与最近登录信息。
        </p>
      </div>
      <el-button :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">
          {{ item.label }}
        </div>
        <div class="summary-value">
          {{ item.value }}
        </div>
        <div class="summary-note">
          {{ item.note }}
        </div>
      </el-card>
    </section>

    <el-card shadow="never">
      <el-form class="filter-form" inline @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="用户名 / 显示名 / 角色"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.roleCode" clearable placeholder="全部角色" style="width: 160px;">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="role.name || role.code"
              :value="role.code!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px;">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="resetFilters">
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="users" stripe style="margin-top: 12px;">
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="displayName" label="显示名称" min-width="130">
          <template #default="{ row }">
            {{ row.displayName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ row.roleName || row.roleCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.lastLoginAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage">
              <el-button size="small" type="primary" @click="openEdit(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="normalizeStatus(row.status) === 'disabled' || isSelf(row.id)"
                @click="handleDisable(row)"
              >
                禁用
              </el-button>
            </template>
            <span v-else class="no-perm">无权限</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑用户" width="480px" :close-on-click-modal="false">
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="用户名">
          <span class="readonly-field">{{ editTarget?.username }}</span>
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="editForm.displayName" placeholder="默认使用用户名" clearable />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="editForm.roleCode" placeholder="请选择角色" style="width: 100%;">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="role.name || role.code"
              :value="role.code!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" placeholder="请选择状态" style="width: 100%;">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="editSaving" @click="handleSaveEdit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: var(--text-primary);
}

.summary-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.filter-form {
  row-gap: 8px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.readonly-field {
  font-size: 14px;
  color: var(--text-secondary);
}

.no-perm {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (width <= 960px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
