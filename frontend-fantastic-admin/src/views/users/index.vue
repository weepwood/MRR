<script setup lang="ts">
/* eslint-disable antfu/consistent-list-newline, antfu/if-newline, curly, regexp/prefer-w, regexp/use-ignore-case, vue/singleline-html-element-content-newline */
import type { AuthRole, AuthUserUpdatePayload } from '@/api/types'
import type { CredentialAwareUser, UserCredentialResult } from '@/api/user-credential-types'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { KeyRound } from 'lucide-vue-next'
import apiUser from '@/api/modules/user'
import AppEmpty from '@/components/AppEmpty/index.vue'
import AppError from '@/components/AppError/index.vue'
import AppLoading from '@/components/AppLoading/index.vue'
import MrrDataTablePanel from '@/components/MrrDataTablePanel/index.vue'
import MrrDialog from '@/components/MrrDialog/index.vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrFilterBar from '@/components/MrrFilterBar/index.vue'
import MrrMetricCard from '@/components/MrrMetricCard/index.vue'
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import MrrStatusTag from '@/components/MrrStatusTag/index.vue'
import MrrTableActions from '@/components/MrrTableActions/index.vue'
import { actionDialog, ActionDialogCancelled } from '@/ui/components/ActionDialog'
import { copyText as copyToClipboard } from '@/utils/copy-text'
import { tableSequenceColumn } from '@/utils/table-sequence'

defineOptions({ name: 'UsersPage' })

type MetricTone = 'blue' | 'green' | 'danger' | 'violet'
type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

const { auth } = useAuth()
const userStore = useUserStore()
const canManage = computed(() => auth('user:manage'))
const loading = ref(false)
const hasLoaded = ref(false)
const hasMetricSnapshot = ref(false)
const dataLoadingAction = ref<'search' | 'reset' | null>(null)
const users = ref<CredentialAwareUser[]>([])

// 查询是读操作：新结果到达由 MrrDataTablePanel 的顶边进度条与行淡入反馈，
// 不再弹「查询成功」toast，也不设置人为的最低加载时长（原来的 320ms 下限单纯放慢高频操作）。
const error = ref('')
const roles = ref<AuthRole[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loadedRowOffset = ref(0)
const pageSizes = [10, 20, 50, 100]
const compactPagination = useMediaQuery('(max-width: 760px)')
const paginationLayout = computed(() => total.value > size.value
  ? 'total, prev, pager, next, jumper, sizes'
  : 'total, prev, pager, next, sizes')
const filters = reactive({ keyword: '', roleCode: '', status: '' })

const statusOptions = [
  { label: '待审核', value: 'pending' },
  { label: '启用', value: 'active' },
  { label: '已拒绝', value: 'rejected' },
  { label: '禁用', value: 'disabled' },
]
const editableStatusOptions = [
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
  temporaryPasswordValidHours: 24,
})

type RegistrationDialogMode = 'detail' | 'review' | 'reopen'
const registrationVisible = ref(false)
const registrationMode = ref<RegistrationDialogMode>('detail')
const registrationTarget = ref<CredentialAwareUser | null>(null)
const registrationTitle = computed(() => ({
  detail: '申请详情',
  review: '审核注册申请',
  reopen: '恢复注册申请',
})[registrationMode.value])

function hasApplication(row: CredentialAwareUser) {
  return Boolean(row.appliedAt || row.contactInfo?.trim() || row.applyRemark?.trim() || row.rejectReason?.trim() || row.reviewedAt || row.reviewedBy != null || ['pending', 'rejected'].includes(normalizeStatus(row.status)))
}

function applicationPreview(row: CredentialAwareUser) {
  return (row.applyRemark?.trim() || row.contactInfo?.trim() || row.rejectReason?.trim() || (row.appliedAt ? `申请于 ${formatDateTime(row.appliedAt)}` : '注册申请')).replace(/\s+/g, ' ')
}

function openApplication(row: CredentialAwareUser) {
  openRegistrationDialog(row, 'detail')
}

const approveSaving = ref(false)
const rejectSaving = ref(false)
const approveFormRef = ref()
const approveForm = reactive({ roleCode: '' })
const reopenSaving = ref(false)
const registrationBusy = computed(() => approveSaving.value || rejectSaving.value || reopenSaving.value)

