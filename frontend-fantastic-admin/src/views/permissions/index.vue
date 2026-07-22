<script setup lang="ts">
import type { AuthRole } from '@/api/types'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import apiUser from '@/api/modules/user'
import { PERMISSION_HIERARCHY } from '@/utils/permission'

defineOptions({ name: 'PermissionsPage' })

interface PermDef {
  value: string
  label: string
  shortLabel: string
  category: string
  children: string[]
}

const permissionDefs: PermDef[] = [
  {
    value: 'record:manage',
    label: '病案管理（完整 CRUD 与输出）',
    shortLabel: '病案管理',
    category: '病案管理',
    children: ['record:edit', 'record:download', 'record:pdf:export', 'record:read'],
  },
  { value: 'record:edit', label: '病案编辑', shortLabel: '病案编辑', category: '病案管理', children: ['record:read'] },
  { value: 'record:read', label: '病案查看', shortLabel: '病案查看', category: '病案管理', children: [] },
  { value: 'record:download', label: '下载病案 ZIP 文件', shortLabel: '病案下载', category: '病案输出', children: ['record:read'] },
  { value: 'record:pdf:export', label: '导出病案 PDF 文件', shortLabel: 'PDF 导出', category: '病案输出', children: ['record:read'] },
  { value: 'search:read', label: '病案搜索', shortLabel: '病案搜索', category: '病案检索', children: [] },
  { value: 'statistics:read', label: '统计分析查看', shortLabel: '统计分析', category: '统计分析', children: [] },
  { value: 'user:manage', label: '用户管理', shortLabel: '用户管理', category: '用户管理', children: [] },
  { value: 'role:manage', label: '角色管理（完整 CRUD）', shortLabel: '角色管理', category: '角色管理', children: ['role:read'] },
  { value: 'role:read', label: '角色查看', shortLabel: '角色查看', category: '角色管理', children: [] },
  { value: 'log:read', label: '日志审计查看', shortLabel: '日志审计', category: '日志管理', children: [] },
  { value: 'system:manage', label: '系统设置、清理与运维写操作', shortLabel: '系统管理', category: '系统管理', children: ['system:read'] },
  { value: 'system:read', label: '系统设置与监控查看', shortLabel: '系统查看', category: '系统管理', children: [] },
  { value: 'test:read', label: '测试中心访问', shortLabel: '测试中心', category: '测试管理', children: [] },
]

const loading = ref(false)
const saving = ref(false)
const roles = ref<AuthRole[]>([])
const editingCode = ref<string | null>(null)
const viewMode = ref<'cards' | 'matrix'>('cards')
const defMap = new Map(permissionDefs.map(def => [def.value, def]))
const editForm = reactive({
  name: '',
  description: '',
  permissions: [] as string[],
})

const categories = computed(() => [...new Set(permissionDefs.map(def => def.category))])
const sortedRoles = computed(() => [...roles.value]
  .sort((a, b) => (a.sortOrder ?? 99) - (b.sortOrder ?? 99)))
const editDirectPerms = computed(() => getDirectPerms(editForm.permissions))
const editInheritedPerms = computed(() => getInheritedPerms(editForm.permissions))
const groupedOptions = computed(() => {
  const grouped = new Map<string, PermDef[]>()
  for (const def of getAvailablePerms(editForm.permissions)) {
    const options = grouped.get(def.category) ?? []
    options.push(def)
    grouped.set(def.category, options)
  }
  return [...grouped.entries()].map(([label, options]) => ({ label, options }))
})

function splitPermissions(raw: string | undefined): string[] {
  return raw?.split(',').map(item => item.trim()).filter(Boolean) ?? []
}

function isAdminRole(code: string | undefined): boolean {
  return String(code || '').toUpperCase() === 'ADMIN'
}

function getAllChildren(value: string): string[] {
  const def = defMap.get(value)
  if (!def) return []
  return [...new Set(def.children.flatMap(child => [child, ...getAllChildren(child)]))]
}

