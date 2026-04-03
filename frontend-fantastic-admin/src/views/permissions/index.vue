<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuthRoles } from '@/api/modules/auth'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PermissionsPage' })

const userStore = useUserStore()
const loading = ref(false)
const roles = ref<any[]>([])
const searchTerm = ref('')
const moduleFilter = ref('all')

const moduleOptions = [
  { label: '全部模块', value: 'all' },
  { label: '认证', value: 'auth' },
  { label: '记录', value: 'record' },
  { label: '日志', value: 'log' },
  { label: '系统', value: 'system' },
  { label: '统计', value: 'statistics' },
  { label: '搜索', value: 'search' },
  { label: '其他', value: 'other' },
]

function normalizePermissions(item: any) {
  const raw = item?.permissions || item?.permissionsCsv || ''
  if (Array.isArray(raw)) return raw.filter(Boolean)
  return String(raw).split(',').map(permission => permission.trim()).filter(Boolean)
}

async function loadData() {
  loading.value = true
  try {
    const payload = await getAuthRoles()
    roles.value = Array.isArray(payload.data) ? payload.data : []
  } catch (error: any) {
    roles.value = []
    ElMessage.error(error?.message || '权限数据加载失败')
  } finally {
    loading.value = false
  }
}

const filteredRoles = computed(() => {
  const keyword = searchTerm.value.trim().toLowerCase()
  return roles.value.filter((role) => {
    const permissions = normalizePermissions(role)
    const matchesKeyword = !keyword || [role.code, role.name, role.description, ...permissions]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(keyword))
    const matchesModule = moduleFilter.value === 'all'
      || permissions.some(permission => permission.startsWith(`${moduleFilter.value}:`))
    return matchesKeyword && matchesModule
  })
})

const permissionGroups = computed(() => {
  const buckets = new Map<string, Set<string>>()
  roles.value.forEach((role) => {
    normalizePermissions(role).forEach((permission) => {
      const key = permission.includes(':') ? permission.split(':')[0] : 'other'
      const current = buckets.get(key) || new Set<string>()
      current.add(permission)
      buckets.set(key, current)
    })
  })
  return Array.from(buckets.entries()).map(([key, values]) => ({
    key,
    permissions: Array.from(values).sort(),
  })).filter(group => moduleFilter.value === 'all' || group.key === moduleFilter.value)
})

const summaryCards = computed(() => {
  const uniquePermissions = new Set<string>()
  roles.value.forEach(role => normalizePermissions(role).forEach(permission => uniquePermissions.add(permission)))
  return [
    { label: '角色总数', value: roles.value.length, note: '后端角色表中已定义角色数量' },
    { label: '权限点总数', value: uniquePermissions.size, note: '系统中可见权限点数量' },
    { label: '当前账号', value: userStore.profile?.displayName || userStore.profile?.username || '-', note: '当前登录账号' },
    { label: '当前角色', value: userStore.profile?.roleName || userStore.profile?.roleCode || '-', note: '当前账号角色' },
  ]
})

onMounted(loadData)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">Permission Center</p>
        <h2>权限管理</h2>
        <p class="subtitle">查看角色与权限点映射关系，以及当前账号的权限视图。</p>
      </div>
      <div class="actions">
        <el-input v-model="searchTerm" clearable placeholder="搜索角色或权限" />
        <el-select v-model="moduleFilter" placeholder="模块">
          <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <section class="summary-grid">
      <el-card v-for="item in summaryCards" :key="item.label" shadow="never">
        <div class="summary-label">{{ item.label }}</div>
        <div class="summary-value">{{ item.value }}</div>
        <div class="summary-note">{{ item.note }}</div>
      </el-card>
    </section>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>角色列表</template>
          <el-table v-loading="loading" :data="filteredRoles" stripe>
            <el-table-column prop="code" label="角色编码" width="140" />
            <el-table-column prop="name" label="角色名称" min-width="140" />
            <el-table-column prop="description" label="角色说明" min-width="180" />
            <el-table-column label="权限点" min-width="280">
              <template #default="{ row }">
                <div class="permission-tags">
                  <el-tag v-for="permission in normalizePermissions(row)" :key="permission" size="small" type="info">
                    {{ permission }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>权限矩阵</template>
          <div class="matrix-list">
            <article v-for="group in permissionGroups" :key="group.key" class="matrix-item">
              <div class="matrix-header">
                <strong>{{ group.key }}</strong>
                <el-tag type="success">{{ group.permissions.length }}</el-tag>
              </div>
              <div class="permission-tags">
                <el-tag v-for="permission in group.permissions" :key="permission" size="small" effect="plain">
                  {{ permission }}
                </el-tag>
              </div>
            </article>
          </div>
        </el-card>
      </el-col>
    </el-row>
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

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.matrix-list {
  display: grid;
  gap: 12px;
}

.matrix-item {
  padding: 14px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.03);
}

.matrix-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
</style>
