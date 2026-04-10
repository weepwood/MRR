<script setup lang="ts">
import type { AuthRole, AuthUser, AuthUserUpdatePayload } from '@/api/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import apiUser from '@/api/modules/user'

defineOptions({ name: 'UsersPage' })

const { auth } = useAuth()
const canManage = computed(() => auth('user:manage'))

const loading = ref(false)
const users = ref<AuthUser[]>([])
const roles = ref<AuthRole[]>([])

// 编辑对话框
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
  roleCode: [{ required: true, message: '角色不能为空', trigger: 'change' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }],
}

const statusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' },
]

async function loadData() {
  loading.value = true
  try {
    const [usersRes, rolesRes] = await Promise.all([
      apiUser.getUsers(),
      apiUser.getRoles(),
    ])
    users.value = Array.isArray((usersRes as any).data) ? (usersRes as any).data : []
    roles.value = Array.isArray((rolesRes as any).data) ? (rolesRes as any).data : []
  }
  catch (error: any) {
    ElMessage.error(error?.message || '用户列表加载失败')
  }
  finally {
    loading.value = false
  }
}

function openEdit(row: AuthUser) {
  editTarget.value = row
  editForm.displayName = row.displayName ?? ''
  editForm.roleCode = row.roleCode ?? ''
  editForm.status = row.status ?? 'ACTIVE'
  editVisible.value = true
}

async function handleSaveEdit() {
  if (!editFormRef.value) { return }
  await editFormRef.value.validate()
  if (!editTarget.value?.id) { return }

  editSaving.value = true
  try {
    const res = await apiUser.updateUser(editTarget.value.id, {
      displayName: editForm.displayName || undefined,
      roleCode: editForm.roleCode,
      status: editForm.status,
    })
    const updated: AuthUser = (res as any).data || {}
    const idx = users.value.findIndex(u => u.id === editTarget.value!.id)
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

async function handleDisable(row: AuthUser) {
  await ElMessageBox.confirm(
    `确认要禁用用户「${row.displayName || row.username}」吗？禁用后该用户将无法登录。`,
    '禁用确认',
    { confirmButtonText: '确认禁用', cancelButtonText: '取消', type: 'warning' },
  )
  try {
    await apiUser.disableUser(row.id!)
    const idx = users.value.findIndex(u => u.id === row.id)
    if (idx !== -1) {
      users.value[idx] = { ...users.value[idx], status: 'DISABLED' }
    }
    ElMessage.success('已禁用用户')
  }
  catch (error: any) {
    if ((error as any)?.toString?.()?.includes('cancel')) { return }
    ElMessage.error(error?.message || '禁用操作失败')
  }
}

function statusTagType(status: string | undefined) {
  if (status === 'ACTIVE') { return 'success' }
  if (status === 'DISABLED') { return 'danger' }
  return 'info'
}

function statusLabel(status: string | undefined) {
  return status === 'ACTIVE' ? '启用' : status === 'DISABLED' ? '禁用' : (status || '-')
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
          管理系统用户账户、角色分配与权限控制。
        </p>
      </div>
      <el-button :loading="loading" @click="loadData">
        刷新列表
      </el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="displayName" label="显示名称" min-width="130">
          <template #default="{ row }">
            {{ row.displayName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色" min-width="120">
          <template #default="{ row }">
            {{ row.roleName || row.roleCode || '-' }}
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
            {{ row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString('zh-CN') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage">
              <el-button size="small" type="primary" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="row.status === 'DISABLED'"
                @click="handleDisable(row)"
              >
                禁用
              </el-button>
            </template>
            <span v-else class="no-perm">无权限</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
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
          <el-input v-model="editForm.displayName" placeholder="可不填，默认使用用户名" clearable />
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
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.readonly-field {
  font-size: 14px;
  color: #64748b;
}

.no-perm {
  font-size: 12px;
  color: #94a3b8;
}
</style>
