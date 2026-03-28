<template>
  <div class="permissions-view pmr-page">
    <section class="pmr-page-header">
      <div>
        <p class="module-eyebrow">Permission Center</p>
        <h2 class="pmr-page-title">权限管理</h2>
        <p class="pmr-page-subtitle">集中查看角色、权限点和当前账号的可见范围，默认只读展示。</p>
      </div>
      <div class="pmr-toolbar-actions">
        <el-input v-model="searchTerm" clearable placeholder="搜索角色或权限" style="width: 220px" />
        <el-select v-model="moduleFilter" clearable placeholder="权限模块" style="width: 160px">
          <el-option label="全部模块" value="all" />
          <el-option label="认证" value="auth" />
          <el-option label="病案" value="record" />
          <el-option label="日志" value="log" />
          <el-option label="系统" value="system" />
          <el-option label="统计" value="statistics" />
          <el-option label="检索" value="search" />
          <el-option label="其他" value="other" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </section>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      class="mb-16"
    />

    <el-alert
      v-else
      title="权限管理为只读视图，角色权限由后端数据库统一维护。"
      type="info"
      show-icon
      class="mb-16"
    />

    <section class="summary-grid">
      <article v-for="item in summaryCards" :key="item.label" class="summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </section>

    <section class="permission-layout">
      <el-card class="pmr-panel permission-card" shadow="never">
        <template #header>
          <div class="pmr-panel-header">
            <div>
              <h3 class="pmr-panel-title">角色清单</h3>
              <p class="pmr-panel-subtitle">按角色查看对应权限集合与排序信息。</p>
            </div>
            <span class="pmr-badge">{{ filteredRoles.length }} 个角色</span>
          </div>
        </template>

        <el-table :data="filteredRoles" v-loading="loading" stripe border>
          <el-table-column prop="code" label="角色编码" width="140" />
          <el-table-column prop="name" label="角色名称" min-width="140" />
          <el-table-column prop="description" label="角色说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="权限点" min-width="320">
            <template #default="{ row }">
              <div class="permission-tags">
                <el-tag
                  v-for="permission in getPermissionList(row)"
                  :key="permission"
                  size="small"
                  type="info"
                >
                  {{ permission }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="90" />
        </el-table>
      </el-card>

      <el-card class="pmr-panel permission-card" shadow="never">
        <template #header>
          <div class="pmr-panel-header">
            <div>
              <h3 class="pmr-panel-title">权限矩阵</h3>
              <p class="pmr-panel-subtitle">按模块聚合展示当前系统已定义的权限点。</p>
            </div>
            <span class="pmr-badge">{{ groupedPermissions.length }} 个模块</span>
          </div>
        </template>

        <div v-if="groupedPermissions.length" class="module-grid">
          <article v-for="group in groupedPermissions" :key="group.key" class="module-card">
            <div class="module-card-top">
              <strong>{{ group.label }}</strong>
              <el-tag size="small" type="success">{{ group.permissions.length }}</el-tag>
            </div>
            <div class="permission-tags">
              <el-tag
                v-for="permission in group.permissions"
                :key="permission"
                size="small"
                effect="plain"
              >
                {{ permission }}
              </el-tag>
            </div>
          </article>
        </div>

        <el-empty v-else description="暂无可展示的权限点" />
      </el-card>
    </section>

    <el-card class="pmr-panel pmr-section" shadow="never">
      <template #header>
        <div class="pmr-panel-header">
          <div>
            <h3 class="pmr-panel-title">当前账号权限</h3>
            <p class="pmr-panel-subtitle">结合登录态展示当前可访问范围。</p>
          </div>
        </div>
      </template>

      <div class="current-user-grid">
        <article class="current-user-card">
          <div class="current-user-label">账号</div>
          <div class="current-user-value">{{ currentUserLabel }}</div>
        </article>
        <article class="current-user-card">
          <div class="current-user-label">角色</div>
          <div class="current-user-value">{{ currentRoleLabel }}</div>
        </article>
        <article class="current-user-card">
          <div class="current-user-label">状态</div>
          <div class="current-user-value">{{ currentStatusLabel }}</div>
        </article>
        <article class="current-user-card">
          <div class="current-user-label">管理员权限</div>
          <div class="current-user-value" :class="isAdmin ? 'success' : 'danger'">
            {{ isAdmin ? '已启用' : '未启用' }}
          </div>
        </article>
      </div>

      <div class="permission-tags current-tags">
        <el-tag v-for="permission in currentPermissions" :key="permission" type="success">
          {{ permission }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAuthRoles } from '@/services/api'
import { getCurrentUser, isAdminUser } from '@/utils/session.js'

const loading = ref(false)
const error = ref('')
const roles = ref([])
const searchTerm = ref('')
const moduleFilter = ref('all')

const currentUser = computed(() => getCurrentUser())
const isAdmin = computed(() => isAdminUser())

const currentUserLabel = computed(
  () => currentUser.value?.displayName || currentUser.value?.username || '未登录'
)
const currentRoleLabel = computed(
  () => currentUser.value?.roleName || currentUser.value?.roleCode || '未分配'
)
const currentStatusLabel = computed(() => currentUser.value?.status || 'unknown')
const currentPermissions = computed(() => {
  const permissions = currentUser.value?.permissions
  return Array.isArray(permissions) ? permissions : []
})

const loadData = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await getAuthRoles()
    const payload = response?.data
    roles.value = Array.isArray(payload?.data) ? payload.data : []
  } catch (err) {
    console.error('加载权限数据失败:', err)
    error.value = err.response?.data?.message || '加载权限数据失败'
  } finally {
    loading.value = false
  }
}

