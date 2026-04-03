<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { disableAuthUser, getAuthRoles, getAuthUsers, updateAuthUser } from '@/api/modules/auth'

defineOptions({ name: 'UsersPage' })

const users = ref<any[]>([])
const roles = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const searchTerm = ref('')
const statusFilter = ref('all')
const editorVisible = ref(false)

const editForm = reactive({
  id: null as null | number,
  username: '',
  displayName: '',
  roleCode: '',
  status: 'active',
})

const roleMap = computed(() => new Map(roles.value.map(role => [String(role.code || '').toUpperCase(), role])))

async function loadData() {
  loading.value = true
  try {
    const [userPayload, rolePayload] = await Promise.all([getAuthUsers(), getAuthRoles()])
    users.value = Array.isArray(userPayload.data) ? userPayload.data : []
    roles.value = Array.isArray(rolePayload.data) ? rolePayload.data : []
  } catch (error: any) {
    users.value = []
    roles.value = []
    ElMessage.error(error?.message || '用户管理数据加载失败')
  } finally {
    loading.value = false
  }
}

const filteredUsers = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  return users.value.filter((user) => {
    const matchesSearch = !keyword || [user.username, user.displayName, user.roleName, user.roleCode]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(keyword))
    const matchesStatus = statusFilter.value === 'all' || !statusFilter.value || user.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const summaryCards = computed(() => [
  { label: '账号总数', value: users.value.length, note: '当前系统中可管理账号' },
  { label: '启用账号', value: users.value.filter(user => user.status === 'active').length, note: '状态为 active 的账号数量' },
  { label: '管理员', value: users.value.filter(user => String(user.roleCode || '').toUpperCase() === 'ADMIN').length, note: '拥有管理员角色的账号数量' },
  { label: '角色数', value: roles.value.length, note: '后端角色表中可选角色' },
])

function permissionList(item: any) {
  const raw = item?.permissions || item?.permissionsCsv || roleMap.value.get(String(item?.roleCode || '').toUpperCase())?.permissions || ''
  if (Array.isArray(raw)) return raw
  return String(raw).split(',').map(permission => permission.trim()).filter(Boolean)
}

function getRoleType(roleCode: unknown) {
  const code = String(roleCode || '').toUpperCase()
  if (code === 'ADMIN') return 'danger'
  if (code === 'DOCTOR') return 'primary'
  if (code === 'NURSE') return 'success'
  return 'info'
}

function formatDateTime(value: unknown) {
  if (!value) return '未登录'
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

function openEditor(row: any) {
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
    await updateAuthUser(editForm.id, {
      displayName: editForm.displayName,
      roleCode: editForm.roleCode,
      status: editForm.status,
    })
    editorVisible.value = false
    ElMessage.success('用户信息已更新')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: any) {
  const nextStatus = row.status === 'active' ? 'disabled' : 'active'
  const title = row.status === 'active' ? '确认禁用该账号吗？' : '确认启用该账号吗？'
  try {
    await ElMessageBox.confirm(title, '账号状态变更', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    if (nextStatus === 'disabled') {
      await disableAuthUser(row.id)
    } else {
      await updateAuthUser(row.id, {
        displayName: row.displayName,
        roleCode: row.roleCode,
        status: nextStatus,
      })
    }
    ElMessage.success('账号状态已更新')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '状态更新失败')
    }
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">User Center</p>
        <h2>用户管理</h2>
        <p class="subtitle">维护后台账号、角色与权限信息，并支持启停用与角色调整。</p>
      </div>
      <div class="actions">
        <el-input v-model="searchTerm" clearable placeholder="搜索账号 / 姓名 / 角色" />
        <el-select v-model="statusFilter" placeholder="状态">
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

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">{{ item.label }}</div>
        <div class="summary-value">{{ item.value }}</div>
        <div class="summary-note">{{ item.note }}</div>
      </el-card>
    </section>

    <el-card shadow="never">
      <div class="role-strip" v-if="roles.length">
        <article v-for="role in roles" :key="role.code" class="role-pill">
          <div class="role-pill-top">
            <strong>{{ role.name }}</strong>
            <el-tag :type="getRoleType(role.code)">{{ role.code }}</el-tag>
          </div>
          <p>{{ role.description || '暂无说明' }}</p>
          <small>{{ permissionList(role).length }} 项权限</small>
        </article>
      </div>

      <el-table v-loading="loading" :data="filteredUsers" stripe style="margin-top: 16px">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="姓名" min-width="120" />
        <el-table-column prop="roleName" label="角色" min-width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.roleCode)">{{ row.roleName || row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="320">
          <template #default="{ row }">
            <div class="permission-tags">
              <el-tag v-for="permission in permissionList(row)" :key="permission" size="small" type="info">
                {{ permission }}
              </el-tag>
            </div>
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
          <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" @click="openEditor(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" :type="row.status === 'active' ? 'warning' : 'success'" @click="toggleStatus(row)">
                <el-icon><SwitchButton /></el-icon>
                {{ row.status === 'active' ? '禁用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" title="编辑用户" width="520px">
      <el-form :model="editForm" label-width="96px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleCode">
            <el-option v-for="role in roles" :key="role.code" :label="`${role.name} (${role.code})`" :value="role.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
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

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

h2 {
  margin: 0;
  font-size: 28px;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  color: #64748b;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.summary-note {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.role-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.role-pill {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e6edf7;
}

.role-pill-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.role-pill p {
  margin: 8px 0 0;
  color: #6b7280;
}

.role-pill small {
  display: block;
  margin-top: 8px;
  color: #7a889f;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.row-actions {
  display: flex;
  gap: 8px;
}
</style>