function getDirectPerms(perms: string[]): string[] {
  return perms.filter(permission => !perms.some(other =>
    other !== permission && PERMISSION_HIERARCHY[other]?.includes(permission),
  ))
}

function getInheritedPerms(perms: string[]): string[] {
  const inherited = new Set<string>()
  for (const permission of perms) {
    for (const child of PERMISSION_HIERARCHY[permission] ?? []) {
      if (child !== permission && !perms.includes(child)) inherited.add(child)
    }
  }
  return [...inherited]
}

function getAvailablePerms(perms: string[]): PermDef[] {
  const owned = new Set(perms)
  for (const permission of perms) {
    getAllChildren(permission).forEach(child => owned.add(child))
  }
  return permissionDefs.filter(def => !owned.has(def.value))
}

function collapsePermissions(perms: string[]): string[] {
  return getDirectPerms([...new Set(perms)])
}

function permsByCategory(perms: string[]) {
  return categories.value
    .map(category => [category, permissionDefs.filter(def =>
      def.category === category && perms.includes(def.value),
    )] as const)
    .filter(([, defs]) => defs.length > 0)
}

function roleHasPermission(role: AuthRole, permission: string): 'direct' | 'inherited' | 'none' {
  if (isAdminRole(role.code)) return 'direct'
  const raw = splitPermissions(role.permissions)
  if (getDirectPerms(raw).includes(permission)) return 'direct'
  if (raw.some(item => PERMISSION_HIERARCHY[item]?.includes(permission))) return 'inherited'
  return 'none'
}

function rolePermCount(role: AuthRole): number {
  if (isAdminRole(role.code)) return permissionDefs.length
  const resolved = new Set(splitPermissions(role.permissions))
  for (const permission of [...resolved]) {
    PERMISSION_HIERARCHY[permission]?.forEach(child => resolved.add(child))
  }
  return resolved.size
}

function viewDirectPermsFor(role: AuthRole): string[] {
  return isAdminRole(role.code)
    ? permissionDefs.map(def => def.value)
    : getDirectPerms(splitPermissions(role.permissions))
}

function permLabel(value: string): string {
  return defMap.get(value)?.shortLabel ?? value
}

function isImpliedBy(permission: string): boolean {
  return editForm.permissions.some(existing =>
    existing !== permission && PERMISSION_HIERARCHY[existing]?.includes(permission),
  )
}

function startEdit(role: AuthRole) {
  editingCode.value = role.code ?? null
  editForm.name = role.name ?? ''
  editForm.description = role.description ?? ''
  editForm.permissions = splitPermissions(role.permissions)
}

function cancelEdit() {
  editingCode.value = null
}

function addPermission(permission: string) {
  if (!permission || editForm.permissions.includes(permission)) return
  editForm.permissions.push(permission)
}

function removePermission(permission: string) {
  const removed = new Set([permission, ...getAllChildren(permission)])
  editForm.permissions = editForm.permissions.filter(item => !removed.has(item))
}

async function saveEdit(code: string) {
  saving.value = true
  try {
    const permissions = collapsePermissions(editForm.permissions)
    await apiUser.updateRole(code, {
      name: editForm.name || undefined,
      description: editForm.description || undefined,
      permissions: permissions.length ? permissions.join(',') : undefined,
    })
    ElMessage.success('角色更新成功，用户重新登录后生效')
    editingCode.value = null
    await loadPermissions()
  }
  catch (error: unknown) {
    ElMessage.error((error as { message?: string })?.message || '角色更新失败')
  }
  finally {
    saving.value = false
  }
}