function openRegistrationDialog(row: CredentialAwareUser, mode: RegistrationDialogMode) {
  if (registrationBusy.value || registrationVisible.value) return
  registrationTarget.value = row
  registrationMode.value = mode
  registrationVisible.value = true
}

function clearRegistrationDialog() {
  if (!registrationVisible.value) registrationTarget.value = null
}

const resetVisible = ref(false)
const resetSaving = ref(false)
const showResetLoading = ref(false)
const resetTarget = ref<CredentialAwareUser | null>(null)
const resetFormRef = ref()
const resetForm = reactive({ administratorPassword: '', temporaryPasswordValidHours: 24 })
const disableVisible = ref(false)
const disableSaving = ref(false)
const disableTarget = ref<CredentialAwareUser | null>(null)

const credentialVisible = ref(false)
const credentialAcknowledged = ref(false)
const credentialTitle = ref('')
const credentialResult = ref<UserCredentialResult | null>(null)

// Two stable action slots at every width, including fixed narrow-screen columns.
const userActionColumnWidth = 96

const approvableRoles = computed(() => {
  const nonAdminRoles = roles.value.filter(role => String(role.code || '').toUpperCase() !== 'ADMIN')
  return nonAdminRoles.length ? nonAdminRoles : roles.value
})

const summaryCards = computed(() => [
  {
    label: '待审核', value: users.value.filter(item => normalizeStatus(item.status) === 'pending').length,
    tone: 'violet' as MetricTone,
    icon: 'i-lucide:user-round-check',
  },
  {
    label: '已启用', value: users.value.filter(item => normalizeStatus(item.status) === 'active').length,
    tone: 'green' as MetricTone,
    icon: 'i-lucide:circle-check',
  },
  {
    label: '已拒绝', value: users.value.filter(item => normalizeStatus(item.status) === 'rejected').length,
    tone: 'danger' as MetricTone,
    icon: 'i-lucide:user-round-x',
  },
  {
    label: '已禁用', value: users.value.filter(item => normalizeStatus(item.status) === 'disabled').length,
    tone: 'blue' as MetricTone,
    icon: 'i-lucide:circle-stop',
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

const approveRules = {
  roleCode: [{ required: true, message: '请选择审核通过后授予的角色', trigger: 'change' }],
}

const resetRules = {
  administratorPassword: [{ required: true, message: '请输入当前管理员密码', trigger: 'blur' }],
}

watch(resetSaving, (isSaving, _, onCleanup) => {
  if (!isSaving) {
    showResetLoading.value = false
    return
  }

  const timer = setTimeout(() => {
    showResetLoading.value = true
  }, 180)
  onCleanup(() => clearTimeout(timer))
})

async function loadData(action?: 'search' | 'reset') {
  const requestedPage = page.value
  const requestedSize = size.value
  const showButtonLoading = hasLoaded.value && action !== undefined
  loading.value = true
  if (showButtonLoading) {
    dataLoadingAction.value = action!
  }
  try {
    error.value = ''
    const [usersRes, rolesRes] = await Promise.all([
      apiUser.getUsers({
        page: requestedPage,
        size: requestedSize,
        keyword: filters.keyword.trim(),
        roleCode: filters.roleCode,
        status: filters.status,
      }),
      apiUser.getRoles(),
    ])
    users.value = (Array.isArray(usersRes.data?.list) ? usersRes.data.list : []) as CredentialAwareUser[]
    loadedRowOffset.value = (requestedPage - 1) * requestedSize
    total.value = Number(usersRes.data?.total ?? users.value.length)
    roles.value = Array.isArray(rolesRes.data) ? rolesRes.data : []
    hasMetricSnapshot.value = true
  }
  catch (err: any) {
    hasMetricSnapshot.value = false
    users.value = []
    total.value = 0
    error.value = err?.message || '用户列表加载失败'
  }
  finally {
    loading.value = false
    hasLoaded.value = true
    if (showButtonLoading && dataLoadingAction.value === action) {
      dataLoadingAction.value = null
    }
  }
}

function normalizeStatus(status?: string) {
  return String(status || '').toLowerCase()
}

function isPending(row: CredentialAwareUser) {
  return normalizeStatus(row.status) === 'pending'
}

function isActive(row: CredentialAwareUser) {
  return normalizeStatus(row.status) === 'active'
}

function statusDisplay(status?: string): { label: string, tone: StatusTone } {
  switch (normalizeStatus(status)) {
    case 'pending': return { label: '待审核', tone: 'warning' }
    case 'active': return { label: '启用', tone: 'success' }
    case 'rejected': return { label: '已拒绝', tone: 'danger' }
    case 'disabled': return { label: '禁用', tone: 'neutral' }
    default: return { label: status || '未知', tone: 'neutral' }
  }
}

function handleSearch() {
  if (loading.value || dataLoadingAction.value !== null) return
  page.value = 1
  void loadData('search')
}

function handleSizeChange(nextSize: number) {
  if (loading.value || dataLoadingAction.value !== null) return
  page.value = 1
  size.value = nextSize
  void loadData()
}

function handlePageChange() {
  if (loading.value || dataLoadingAction.value !== null) return
  void loadData()
}

function resetFilters() {
  if (loading.value || dataLoadingAction.value !== null) return
  filters.keyword = ''
  filters.roleCode = ''
  filters.status = ''
  page.value = 1
  void loadData('reset')
}

function isSelf(rowId?: number | string) {
  return rowId != null && userStore.profile.id != null && Number(rowId) === Number(userStore.profile.id)
}

function userActions(row: CredentialAwareUser): MrrTableAction[] {
  const status = normalizeStatus(row.status)
  const pencil = (key: string, label: string): MrrTableAction => ({
    key, label, icon: 'i-lucide:pencil', tone: 'primary', placement: 'inline',
  })
  const detail: MrrTableAction = {
    key: 'application', label: '查看申请详情', icon: 'i-lucide:file-text', placement: 'overflow',
  }
  if (status === 'pending') {
    return [pencil('approve', '审核注册申请'), detail, {
      key: 'reject', label: '拒绝申请', icon: 'i-lucide:x', tone: 'danger', placement: 'overflow',
    }]
  }
  if (status === 'rejected') {
    return [pencil('reopen', '恢复待审核'), detail]
  }
  if (status === 'disabled') {
    return [pencil('edit', '编辑用户'), {
      key: 'enable', label: '恢复启用', icon: 'i-lucide:user-check', placement: 'overflow',
    }]
  }
  if (status === 'active') {
    const self = isSelf(row.id)
    return [
      pencil('edit', '编辑用户'),
      {
        key: 'reset-password', label: '重置密码', icon: 'i-lucide:key-round',
        tone: 'default', placement: 'overflow', disabled: self,
        disabledReason: self ? '请通过个人设置修改自己的密码' : undefined,
      },
      {
        key: 'disable', label: '禁用用户', icon: 'i-lucide:ban',
        tone: 'danger', placement: 'overflow', disabled: self,
        disabledReason: self ? '不能禁用当前登录账号' : undefined,
      },
    ]
  }
  return [{ ...pencil('unknown', '未知账号状态'), disabled: true }, {
    key: 'application', label: '查看申请详情', icon: 'i-lucide:file-text',
    placement: 'overflow', disabled: !hasApplication(row),
  }]
}

function handleUserAction(action: string, row: CredentialAwareUser) {
  if (!canManage.value || loading.value) return
  switch (action) {
    case 'approve': openApprove(row); break
    case 'reject': void handleReject(row); break
    case 'reopen': openReopen(row); break
    case 'application': openApplication(row); break
    case 'edit': openEdit(row); break
    case 'enable':
      if (normalizeStatus(row.status) === 'disabled') {
        openEdit(row)
        editForm.status = 'active'
      }
      break
    case 'reset-password': openReset(row); break
    case 'disable': openDisable(row); break
  }
}

function openReopen(row: CredentialAwareUser) {
  if (normalizeStatus(row.status) !== 'rejected' || !row.id || reopenSaving.value) return
  openRegistrationDialog(row, 'reopen')
}

async function handleReopen() {
  const row = registrationTarget.value
  if (!canManage.value || !registrationVisible.value || registrationMode.value !== 'reopen' || !row?.id || registrationBusy.value) return
  reopenSaving.value = true
  try {
    await apiUser.reopenRegistration(row.id)
    registrationVisible.value = false
    ElMessage.success('注册申请已恢复待审核')
    page.value = 1
    await loadData()
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '恢复申请失败')
  }
  finally {
    reopenSaving.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    username: '',
    displayName: '',
    roleCode: roles.value[0]?.code || '',
    temporaryPasswordValidHours: 24,
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
      status: 'active',
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

function openApprove(row: CredentialAwareUser) {
  if (!isPending(row) || !canManage.value || registrationVisible.value || registrationBusy.value) return
  approveForm.roleCode = approvableRoles.value[0]?.code || ''
  openRegistrationDialog(row, 'review')
}

async function handleApprove() {
  const row = registrationTarget.value
  if (registrationBusy.value || !canManage.value || !registrationVisible.value || registrationMode.value !== 'review' || !row?.id || !isPending(row)) return
  await approveFormRef.value?.validate()
  if (registrationBusy.value || !registrationVisible.value || registrationMode.value !== 'review' || registrationTarget.value !== row) return
  approveSaving.value = true
  try {
    const response = await apiUser.approveRegistration(row.id, { roleCode: approveForm.roleCode })
    const index = users.value.findIndex(item => item.id === row.id)
    if (index !== -1) users.value[index] = { ...users.value[index], ...response.data }
    registrationVisible.value = false
    ElMessage.success('注册申请已通过，用户现在可以登录')
    await loadData()
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '审核通过操作失败')
  }
  finally {
    approveSaving.value = false
  }
}

function rejectFromReview() {
  const row = registrationTarget.value
  if (!row || registrationMode.value !== 'review' || registrationBusy.value) return
  registrationVisible.value = false
  void handleReject(row)
}

async function handleReject(row: CredentialAwareUser) {
  if (!row.id || !isPending(row) || approveSaving.value || rejectSaving.value || !canManage.value) return
  rejectSaving.value = true
  try {
    const { value } = await actionDialog.prompt(
      `请输入拒绝「${row.displayName || row.username}」注册申请的原因。`,
      '拒绝注册申请',
      {
        confirmButtonText: '确认拒绝',
        confirmButtonType: 'danger',
        cancelButtonText: '取消',
        type: 'warning',
        inputType: 'textarea',
        inputPlaceholder: '拒绝原因将保存在审核记录中',
        inputValidator: value => Boolean(value?.trim()) || '拒绝原因不能为空',
      },
    )
    const response = await apiUser.rejectRegistration(row.id, { rejectReason: value.trim() })
    const index = users.value.findIndex(item => item.id === row.id)
    if (index !== -1) users.value[index] = { ...users.value[index], ...response.data }
    registrationVisible.value = false
    ElMessage.success('注册申请已拒绝')
    await loadData()
  }
  catch (err: any) {
    if (err instanceof ActionDialogCancelled) return
    ElMessage.error(err?.response?.data?.message || err?.message || '拒绝操作失败')
  }
  finally {
    rejectSaving.value = false
  }
}

function openEdit(row: CredentialAwareUser) {
  const status = normalizeStatus(row.status)
  if (!['active', 'disabled'].includes(status)) {
    ElMessage.warning('待审核或已拒绝账号不能通过编辑直接启用')
    return
  }
  editTarget.value = row
  editForm.displayName = row.displayName ?? ''
  editForm.roleCode = row.roleCode ?? ''
  editForm.status = status
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
  if (!isActive(row)) return
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
  await copyToClipboard(value, { success: successMessage })
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

function openDisable(row: CredentialAwareUser) {
  if (disableVisible.value || disableSaving.value || !row.id || isSelf(row.id) || !isActive(row)) return
  disableTarget.value = row
  disableVisible.value = true
}

async function handleDisable() {
  const row = disableTarget.value
  if (!disableVisible.value || disableSaving.value || !row?.id) return
  disableSaving.value = true
  try {
    await apiUser.disableUser(row.id)
    const index = users.value.findIndex(item => item.id === row.id)
    if (index !== -1) users.value[index] = { ...users.value[index], status: 'disabled' }
    disableVisible.value = false
    ElMessage.success('已禁用用户')
  }
  catch (err: any) {
    ElMessage.error(err?.response?.data?.message || err?.message || '禁用操作失败')
  }
  finally {
    disableSaving.value = false
  }
}

function credentialStatus(row: CredentialAwareUser) {
  const status = normalizeStatus(row.status)
  if (status === 'pending') return { label: '等待审核', tone: 'warning' as const }
  if (status === 'rejected') return { label: '申请未通过', tone: 'danger' as const }
  if (status === 'disabled') return { label: '账号已禁用', tone: 'neutral' as const }
  if (!row.mustChangePassword) return { label: '正常', tone: 'success' as const }
  const expiresAt = row.temporaryPasswordExpiresAt ? new Date(row.temporaryPasswordExpiresAt) : null
  if (expiresAt && !Number.isNaN(expiresAt.getTime()) && expiresAt.getTime() < Date.now()) {
    return { label: '临时密码已过期', tone: 'danger' as const }
  }
  return { label: row.passwordResetAt ? '密码已重置' : '待修改初始密码', tone: 'warning' as const }
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
      description="管理系统账号、注册申请审核、角色、凭据状态、启停状态与最近登录信息。下方状态按当前页统计。"
    />

    <section class="mrr-metric-grid mrr-metric-grid--four" aria-label="当前页账号状态" :aria-busy="loading" aria-live="polite">
      <MrrMetricCard v-for="item in summaryCards" :key="item.label" v-bind="item" :value="hasMetricSnapshot ? item.value : '—'" />
    </section>

    <MrrDataTablePanel class="users-table-panel" role="region" aria-label="用户列表" :loading="loading" :result-key="users">
      <template #filters>
        <div class="users-toolbar">
          <MrrFilterBar class="users-filter-bar" embedded :focus-ring="false">
            <div class="users-filter__field users-filter__keyword mrr-filter-field--keyword">
              <el-input id="users-filter-keyword" v-model="filters.keyword" aria-label="关键词" clearable placeholder="搜索用户名、显示名、联系方式或角色…" @keyup.enter="handleSearch">
                <template #prefix><FaIcon name="i-lucide:search" aria-hidden="true" /></template>
              </el-input>
            </div>
            <div class="users-filter__field users-filter__select">
              <el-select id="users-filter-role" v-model="filters.roleCode" class="mrr-single-select" aria-label="角色" clearable placeholder="全部角色">
                <el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" />
              </el-select>
            </div>
            <div class="users-filter__field users-filter__select">
              <el-select id="users-filter-status" v-model="filters.status" class="mrr-single-select" aria-label="账号状态" clearable placeholder="全部状态">
                <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
              </el-select>
            </div>
            <template #actions>
              <MrrFilterActionButton action="search" feedback="request" :pending="dataLoadingAction === 'search'" :busy="loading || dataLoadingAction !== null" @click="handleSearch">查询</MrrFilterActionButton>
              <MrrFilterActionButton action="reset" feedback="request" :pending="dataLoadingAction === 'reset'" :busy="loading || dataLoadingAction !== null" @click="resetFilters">重置</MrrFilterActionButton>
            </template>
            <template v-if="canManage" #business>
              <el-button type="primary" class="users-toolbar__create" @click="openCreate">
                <FaIcon name="i-lucide:user-round-plus" aria-hidden="true" />创建用户
              </el-button>
            </template>
          </MrrFilterBar>
        </div>
      </template>

      <div v-if="loading && !hasLoaded" class="users-state"><AppLoading type="table" :rows="8" /></div>
      <div v-else-if="error" class="users-state"><AppError :message="error" @retry="loadData" /></div>
      <div v-else-if="!users.length" class="users-state"><AppEmpty description="暂无符合条件的用户记录" /></div>

      <el-table v-else :data="users" row-key="id" stripe>
        <el-table-column v-bind="tableSequenceColumn(loadedRowOffset, users.length)" />
        <el-table-column prop="username" label="用户名" width="168" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="user-identity">
              <span class="user-avatar" aria-hidden="true">{{ String(row.displayName || row.username || '?').slice(0, 1) }}</span>
              <span class="user-identity__username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名称" width="132" show-overflow-tooltip />
        <el-table-column label="申请信息" min-width="240" class-name="user-application-column">
          <template #default="{ row }">
            <el-button
              v-if="hasApplication(row)"
              text
              class="application-preview"
              :aria-label="`查看 ${row.username || row.displayName || '用户'} 的申请详情`"
              aria-haspopup="dialog"
              @click.stop="openApplication(row)"
            >
              <span class="application-preview__text">{{ applicationPreview(row) }}</span>
              <FaIcon name="i-lucide:arrow-up-right" class="application-preview__icon" aria-hidden="true" />
            </el-button>
            <span v-else class="user-source">管理员创建</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="96" show-overflow-tooltip><template #default="{ row }">{{ row.roleName || row.roleCode }}</template></el-table-column>
        <el-table-column label="凭据状态" width="152" class-name="mrr-table-status-column"><template #default="{ row }"><MrrStatusTag :label="credentialStatus(row).label" :tone="credentialStatus(row).tone" /></template></el-table-column>
        <el-table-column label="账号状态" width="96" class-name="user-status-column mrr-table-status-column"><template #default="{ row }"><MrrStatusTag :status="row.status" :label="statusDisplay(row.status).label" :tone="statusDisplay(row.status).tone" /></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="176" show-overflow-tooltip><template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column
          v-if="canManage"
          label="操作"
          :width="userActionColumnWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <MrrTableActions
              appearance="quiet"
              :actions="userActions(row)"
              :max-inline="1"
              @select="handleUserAction($event, row)"
            />
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <el-pagination v-model:current-page="page" v-model:page-size="size" class="users-pagination" :total="total" :page-sizes="pageSizes" :pager-count="compactPagination ? 5 : 7" :layout="paginationLayout" :disabled="loading" @size-change="handleSizeChange" @current-change="handlePageChange" />
      </template>
    </MrrDataTablePanel>

    <MrrDialog
      v-model="registrationVisible"
      :title="registrationTitle"
      :close-on-click-overlay="registrationMode === 'detail' && !registrationBusy"
      :close-on-press-escape="!registrationBusy"
      :closable="!registrationBusy"
      @closed="clearRegistrationDialog"
    >
      <div v-if="registrationTarget" class="registration-dialog-content">
        <el-alert
          v-if="registrationMode === 'review'"
          type="warning"
          :closable="false"
          show-icon
          title="审核通过后账号立即启用，用户可使用注册时设置的密码登录。"
        />
        <el-alert
          v-else-if="registrationMode === 'reopen'"
          type="warning"
          :closable="false"
          show-icon
          title="恢复后账号重新进入待审核，仍不能登录，需再次审核通过。"
        />
        <dl class="review-summary">
          <div><dt>用户名</dt><dd><strong>{{ registrationTarget.username }}</strong></dd></div>
          <div><dt>显示名称</dt><dd>{{ registrationTarget.displayName || '未填写' }}</dd></div>
          <div><dt>账号状态</dt><dd><MrrStatusTag :label="statusDisplay(registrationTarget.status).label" :tone="statusDisplay(registrationTarget.status).tone" /></dd></div>
          <div><dt>联系方式</dt><dd>{{ registrationTarget.contactInfo || '未填写' }}</dd></div>
          <div><dt>申请时间</dt><dd>{{ formatDateTime(registrationTarget.appliedAt) }}</dd></div>
          <div v-if="registrationTarget.reviewedAt"><dt>审核时间</dt><dd>{{ formatDateTime(registrationTarget.reviewedAt) }}</dd></div>
          <div class="application-detail__section">
            <dt>申请说明</dt>
            <dd><p>{{ registrationTarget.applyRemark || '未填写' }}</p></dd>
          </div>
          <div v-if="registrationTarget.rejectReason" class="application-detail__section--rejected">
            <dt>{{ registrationMode === 'reopen' ? '原拒绝原因' : isPending(registrationTarget) ? '上次拒绝原因' : '拒绝原因' }}</dt>
            <dd><p>{{ registrationTarget.rejectReason }}</p></dd>
          </div>
        </dl>
        <el-form v-if="registrationMode === 'review'" ref="approveFormRef" :model="approveForm" :rules="approveRules" label-position="top" class="registration-review-form" :disabled="registrationBusy">
          <el-form-item label="授予角色" prop="roleCode">
            <el-select v-model="approveForm.roleCode" class="full-width">
              <el-option v-for="role in approvableRoles" :key="role.code" :label="role.name || role.code" :value="role.code!" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="registrationBusy" :auto-insert-space="false" @click="registrationVisible = false">{{ registrationMode === 'detail' ? '关闭' : '取消' }}</el-button>
        <template v-if="registrationMode === 'review'">
          <el-button type="danger" plain :disabled="registrationBusy" @click="rejectFromReview">拒绝申请</el-button>
          <el-button type="primary" :loading="approveSaving" :disabled="registrationBusy" @click="handleApprove">确认通过</el-button>
        </template>
        <el-button v-else-if="registrationMode === 'reopen'" type="primary" :loading="reopenSaving" :disabled="registrationBusy" @click="handleReopen">恢复待审核</el-button>
      </template>
    </MrrDialog>

    <MrrDialog v-model="createVisible" title="创建用户" width="520px" :close-on-click-overlay="false">
      <el-alert type="info" :closable="false" show-icon title="管理员创建的用户默认启用。系统将生成一次性临时密码，首次登录后必须修改。" />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top" class="dialog-form mrr-edit-form mrr-edit-form--paired">
        <el-form-item label="用户名" prop="username"><el-input v-model="createForm.username" autocomplete="off" placeholder="例如 zhangsan" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="createForm.displayName" placeholder="例如 张三" /></el-form-item>
        <el-form-item label="角色" prop="roleCode"><el-select v-model="createForm.roleCode" class="full-width"><el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" /></el-select></el-form-item>
        <el-form-item label="临时密码有效期"><el-input-number v-model="createForm.temporaryPasswordValidHours" :min="1" :max="168" /><span class="mrr-edit-form__unit">小时</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="createSaving" @click="handleCreate">创建用户</el-button></template>
    </MrrDialog>

    <MrrDialog v-model="editVisible" title="编辑用户" width="480px" :close-on-click-overlay="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top" class="dialog-form mrr-edit-form mrr-edit-form--paired">
        <el-form-item label="用户名"><div class="readonly-field"><span>{{ editTarget?.username }}</span></div></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="editForm.displayName" /></el-form-item>
        <el-form-item label="角色" prop="roleCode"><el-select v-model="editForm.roleCode" class="full-width"><el-option v-for="role in roles" :key="role.code" :label="role.name || role.code" :value="role.code!" /></el-select></el-form-item>
        <el-form-item label="状态" prop="status"><el-select v-model="editForm.status" class="full-width"><el-option v-for="option in editableStatusOptions" :key="option.value" v-bind="option" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="editSaving" @click="handleSaveEdit">保存修改</el-button></template>
    </MrrDialog>

    <MrrDialog v-model="resetVisible" title="重置用户密码" width="500px" :close-on-click-overlay="false">
      <el-alert type="warning" :closable="false" show-icon :title="`重置后「${resetTarget?.displayName || resetTarget?.username}」的旧 Token 将立即失效，下次登录必须修改密码。`" />
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top" class="dialog-form mrr-edit-form">
        <el-form-item label="当前管理员密码" prop="administratorPassword"><el-input v-model="resetForm.administratorPassword" type="password" autocomplete="current-password" show-password /></el-form-item>
        <el-form-item label="临时密码有效期"><el-input-number v-model="resetForm.temporaryPasswordValidHours" :min="1" :max="168" /><span class="mrr-edit-form__unit">小时</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="resetVisible = false">取消</el-button><el-button type="danger" :icon="KeyRound" :loading="showResetLoading" :disabled="resetSaving" @click="handleResetPassword">确认重置</el-button></template>
    </MrrDialog>

    <MrrDialog
      v-model="disableVisible"
      title="禁用用户"
      width="min(480px, calc(100vw - 32px))"
      :close-on-click-overlay="!disableSaving"
      :close-on-press-escape="!disableSaving"
      :closable="!disableSaving"
      @closed="disableTarget = null"
    >
      <el-alert
        v-if="disableTarget"
        type="warning"
        :closable="false"
        show-icon
        :title="`确认禁用用户「${disableTarget.displayName || disableTarget.username}」吗？禁用后该用户将无法登录。`"
      />
      <template #footer>
        <el-button :disabled="disableSaving" @click="disableVisible = false">取消</el-button>
        <el-button type="danger" :loading="disableSaving" @click="handleDisable">确认禁用</el-button>
      </template>
    </MrrDialog>

    <MrrDialog v-model="credentialVisible" :title="credentialTitle" width="520px" :close-on-click-overlay="false" :closable="false" :close-on-press-escape="false">
      <el-alert type="warning" :closable="false" show-icon title="临时密码关闭窗口后无法再次查看，请通过安全方式交付。" />
      <div class="credential-panel">
        <div><span>用户名</span><strong>{{ credentialResult?.user.username }}</strong></div>
        <div><span>临时密码</span><code>{{ credentialResult?.temporaryPassword }}</code></div>
        <div><span>有效期至</span><strong>{{ formatDateTime(credentialResult?.temporaryPasswordExpiresAt) }}</strong></div>
      </div>
      <div class="credential-actions"><el-button @click="copyCredentialSummary">复制账号信息</el-button><el-button type="primary" @click="copyText(credentialResult?.temporaryPassword || '', '临时密码已复制')">复制临时密码</el-button></div>
      <el-checkbox v-model="credentialAcknowledged" class="acknowledge">我已通过安全方式保存或交付临时密码</el-checkbox>
      <template #footer><el-button type="primary" :disabled="!credentialAcknowledged" @click="closeCredentialResult">完成</el-button></template>
    </MrrDialog>
  </MrrPageShell>
</template>

<style scoped>
/* stylelint-disable @stylistic/block-closing-brace-newline-after, @stylistic/selector-list-comma-newline-after, at-rule-empty-line-before, order/properties-order */

.users-state { display: grid; min-height: 280px; padding: var(--mrr-space-5); place-items: center; }
.user-identity { display: flex; gap: var(--mrr-space-2); align-items: center; min-width: 0; }
.user-avatar { display: grid; flex: 0 0 28px; width: 28px; height: 28px; font-size: var(--mrr-font-size-xs); font-weight: 600; color: var(--mrr-primary); background: var(--mrr-tone-primary-soft); border: 1px solid var(--mrr-border); border-radius: 50%; place-items: center; }
.user-identity__username { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-source { color: var(--mrr-muted-foreground); }

/* One line for scanning; the read-only dialog preserves the original text. */
.application-preview.el-button { justify-content: flex-start; width: 100%; max-width: 100%; min-width: 0; height: var(--mrr-control-height); padding: 0; color: var(--mrr-muted-foreground); text-align: left; border: 0; box-shadow: none; }
.application-preview.el-button :deep(> span) { display: flex; gap: var(--mrr-space-2); align-items: center; justify-content: flex-start; width: 100%; min-width: 0; }
.application-preview__text { min-width: 0; overflow: hidden; font-size: var(--mrr-font-size-sm); text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.application-preview__icon { flex: none; font-size: 14px; }
.application-preview.el-button:not(.is-disabled):hover { color: var(--mrr-primary); background: transparent; }
.application-preview.el-button:focus-visible { outline: 2px solid var(--mrr-primary); outline-offset: -2px; }

/* Shared registration summary: review is the reference layout in every mode. */
.registration-dialog-content { display: grid; min-width: 0; gap: var(--mrr-space-4); }
.registration-review-form :deep(.el-form-item) { margin-bottom: 0; }
.dialog-form { margin-top: var(--mrr-space-4); }
.dialog-form:first-child { margin-top: 0; }
.readonly-field { display: flex; align-items: center; width: 100%; min-height: var(--mrr-control-height); padding: var(--mrr-space-1) var(--mrr-space-3); overflow-wrap: anywhere; color: var(--mrr-muted-foreground); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-md); }
.full-width { width: 100%; }
.credential-panel { display: grid; gap: var(--mrr-space-3); padding: var(--mrr-space-4); margin-top: var(--mrr-space-4); background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-lg); }
.credential-panel > div { display: grid; grid-template-columns: 90px 1fr; gap: var(--mrr-space-3); align-items: start; }
.credential-panel span { font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }
.review-summary { display: grid; gap: var(--mrr-space-3); min-width: 0; padding: var(--mrr-space-4); margin: 0; background: var(--mrr-muted); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-md); }
.review-summary > div { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: var(--mrr-space-3); align-items: baseline; min-width: 0; }
.review-summary dt { font-size: var(--mrr-font-size-sm); color: var(--mrr-muted-foreground); }
.review-summary dd, .review-summary p { min-width: 0; margin: 0; font-size: var(--mrr-font-size-md); line-height: 1.6; overflow-wrap: anywhere; white-space: pre-wrap; }
.review-summary strong { font-weight: 600; }
.review-summary .application-detail__section--rejected dt { color: var(--mrr-destructive); }
@media (max-width: 480px) {
  .review-summary { padding: var(--mrr-space-3); }
  .review-summary > div { grid-template-columns: 76px minmax(0, 1fr); gap: var(--mrr-space-2); }
}
.credential-panel code { padding: var(--mrr-space-2) var(--mrr-space-3); font-family: var(--mrr-font-mono); font-size: var(--mrr-font-size-md); letter-spacing: 0.02em; font-weight: 700; word-break: break-all; background: var(--mrr-card); border-radius: var(--mrr-radius-md); }
.credential-actions { display: flex; gap: var(--mrr-space-2); margin-top: var(--mrr-space-4); }
.acknowledge { margin-top: var(--mrr-space-4); }
@media (max-width: 760px) {
  .credential-actions { flex-direction: column; }
}
</style>
