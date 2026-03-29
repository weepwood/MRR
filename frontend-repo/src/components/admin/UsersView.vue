<template>
  <div class="users-view pmr-page">
    <el-card class="pmr-panel panel-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <h3 class="pmr-panel-title">用户管理</h3>
            <p class="pmr-panel-subtitle">查看账号、角色、权限与最近登录状态。</p>
          </div>

          <div class="header-actions pmr-actions-row">
            <el-input
              v-model="searchTerm"
              class="search-input"
              clearable
              placeholder="搜索用户名、姓名或角色"
            />
            <el-select v-model="statusFilter" class="filter-select" clearable placeholder="状态">
              <el-option label="全部" value="all" />
              <el-option label="启用" value="active" />
              <el-option label="禁用" value="disabled" />
            </el-select>
            <el-button type="primary" :loading="loading" @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="error" :title="error" type="error" show-icon class="mb-16" />

      <section class="summary-grid">
        <article v-for="item in summaryCards" :key="item.label" class="summary-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </article>
      </section>

      <section class="role-strip" v-if="roles.length">
        <article v-for="role in roles" :key="role.code" class="role-pill">
          <div class="role-pill-top">
            <strong>{{ role.name }}</strong>
            <el-tag :type="getRoleType(role.code)">{{ role.code }}</el-tag>
          </div>
          <p>{{ role.description || '暂无说明' }}</p>
          <small>{{ getPermissionList(role).length }} 项权限</small>
        </article>
      </section>

      <el-alert
        v-if="!loading && !error && users.length && !hasPermissionMetadata"
        class="mb-16"
        type="info"
        show-icon
        title="后端暂未返回完整角色权限元数据，当前仅展示角色代码。"
      />

      <div class="table-shell">
        <el-table :data="filteredUsers" v-loading="loading" height="580" stripe>
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="displayName" label="姓名" min-width="120" />
          <el-table-column prop="roleName" label="角色" min-width="120">
            <template #default="{ row }">
              <el-tag :type="getRoleType(row.roleCode)">{{ row.roleName || row.roleCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权限" min-width="320">
            <template #default="{ row }">
              <div v-if="getPermissionList(row).length" class="permission-tags">
                <el-tag
                  v-for="permission in getPermissionList(row)"
                  :key="permission"
                  size="small"
                  type="info"
                >
                  {{ permission }}
                </el-tag>
              </div>
              <el-tag v-else type="info" effect="plain" size="small">权限数据不可用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
                {{ row.status === 'active' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最近登录" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.lastLoginAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" @click="openEditor(row)">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button
                  size="small"
                  :type="row.status === 'active' ? 'warning' : 'success'"
                  @click="toggleStatus(row)"
                >
                  <el-icon><SwitchButton /></el-icon>
                  {{ row.status === 'active' ? '禁用' : '启用' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="editorVisible" title="编辑用户" width="520px">
      <el-form :model="editForm" label-width="96px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.displayName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleCode" placeholder="请选择角色">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="`${role.name} (${role.code})`"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" placeholder="请选择状态">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { disableAuthUser, getAuthRoles, getAuthUsers, updateAuthUser } from '@/utils/api'

const users = ref([])
const roles = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const searchTerm = ref('')
const statusFilter = ref('all')
const editorVisible = ref(false)

const editForm = reactive({
  id: null,
  username: '',
  displayName: '',
  roleCode: '',
  status: 'active'
})

const roleMap = computed(
  () => new Map(roles.value.map((role) => [String(role.code || '').toUpperCase(), role]))
)

const loadData = async () => {
  loading.value = true
  error.value = ''
  try {
    const [usersResponse, rolesResponse] = await Promise.all([getAuthUsers(), getAuthRoles()])
    const userPayload = usersResponse?.data
    const rolePayload = rolesResponse?.data

    users.value = Array.isArray(userPayload?.data) ? userPayload.data : []
    roles.value = Array.isArray(rolePayload?.data) ? rolePayload.data : []
  } catch (err) {
    console.error('加载用户管理数据失败:', err)
    error.value = err.response?.data?.message || '加载用户管理数据失败'
  } finally {
    loading.value = false
  }
}

const filteredUsers = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  return users.value.filter((user) => {
    const matchesSearch =
      !keyword ||
      [user.username, user.displayName, user.roleName, user.roleCode]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
    const matchesStatus = statusFilter.value === 'all' || !statusFilter.value || user.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const summaryCards = computed(() => {
  const activeCount = users.value.filter((user) => user.status === 'active').length
  const adminCount = users.value.filter((user) => String(user.roleCode || '').toUpperCase() === 'ADMIN').length
  return [
    { label: '账号总数', value: users.value.length, note: '当前可管理账号' },
    { label: '启用账号', value: activeCount, note: '状态为 active 的账号' },
    { label: '管理员', value: adminCount, note: '拥有后台管理权限' },
    { label: '角色数量', value: roles.value.length, note: '系统已定义角色' }
  ]
})

const getRoleType = (roleCode) => {
  const code = String(roleCode || '').toUpperCase()
  const map = {
    ADMIN: 'danger',
    DOCTOR: 'primary',
    NURSE: 'success'
  }
  return map[code] || 'info'
}

const getPermissionList = (item) => {
  const rawPermissions =
    item?.permissions ||
    item?.permissionsCsv ||
    item?.rolePermissions ||
    roleMap.value.get(String(item?.roleCode || '').toUpperCase())?.permissions ||
    roleMap.value.get(String(item?.roleCode || '').toUpperCase())?.permissionsCsv ||
    ''

  if (Array.isArray(rawPermissions)) {
    return rawPermissions
  }

  if (typeof rawPermissions === 'string') {
    return rawPermissions
      .split(',')
      .map((permission) => permission.trim())
      .filter(Boolean)
  }

  return []
}

const hasPermissionMetadata = computed(() =>
  users.value.some((user) => getPermissionList(user).length > 0) ||
  roles.value.some((role) => getPermissionList(role).length > 0)
)

const formatDateTime = (value) => {
  if (!value) {
    return '未登录'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

const openEditor = (row) => {
  editForm.id = row.id
  editForm.username = row.username || ''
  editForm.displayName = row.displayName || ''
  editForm.roleCode = row.roleCode || ''
  editForm.status = row.status || 'active'
  editorVisible.value = true
}

const saveUser = async () => {
  if (!editForm.id) {
    return
  }

  saving.value = true
  try {
    const response = await updateAuthUser(editForm.id, {
      displayName: editForm.displayName,
      roleCode: editForm.roleCode,
      status: editForm.status
    })

    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || '保存失败')
    }

    ElMessage.success('用户信息已更新')
    editorVisible.value = false
    await loadData()
  } catch (err) {
    console.error('保存用户失败:', err)
    ElMessage.error(err.response?.data?.message || err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row) => {
  const nextStatus = row.status === 'active' ? 'disabled' : 'active'
  const title = row.status === 'active' ? '确认禁用该账号吗？' : '确认启用该账号吗？'

  try {
    await ElMessageBox.confirm(title, '账号状态变更', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    if (nextStatus === 'disabled') {
      await disableAuthUser(row.id)
    } else {
      await updateAuthUser(row.id, {
        displayName: row.displayName,
        roleCode: row.roleCode,
        status: nextStatus
      })
    }

    ElMessage.success('账号状态已更新')
    await loadData()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      console.error('切换账号状态失败:', err)
      ElMessage.error(err.response?.data?.message || '状态更新失败')
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.users-view {
  min-height: 100%;
}

.panel-card {
  padding-bottom: 12px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-actions {
  align-items: center;
}

.search-input,
.filter-select {
  width: 220px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
  border: 1px solid #e6edf7;
}

.summary-card span {
  display: block;
  font-size: 12px;
  color: var(--pmr-color-text-secondary);
}

.summary-card strong {
  display: block;
  margin-top: 6px;
  font-size: 28px;
  line-height: 1;
  color: var(--pmr-color-text-primary);
}

.summary-card small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #7a889f;
}

.role-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.role-pill {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e6edf7;
}

.role-pill-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.role-pill p {
  margin: 8px 0 0;
  color: var(--pmr-color-text-secondary);
  font-size: 13px;
}

.role-pill small {
  display: block;
  margin-top: 8px;
  color: #7a889f;
  font-size: 12px;
}

.table-shell {
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid #e6edf7;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .summary-grid,
  .role-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .role-strip {
    grid-template-columns: 1fr;
  }

  .search-input,
  .filter-select {
    width: 100%;
  }
}
</style>