async function loadPermissions() {
  loading.value = true
  try {
    const response = await apiUser.getRoles()
    roles.value = Array.isArray((response as { data?: AuthRole[] }).data)
      ? (response as { data: AuthRole[] }).data
      : []
  }
  catch (error: unknown) {
    ElMessage.error((error as { message?: string })?.message || '角色列表加载失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadPermissions)
</script>

<template>
  <div class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">Permission Management</p>
        <h2>权限管理</h2>
        <p class="subtitle">
          分离病案查看、输出、系统查看与系统管理权限。修改保存后，用户重新登录生效。
        </p>
      </div>
      <div class="header-actions">
        <el-segmented v-model="viewMode" :options="[{ label: '卡片视图', value: 'cards' }, { label: '矩阵视图', value: 'matrix' }]" />
        <el-button :loading="loading" @click="loadPermissions">刷新</el-button>
      </div>
    </header>

    <el-alert type="info" :closable="false" show-icon>
      <template #title>
        病案查看不会自动获得下载或 PDF 导出权限；系统查看不会自动获得设置修改、日志清理或数据质量执行权限。
      </template>
    </el-alert>

    <el-empty v-if="!loading && !sortedRoles.length" description="暂无角色数据" />

    <el-card v-if="viewMode === 'matrix' && sortedRoles.length" shadow="never" class="matrix-card">
      <el-table v-loading="loading" :data="permissionDefs" border stripe>
        <el-table-column label="权限" min-width="230" fixed>
          <template #default="{ row }">
            <div class="matrix-permission">
              <strong>{{ row.shortLabel }}</strong>
              <span>{{ row.value }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-for="role in sortedRoles"
          :key="role.code"
          :label="role.name || role.code!"
          min-width="120"
          align="center"
        >
          <template #default="{ row }">
            <span
              class="matrix-dot"
              :class="roleHasPermission(role, row.value)"
              :title="roleHasPermission(role, row.value) === 'direct' ? '直接授权' : roleHasPermission(role, row.value) === 'inherited' ? '继承获得' : '无权限'"
            >
              <span v-if="roleHasPermission(role, row.value) === 'direct'">✓</span>
              <span v-else-if="roleHasPermission(role, row.value) === 'inherited'">•</span>
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div class="matrix-legend">
        <span><i class="legend direct" />直接授权</span>
        <span><i class="legend inherited" />继承获得</span>
        <span><i class="legend none" />无权限</span>
      </div>
    </el-card>

    <div v-else v-loading="loading" class="roles-grid">
      <el-card
        v-for="role in sortedRoles"
        :key="role.code"
        shadow="never"
        class="role-card"
        :class="{ admin: isAdminRole(role.code) }"
      >
        <template v-if="editingCode !== role.code">
          <div class="role-header">
            <div>
              <div class="role-name-row">
                <strong>{{ role.name || role.code }}</strong>
                <el-tag size="small" effect="plain">{{ role.code }}</el-tag>
              </div>
              <span class="permission-count">{{ rolePermCount(role) }} 项权限</span>
            </div>
            <el-button v-if="!isAdminRole(role.code)" type="primary" link @click="startEdit(role)">编辑</el-button>
          </div>
          <p v-if="role.description" class="role-description">{{ role.description }}</p>
          <p v-if="isAdminRole(role.code)" class="admin-note">管理员自动拥有全部权限，不可修改。</p>
          <div class="permission-groups">
            <section v-for="[category, defs] in permsByCategory(viewDirectPermsFor(role))" :key="category">
              <span class="category-label">{{ category }}</span>
              <div class="permission-tags">
                <el-tooltip
                  v-for="def in defs"
                  :key="def.value"
                  :content="def.label + (def.children.length ? `；继承 ${getAllChildren(def.value).map(permLabel).join('、')}` : '')"
                >
                  <el-tag size="small" :type="def.children.length ? 'primary' : 'info'">{{ def.shortLabel }}</el-tag>
                </el-tooltip>
              </div>
            </section>
          </div>
        </template>

        <template v-else>
          <div class="role-header">
            <div class="edit-fields">
              <el-input v-model="editForm.name" size="small" placeholder="角色名称" />
              <el-tag size="small" effect="plain">{{ role.code }}</el-tag>
            </div>
            <div>
              <el-button size="small" @click="cancelEdit">取消</el-button>
              <el-button size="small" type="primary" :loading="saving" @click="saveEdit(role.code!)">保存</el-button>
            </div>
          </div>
          <el-input v-model="editForm.description" size="small" placeholder="角色描述" />

          <div class="edit-section">
            <span class="category-label">直接权限</span>
            <div class="permission-tags">
              <el-tag
                v-for="permission in editDirectPerms"
                :key="permission"
                size="small"
                closable
                @close="removePermission(permission)"
              >
                {{ permLabel(permission) }}
              </el-tag>
              <span v-if="!editDirectPerms.length" class="empty-text">暂无直接权限</span>
            </div>
          </div>

          <div v-if="editInheritedPerms.length" class="edit-section">
            <span class="category-label">继承权限</span>
            <div class="permission-tags">
              <el-tag v-for="permission in editInheritedPerms" :key="permission" size="small" type="info" effect="plain">
                {{ permLabel(permission) }}
              </el-tag>
            </div>
          </div>

          <el-select
            class="permission-select"
            placeholder="搜索或选择要添加的权限"
            filterable
            model-value=""
            @change="addPermission"
          >
            <el-option-group v-for="group in groupedOptions" :key="group.label" :label="group.label">
              <el-option
                v-for="option in group.options"
                :key="option.value"
                :value="option.value"
                :disabled="isImpliedBy(option.value)"
                :label="`${option.shortLabel} · ${option.value}`"
              />
            </el-option-group>
          </el-select>
        </template>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 18px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.header-actions, .role-header, .role-name-row, .permission-tags, .matrix-legend, .matrix-legend span, .edit-fields { display: flex; align-items: center; }
.header-actions, .role-name-row, .permission-tags, .matrix-legend, .edit-fields { gap: 8px; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); letter-spacing: 0.12em; text-transform: uppercase; }
h2 { margin: 0; font-size: 28px; }
.subtitle, .role-description { margin: 8px 0 0; color: var(--text-secondary); }
.roles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.role-card { display: grid; gap: 12px; }
.role-card.admin { border-color: hsl(var(--primary) / 30%); }
.role-header { gap: 12px; justify-content: space-between; }
.permission-count, .admin-note, .category-label, .empty-text, .matrix-permission span { font-size: 12px; color: var(--text-tertiary); }
.role-description { font-size: 13px; line-height: 1.6; }
.admin-note { margin: 0; }
.permission-groups { display: grid; gap: 10px; }
.permission-groups section, .edit-section, .matrix-permission { display: grid; gap: 6px; }
.permission-tags { flex-wrap: wrap; }
.category-label { font-weight: 600; letter-spacing: 0.04em; }
.edit-fields :deep(.el-input) { width: 150px; }
.permission-select { width: 100%; }
.matrix-card { overflow-x: auto; }
.matrix-dot { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 8px; }
.matrix-dot.direct { color: hsl(var(--primary)); background: hsl(var(--primary) / 10%); }
.matrix-dot.inherited { color: var(--text-tertiary); background: var(--surface-alt); }
.matrix-legend { padding-top: 12px; margin-top: 14px; border-top: 1px solid var(--divider); }
.matrix-legend span { gap: 5px; font-size: 12px; color: var(--text-secondary); }
.legend { width: 13px; height: 13px; border: 1px solid var(--divider); border-radius: 4px; }
.legend.direct { background: hsl(var(--primary) / 15%); border-color: hsl(var(--primary) / 30%); }
.legend.inherited { background: var(--surface-alt); }
.legend.none { background: transparent; }
@media (max-width: 760px) {
  .page-header { flex-direction: column; }
  .header-actions { width: 100%; justify-content: space-between; }
  .roles-grid { grid-template-columns: 1fr; }
}
</style>
