<template>
  <div class="users-page pmr-page">
    <el-card class="pmr-panel" shadow="never">
      <template #header>
        <div class="header-row">
          <div>
            <h2 class="pmr-page-title">用户管理</h2>
            <p class="pmr-page-subtitle">查看账号、角色、权限与最近登录时间</p>
          </div>
          <div class="toolbar">
            <el-input v-model="keyword" clearable placeholder="Search users" class="search-input" />
            <el-select v-model="statusFilter" clearable placeholder="Status" class="status-select">
              <el-option label="All" value="" />
              <el-option label="Active" value="active" />
              <el-option label="Disabled" value="disabled" />
            </el-select>
            <el-button :loading="loading" type="primary" @click="loadData">
              <el-icon><Refresh /></el-icon>
              Refresh
            </el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="error" type="error" :title="error" show-icon class="mb-16" />

      <section class="summary-grid">
        <article class="summary-card">
          <span>Total users</span>
          <strong>{{ users.length }}</strong>
          <small>Current managed accounts</small>
        </article>
        <article class="summary-card">
          <span>Active users</span>
          <strong>{{ activeCount }}</strong>
          <small>Users with active status</small>
        </article>
        <article class="summary-card">
          <span>Roles</span>
          <strong>{{ roles.length }}</strong>
          <small>Defined role catalog</small>
        </article>
      </section>

      <div class="table-shell">
        <el-table :data="filteredUsers" v-loading="loading" height="620" stripe>
          <el-table-column prop="username" label="Username" min-width="140" />
          <el-table-column prop="displayName" label="Display Name" min-width="160" />
          <el-table-column prop="roleName" label="Role" min-width="140">
            <template #default="{ row }">
              <el-tag :type="getRoleType(row.roleCode)">
                {{ row.roleName || row.roleCode || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Permissions" min-width="300">
            <template #default="{ row }">
              <div class="permission-tags">
                <el-tag v-for="permission in row.permissions || []" :key="permission" size="small" type="info">
                  {{ permission }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
                {{ row.status === 'active' ? 'Active' : 'Disabled' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="Last Login" min-width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.lastLoginAt) }}
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="200" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" @click="openEditor(row)">
                  <el-icon><Edit /></el-icon>
                  Edit
                </el-button>
                <el-button
                  size="small"
                  :type="row.status === 'active' ? 'warning' : 'success'"
                  @click="toggleStatus(row)"
                >
                  <el-icon><SwitchButton /></el-icon>
                  {{ row.status === 'active' ? 'Disable' : 'Enable' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="editorVisible" title="Edit user" width="520px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="Username">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="Display name">
          <el-input v-model="editForm.displayName" placeholder="Enter display name" />
        </el-form-item>
        <el-form-item label="Role">
          <el-select v-model="editForm.roleCode" placeholder="Select role">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="role.name || role.code"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="editForm.status" placeholder="Select status">
            <el-option label="Active" value="active" />
            <el-option label="Disabled" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { disableAuthUser, getAuthRoles, getAuthUsers, updateAuthUser } from '@/services/api/index.js'

const users = ref([])
const roles = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const editorVisible = ref(false)

const editForm = reactive({
  id: null,
  username: '',
  displayName: '',
  roleCode: '',
  status: 'active'
})

const roleMap = computed(() => new Map(roles.value.map((role) => [role.code, role])))

const activeCount = computed(() => users.value.filter((user) => user.status === 'active').length)

const filteredUsers = computed(() => {
  const keywordValue = keyword.value.trim().toLowerCase()
  return users.value.filter((user) => {
    const matchesKeyword =
      !keywordValue ||
      [user.username, user.displayName, user.roleCode, user.roleName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keywordValue))
    const matchesStatus = !statusFilter.value || user.status === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

function getRoleType(roleCode) {
  const code = String(roleCode || '').toUpperCase()
  const map = {
    ADMIN: 'danger',
    DOCTOR: 'primary',
    VIEWER: 'info'
  }
  return map[code] || 'success'
}

function getPermissionList(item) {
  const raw = item?.permissions || item?.permissionsCsv || roleMap.value.get(item?.roleCode)?.permissions || ''
  if (Array.isArray(raw)) {
    return raw
  }
  if (typeof raw === 'string') {
    return raw
      .split(',')
      .map((permission) => permission.trim())
      .filter(Boolean)
  }
  return []
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

function openEditor(row) {
  editForm.id = row.id
  editForm.username = row.username || ''
  editForm.displayName = row.displayName || ''
  editForm.roleCode = row.roleCode || ''
  editForm.status = row.status || 'active'
  editorVisible.value = true
}

async function saveUser() {
  if (!editForm.id) return

  saving.value = true
  try {
    const response = await updateAuthUser(editForm.id, {
      displayName: editForm.displayName,
      roleCode: editForm.roleCode,
      status: editForm.status
    })

    if (response?.data?.code !== 200) {
      throw new Error(response?.data?.message || 'Save failed')
    }

    ElMessage.success('User updated')
    editorVisible.value = false
    await loadData()
  } catch (err) {
    console.error('Save user failed:', err)
    ElMessage.error(err.response?.data?.message || err.message || 'Save failed')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 'active' ? 'disabled' : 'active'
  const title = row.status === 'active' ? 'Disable this account?' : 'Enable this account?'

  try {
    await ElMessageBox.confirm(title, 'Account status', {
      confirmButtonText: 'Confirm',
      cancelButtonText: 'Cancel',
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

    ElMessage.success('Account status updated')
    await loadData()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      console.error('Toggle user status failed:', err)
      ElMessage.error(err.response?.data?.message || 'Update failed')
    }
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [usersResponse, rolesResponse] = await Promise.all([getAuthUsers(), getAuthRoles()])
    users.value = Array.isArray(usersResponse?.data?.data) ? usersResponse.data.data : []
    roles.value = Array.isArray(rolesResponse?.data?.data) ? rolesResponse.data.data : []
  } catch (err) {
    console.error('Load users page failed:', err)
    error.value = err.response?.data?.message || 'Load user management data failed'
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.users-page {
  min-height: 100%;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input,
.status-select {
  width: 220px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .search-input,
  .status-select {
    width: 100%;
  }
}
</style>
