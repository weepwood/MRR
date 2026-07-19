<script setup lang="ts">
import type { AuthRole, AuthUserUpdatePayload } from '@/api/types'
import type { CredentialAwareUser, UserCredentialResult } from '@/api/user-credential-types'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const { auth } = useAuth()
const userStore = useUserStore()
const canManage = computed(() => auth('user:manage'))
const loading = ref(false)
const users = ref<CredentialAwareUser[]>([])
const error = ref('')
const roles = ref<AuthRole[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ keyword: '', roleCode: '', status: '' })

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'disabled' },
]

const editVisible = ref(false)
const editSaving = ref(false)
const editTarget = ref<CredentialAwareUser | null>(null)
const editFormRef = ref()
const editForm = reactive<AuthUserUpdatePayload>({ displayName: '', roleCode: '', status: '' })

const createVisible = ref(false)
const createSaving = ref(false)
const createFormRef = ref()
const createForm = reactive({
  username: '',
  displayName: '',
  roleCode: '',
  status: 'active' as 'active' | 'disabled',
  temporaryPasswordValidHours: 24,
})

const resetVisible = ref(false)
const resetSaving = ref(false)
const resetTarget = ref<CredentialAwareUser | null>(null)
const resetFormRef = ref()
const resetForm = reactive({ administratorPassword: '', temporaryPasswordValidHours: 24 })

const credentialVisible = ref(false)
const credentialAcknowledged = ref(false)
const credentialTitle = ref('')
const credentialResult = ref<UserCredentialResult | null>(null)

const summaryCards = computed(() => [
  {
    label: '当前页用户', value: users.value.length,
    note: `系统共 ${total.value.toLocaleString('zh-CN')} 个账号`, tone: 'blue' as MetricTone,
    icon: 'i-ant-design:user-outlined',
  },
  {
    label: '已启用', value: users.value.filter(item => normalizeStatus(item.status) === 'active').length,
    note: '当前页可正常登录账号', tone: 'green' as MetricTone,
    icon: 'i-ant-design:check-circle-outlined',
  },
  {
    label: '待修改密码', value: users.value.filter(item => item.mustChangePassword).length,
    note: '首次登录或管理员重置后待处理', tone: 'violet' as MetricTone,
    icon: 'i-ri:key-2-line',
  },
  {
    label: '已禁用', value: users.value.filter(item => normalizeStatus(item.status) === 'disabled').length,
    note: '当前页已停止登录账号', tone: 'danger' as MetricTone,
    icon: 'i-ant-design:stop-outlined',
  },
])

const editRules = {
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 40, message: '用户名长度应为 3 到 40 位', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9._-]+$/, message: '只能包含字母、数字、点、下划线和短横线', trigger: 'blur' },
  ],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const resetRules = {
  administratorPassword: [{ required: true, message: '请输入当前管理员密码', trigger: 'blur' }],
}

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
    users.value = (Array.isArray(usersRes.data?.list) ? usersRes.data.list : []) as CredentialAwareUser[]
    total.value = Number(usersRes.data?.total ?? users.value.length)
    roles.value = Array.isArray(rolesRes.data) ? rolesRes.data : []
  }
  catch (err: any) {
    users.value = []
    total.value = 0
    error.value = err?.message || '用户列表加载失败'
  }
  finally {
    loading.value = false
  }
}

function normalizeStatus(status?: string) {
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

function isSelf(rowId?: number | string) {
  return rowId != null && userStore.profile.id != null && Number(rowId) === Number(userStore.profile.id)
}

function openCreate() {
  Object.assign(createForm, {
    username: '', displayName: '', roleCode: roles.value[0]?.code || '',
    status: 'active', temporaryPasswordValidHours: 24,
  })
  createVisible.value = true
}

async function handleCreate() {
  await createFormRef.value?.validate()
  createSaving.value = true
  try {
    const response = await apiUser.createUser({
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim() || undefined,
      roleCode: createForm.roleCode,
      status: createForm.status,
      temporaryPasswordValidHours: createForm.temporaryPasswordValidHours,
    })
    createVisible.value = false
    showCredentialResult('账号创建成功', response.data)
    await loadData()
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '创建用户失败')
  }
  finally {
    createSaving.value = false
  }
}