const normalizePermissions = (item) => {
  const rawPermissions =
    item?.permissions ||
    item?.permissionsCsv ||
    item?.rolePermissions ||
    ''

  if (Array.isArray(rawPermissions)) {
    return rawPermissions.filter(Boolean)
  }

  if (typeof rawPermissions === 'string') {
    return rawPermissions
      .split(',')
      .map((permission) => permission.trim())
      .filter(Boolean)
  }

  return []
}

const getPermissionList = (item) => normalizePermissions(item)

const filteredRoles = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  const moduleName = moduleFilter.value

  return roles.value.filter((role) => {
    const rolePermissions = normalizePermissions(role)
    const matchesKeyword =
      !keyword ||
      [role.code, role.name, role.description, ...rolePermissions]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))

    const matchesModule =
      moduleName === 'all' ||
      !moduleName ||
      rolePermissions.some((permission) => permission.startsWith(`${moduleName}:`))

    return matchesKeyword && matchesModule
  })
})

const permissionGroups = computed(() => {
  const buckets = new Map()

  for (const role of roles.value) {
    for (const permission of normalizePermissions(role)) {
      const key = permission.includes(':') ? permission.split(':')[0] : 'other'
      const list = buckets.get(key) || new Set()
      list.add(permission)
      buckets.set(key, list)
    }
  }

  const labelMap = {
    auth: '认证',
    record: '病案',
    log: '日志',
    system: '系统',
    statistics: '统计',
    search: '检索',
    other: '其他'
  }

  return Array.from(buckets.entries())
    .map(([key, permissions]) => ({
      key,
      label: labelMap[key] || key,
      permissions: Array.from(permissions).sort()
    }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))
})

const groupedPermissions = computed(() =>
  permissionGroups.value.filter((group) => {
    if (moduleFilter.value === 'all' || !moduleFilter.value) {
      return true
    }
    return group.key === moduleFilter.value
  })
)

const summaryCards = computed(() => {
  const uniquePermissions = new Set()
  roles.value.forEach((role) => normalizePermissions(role).forEach((permission) => uniquePermissions.add(permission)))
  return [
    { label: '角色总数', value: roles.value.length, note: '后端角色表中已定义的角色数量' },
    { label: '权限总数', value: uniquePermissions.size, note: '系统当前可见的权限点总数' },
    { label: '管理员角色', value: roles.value.filter((role) => String(role.code || '').toUpperCase() === 'ADMIN').length, note: '系统管理员角色数量' },
    { label: '当前账号', value: currentUserLabel.value, note: isAdmin.value ? '当前账号拥有管理权限' : '当前账号为受限访问' }
  ]
})

onMounted(loadData)
</script>

<style scoped>
.permissions-view {
  display: grid;
  gap: 20px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
  border: 1px solid #e6edf7;
}

.summary-card span,
.current-user-label {
  display: block;
  font-size: 12px;
  color: var(--pmr-color-text-secondary);
}

.summary-card strong,
.current-user-value {
  display: block;
  margin-top: 6px;
  font-size: 24px;
  line-height: 1.1;
  color: var(--pmr-color-text-primary);
  word-break: break-word;
}

.summary-card small {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #7a889f;
}

.permission-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
}

.permission-card {
  min-width: 0;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.module-grid {
  display: grid;
  gap: 12px;
}

.module-card {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #e6edf7;
  background: linear-gradient(180deg, #ffffff 0%, #f9fbff 100%);
}

.module-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.current-user-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.current-user-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
  border: 1px solid #e6edf7;
}

.current-user-value.success {
  color: var(--pmr-color-success-500);
}

.current-user-value.danger {
  color: var(--pmr-color-danger-500);
}

.current-tags {
  margin-top: 12px;
}

.mb-16 {
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .summary-grid,
  .permission-layout,
  .current-user-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .permission-layout,
  .current-user-grid {
    grid-template-columns: 1fr;
  }
}
</style>