function openEdit(row: CredentialAwareUser) {
  editTarget.value = row
  editForm.displayName = row.displayName ?? ''
  editForm.roleCode = row.roleCode ?? ''
  editForm.status = normalizeStatus(row.status) || 'active'
  editVisible.value = true
}

async function handleSaveEdit() {
  await editFormRef.value?.validate()
  if (!editTarget.value?.id) return
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
    const index = users.value.findIndex(item => item.id === editTarget.value!.id)
    if (index !== -1) users.value[index] = { ...users.value[index], ...res.data }
    editVisible.value = false
    ElMessage.success('用户信息已更新')
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '更新失败')
  }
  finally {
    editSaving.value = false
  }
}

function openReset(row: CredentialAwareUser) {
  resetTarget.value = row
  resetForm.administratorPassword = ''
  resetForm.temporaryPasswordValidHours = 24
  resetVisible.value = true
}

async function handleResetPassword() {
  await resetFormRef.value?.validate()
  if (!resetTarget.value?.id) return
  resetSaving.value = true
  try {
    const response = await apiUser.resetUserPassword(resetTarget.value.id, { ...resetForm })
    resetVisible.value = false
    showCredentialResult('密码重置成功', response.data)
    await loadData()
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '密码重置失败')
  }
  finally {
    resetSaving.value = false
  }
}

function showCredentialResult(title: string, result?: UserCredentialResult) {
  if (!result) {
    ElMessage.error('服务端未返回临时凭据')
    return
  }
  credentialTitle.value = title
  credentialResult.value = result
  credentialAcknowledged.value = false
  credentialVisible.value = true
}

async function copyText(value: string, successMessage: string) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(successMessage)
  }
  catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function copyCredentialSummary() {
  const result = credentialResult.value
  if (!result) return
  const text = [
    `用户名：${result.user.username}`,
    `临时密码：${result.temporaryPassword}`,
    `有效期至：${formatDateTime(result.temporaryPasswordExpiresAt)}`,
    '首次登录后必须修改密码。',
  ].join('\n')
  void copyText(text, '账号信息已复制')
}

function closeCredentialResult() {
  if (!credentialAcknowledged.value) {
    ElMessage.warning('请确认已经安全保存或交付临时密码')
    return
  }
  credentialVisible.value = false
  credentialResult.value = null
}

async function handleDisable(row: CredentialAwareUser) {
  if (!row.id || isSelf(row.id)) return
  try {
    await ElMessageBox.confirm(
      `确认禁用用户「${row.displayName || row.username}」吗？禁用后该用户将无法登录。`,
      '禁用确认',
      { confirmButtonText: '确认禁用', cancelButtonText: '取消', type: 'warning' },
    )
    await apiUser.disableUser(row.id)
    const index = users.value.findIndex(item => item.id === row.id)
    if (index !== -1) users.value[index] = { ...users.value[index], status: 'disabled' }
    ElMessage.success('已禁用用户')
  }
  catch (err: any) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err?.response?.data?.message || err?.message || '禁用操作失败')
  }
}

function credentialStatus(row: CredentialAwareUser) {
  if (normalizeStatus(row.status) === 'disabled') return { label: '已禁用', type: 'info' as const }
  if (!row.mustChangePassword) return { label: '正常', type: 'success' as const }
  const expiresAt = row.temporaryPasswordExpiresAt ? new Date(row.temporaryPasswordExpiresAt) : null
  if (expiresAt && !Number.isNaN(expiresAt.getTime()) && expiresAt.getTime() < Date.now()) {
    return { label: '临时密码已过期', type: 'danger' as const }
  }
  return { label: row.passwordResetAt ? '密码已重置' : '待修改初始密码', type: 'warning' as const }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadData)
</script>

<template>
  <MrrPageShell width="fluid">
    <MrrPageHeader
      eyebrow="User Management"
      title="用户管理"
      description="管理系统账号、角色、凭据状态、启停状态与最近登录信息。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadData">
          <FaIcon name="i-ri:refresh-line" />刷新数据
        </el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          <FaIcon name="i-ri:user-add-line" />创建用户
        </el-button>
      </template>
    </MrrPageHeader>

    <section class="mrr-metric-grid">
      <MrrMetricCard v-for="item in summaryCards" :key="item.label" v-bind="item" />
    </section>

    <MrrFilterBar class="users-filter-bar">
      <el-input v-model="filters.keyword" class="users-filter__keyword" clearable placeholder="搜索用户名、显示名或角色…" @keyup.enter="handleSearch" />
      <el-select v-model="filters.roleCode" class="users-filter__select" clearable placeholder="全部角色">
        <el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" />
      </el-select>
      <el-select v-model="filters.status" class="users-filter__select" clearable placeholder="全部状态">
        <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
      </el-select>
      <template #actions>
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </template>
    </MrrFilterBar>

    <MrrDataTablePanel title="账号列表" description="临时密码只在创建或重置成功时展示一次。" icon="i-ant-design:unordered-list-outlined" :count="total">
      <div v-if="loading" class="users-state"><AppLoading type="table" :rows="8" /></div>
      <div v-else-if="error" class="users-state"><AppError :message="error" @retry="loadData" /></div>
      <div v-else-if="!users.length" class="users-state"><AppEmpty description="暂无符合条件的用户记录" /></div>

      <el-table v-else :data="users" row-key="id">
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-identity"><span class="user-avatar">{{ String(row.displayName || row.username || '?').slice(0, 1) }}</span><strong>{{ row.username }}</strong></div>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="角色" min-width="130">
          <template #default="{ row }"><el-tag size="small" effect="plain" round>{{ row.roleName || row.roleCode }}</el-tag></template>
        </el-table-column>
        <el-table-column label="凭据状态" min-width="170">
          <template #default="{ row }"><el-tag :type="credentialStatus(row).type" effect="light" round>{{ credentialStatus(row).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="账号状态" width="110"><template #default="{ row }"><MrrStatusTag :status="row.status" /></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="180"><template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="操作" width="245" fixed="right" align="right">
          <template #default="{ row }">
            <div v-if="canManage" class="row-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :disabled="isSelf(row.id) || normalizeStatus(row.status) === 'disabled'" @click="openReset(row)">重置密码</el-button>
              <el-button link type="danger" :disabled="normalizeStatus(row.status) === 'disabled' || isSelf(row.id)" @click="handleDisable(row)">禁用</el-button>
            </div>
            <span v-else class="no-perm">无操作权限</span>
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="handleSearch" @current-change="loadData" />
      </template>
    </MrrDataTablePanel>

    <el-dialog v-model="createVisible" title="创建用户" width="520px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon title="系统将生成一次性临时密码，用户首次登录后必须修改。" />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top" class="dialog-form">
        <el-form-item label="用户名" prop="username"><el-input v-model="createForm.username" autocomplete="off" placeholder="例如 zhangsan" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="createForm.displayName" placeholder="例如 张三" /></el-form-item>
        <el-form-item label="角色" prop="roleCode"><el-select v-model="createForm.roleCode" class="full-width"><el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" /></el-select></el-form-item>
        <div class="form-grid">
          <el-form-item label="初始状态"><el-select v-model="createForm.status" class="full-width"><el-option v-for="option in statusOptions" :key="option.value" v-bind="option" /></el-select></el-form-item>
          <el-form-item label="临时密码有效期"><el-input-number v-model="createForm.temporaryPasswordValidHours" :min="1" :max="168" /><span class="unit">小时</span></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="createSaving" @click="handleCreate">创建用户</el-button></template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑用户" width="480px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top" class="dialog-form">
        <el-form-item label="用户名"><div class="readonly-field">{{ editTarget?.username }}</div></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="editForm.displayName" /></el-form-item>
        <el-form-item label="角色" prop="roleCode"><el-select v-model="editForm.roleCode" class="full-width"><el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" /></el-select></el-form-item>
        <el-form-item label="状态" prop="status"><el-select v-model="editForm.status" class="full-width"><el-option v-for="option in statusOptions" :key="option.value" v-bind="option" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="editSaving" @click="handleSaveEdit">保存修改</el-button></template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置用户密码" width="500px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon :title="`重置后「${resetTarget?.displayName || resetTarget?.username}」的旧 Token 将立即失效，下次登录必须修改密码。`" />
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top" class="dialog-form">
        <el-form-item label="当前管理员密码" prop="administratorPassword"><el-input v-model="resetForm.administratorPassword" type="password" autocomplete="current-password" show-password /></el-form-item>
        <el-form-item label="临时密码有效期"><el-input-number v-model="resetForm.temporaryPasswordValidHours" :min="1" :max="168" /><span class="unit">小时</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="resetVisible = false">取消</el-button><el-button type="danger" :loading="resetSaving" @click="handleResetPassword">确认重置</el-button></template>
    </el-dialog>

    <el-dialog v-model="credentialVisible" :title="credentialTitle" width="520px" :close-on-click-modal="false" :show-close="false" :close-on-press-escape="false">
      <el-alert type="warning" :closable="false" show-icon title="临时密码关闭窗口后无法再次查看，请通过安全方式交付。" />
      <div class="credential-panel">
        <div><span>用户名</span><strong>{{ credentialResult?.user.username }}</strong></div>
        <div><span>临时密码</span><code>{{ credentialResult?.temporaryPassword }}</code></div>
        <div><span>有效期至</span><strong>{{ formatDateTime(credentialResult?.temporaryPasswordExpiresAt) }}</strong></div>
      </div>
      <div class="credential-actions"><el-button @click="copyCredentialSummary">复制账号信息</el-button><el-button type="primary" @click="copyText(credentialResult?.temporaryPassword || '', '临时密码已复制')">复制临时密码</el-button></div>
      <el-checkbox v-model="credentialAcknowledged" class="acknowledge">我已通过安全方式保存或交付临时密码</el-checkbox>
      <template #footer><el-button type="primary" :disabled="!credentialAcknowledged" @click="closeCredentialResult">完成</el-button></template>
    </el-dialog>
  </MrrPageShell>
</template>

<style scoped>
.users-filter__keyword { flex: 1 1 320px; min-width: 260px; }
.users-filter__select { flex: 0 1 190px; width: 190px; }
.users-state { display: grid; min-height: 280px; padding: var(--mrr-space-5); place-items: center; }
.user-identity { display: flex; gap: 10px; align-items: center; min-width: 0; }
.user-avatar { display: grid; flex: 0 0 30px; width: 30px; height: 30px; font-size: 12px; font-weight: 700; color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 9%, var(--mrr-card)); border: 1px solid var(--mrr-border); border-radius: 50%; place-items: center; }
.row-actions { display: flex; gap: 8px; align-items: center; justify-content: flex-end; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.no-perm, .unit { margin-left: 8px; font-size: 12px; color: var(--mrr-muted-foreground); }
.dialog-form { display: grid; gap: 2px; margin-top: 18px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.readonly-field { width: 100%; min-height: var(--mrr-control-height); padding: 9px 12px; color: var(--mrr-muted-foreground); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-md); }
.full-width { width: 100%; }
.credential-panel { display: grid; gap: 10px; padding: 18px; margin-top: 18px; background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.credential-panel > div { display: grid; grid-template-columns: 90px 1fr; gap: 12px; align-items: center; }
.credential-panel span { font-size: 12px; color: var(--mrr-muted-foreground); }
.credential-panel code { padding: 8px 10px; font-size: 15px; font-weight: 700; word-break: break-all; background: var(--mrr-card); border-radius: var(--mrr-radius-md); }
.credential-actions { display: flex; gap: 10px; margin-top: 14px; }
.acknowledge { margin-top: 16px; }
@media (width <= 760px) { .users-filter__keyword, .users-filter__select { flex: 1 1 100%; width: 100%; } .form-grid { grid-template-columns: 1fr; } .credential-actions { flex-direction: column; } }
</style>
