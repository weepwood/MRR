<script setup lang="ts">
import type { AuthRole } from '@/api/types'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import apiUser from '@/api/modules/user'
import { PERMISSION_HIERARCHY } from '@/utils/permission'

defineOptions({ name: 'PermissionsPage' })

const loading = ref(false)
const saving = ref(false)
const roles = ref<AuthRole[]>([])
const editingCode = ref<string | null>(null)
const viewMode = ref<'cards' | 'matrix'>('cards')

interface PermDef {
  value: string
  label: string
  shortLabel: string
  category: string
  children: string[]
}

const permissionDefs: PermDef[] = [
  { value: 'record:manage', label: '病案管理（完整 CRUD）', shortLabel: '病案管理', category: '病案管理', children: ['record:edit', 'record:read'] },
  { value: 'record:edit', label: '病案编辑', shortLabel: '病案编辑', category: '病案管理', children: ['record:read'] },
  { value: 'record:read', label: '病案查看', shortLabel: '病案查看', category: '病案管理', children: [] },
  { value: 'search:read', label: '病案搜索', shortLabel: '病案搜索', category: '病案检索', children: [] },
  { value: 'statistics:read', label: '统计分析查看', shortLabel: '统计分析', category: '统计分析', children: [] },
  { value: 'user:manage', label: '用户管理', shortLabel: '用户管理', category: '用户管理', children: [] },
  { value: 'role:manage', label: '角色管理（完整 CRUD）', shortLabel: '角色管理', category: '角色管理', children: ['role:read'] },
  { value: 'role:read', label: '角色查看', shortLabel: '角色查看', category: '角色管理', children: [] },
  { value: 'log:read', label: '日志审计查看', shortLabel: '日志审计', category: '日志管理', children: [] },
  { value: 'system:read', label: '系统设置/监控', shortLabel: '系统设置', category: '系统管理', children: [] },
  { value: 'test:read', label: '测试中心访问', shortLabel: '测试中心', category: '测试管理', children: [] },
]

const defMap = new Map(permissionDefs.map(d => [d.value, d]))

const editForm = reactive({
  name: '',
  description: '',
  permissions: [] as string[],
})

const categories = computed(() => {
  const set = new Set<string>()
  permissionDefs.forEach(d => set.add(d.category))
  return Array.from(set)
})

function permsByCategory(perms: string[]) {
  const map = new Map<string, PermDef[]>()
  for (const cat of categories.value) {
    map.set(cat, [])
  }
  for (const perm of perms) {
    const def = defMap.get(perm)
    if (def && map.has(def.category)) {
      map.get(def.category)!.push(def)
    }
  }
  return Array.from(map.entries()).filter(([, defs]) => defs.length > 0)
}

function getAllChildren(value: string): string[] {
  const def = defMap.get(value)
  if (!def) { return [] }
  const result = [...def.children]
  for (const c of def.children) {
    result.push(...getAllChildren(c))
  }
  return [...new Set(result)]
}

function isImpliedBy(perm: string): boolean {
  return editForm.permissions.some(p => p !== perm && PERMISSION_HIERARCHY[p]?.includes(perm))
}

function getDirectPerms(perms: string[]): string[] {
  return perms.filter(p => !perms.some(other => other !== p && PERMISSION_HIERARCHY[other]?.includes(p)))
}

function getInheritedPerms(perms: string[]): string[] {
  const expanded = new Set<string>()
  for (const p of perms) {
    const children = PERMISSION_HIERARCHY[p]
    if (children) {
      children.forEach((c) => {
        if (c !== p) { expanded.add(c) }
      })
    }
  }
  for (const p of perms) {
    expanded.delete(p)
  }
  return Array.from(expanded)
}

function getAvailablePerms(perms: string[]): PermDef[] {
  const owned = new Set(perms)
  for (const p of perms) {
    const children = getAllChildren(p)
    children.forEach(c => owned.add(c))
  }
  return permissionDefs.filter(d => !owned.has(d.value))
}

function collapsePermissions(perms: string[]): string[] {
  return perms.filter(p =>
    !perms.some(other => other !== p && PERMISSION_HIERARCHY[other]?.includes(p)),
  )
}

const groupedOptions = computed(() => {
  const available = getAvailablePerms(editForm.permissions)
  const map = new Map<string, PermDef[]>()
  for (const def of available) {
    if (!map.has(def.category)) { map.set(def.category, []) }
    map.get(def.category)!.push(def)
  }
  return Array.from(map.entries()).map(([label, options]) => ({ label, options }))
})

const editDirectPerms = computed(() => getDirectPerms(editForm.permissions))
const editInheritedPerms = computed(() => getInheritedPerms(editForm.permissions))

const sortedRoles = computed(() =>
  [...roles.value].sort((a, b) => (a.sortOrder ?? 99) - (b.sortOrder ?? 99)),
)

// Matrix: for each permission def, which roles have it (direct or inherited)?
function roleHasPermission(role: AuthRole, permValue: string): 'direct' | 'inherited' | 'none' {
  if (isAdminRole(role.code)) { return 'direct' }
  const raw = splitPermissions(role.permissions)
  const resolved = new Set<string>()
  for (const p of raw) {
    resolved.add(p)
    const children = PERMISSION_HIERARCHY[p]
    if (children) { children.forEach(c => resolved.add(c)) }
  }
  const directSet = new Set(getDirectPerms(raw))
  if (directSet.has(permValue)) { return 'direct' }
  if (resolved.has(permValue)) { return 'inherited' }
  return 'none'
}

function splitPermissions(raw: string | undefined): string[] {
  if (!raw) { return [] }
  return raw.split(',').map(p => p.trim()).filter(Boolean)
}

function isAdminRole(code: string | undefined): boolean {
  return String(code || '').toUpperCase() === 'ADMIN'
}

function permLabel(value: string): string {
  return defMap.get(value)?.shortLabel ?? value
}

function rolePermCount(role: AuthRole): number {
  if (isAdminRole(role.code)) { return permissionDefs.length }
  const raw = splitPermissions(role.permissions)
  const resolved = new Set<string>()
  for (const p of raw) {
    resolved.add(p)
    const children = PERMISSION_HIERARCHY[p]
    if (children) { children.forEach(c => resolved.add(c)) }
  }
  return resolved.size
}

function viewDirectPermsFor(role: AuthRole): string[] {
  if (isAdminRole(role.code)) {
    return permissionDefs.map(d => d.value)
  }
  return viewDirectPerms(role.permissions)
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

function addPermission(value: string) {
  if (!value) { return }
  if (editForm.permissions.includes(value)) {
    ElMessage.warning(`权限 "${permLabel(value)}" 已存在`)
    return
  }
  editForm.permissions.push(value)
}

function removePermission(perm: string) {
  const effected = [perm, ...getAllChildren(perm)]
  editForm.permissions = editForm.permissions.filter(p => !effected.includes(p))
}

async function saveEdit(code: string) {
  saving.value = true
  try {
    const collapsed = collapsePermissions(editForm.permissions)
    const payload = {
      name: editForm.name || undefined,
      description: editForm.description || undefined,
      permissions: collapsed.length > 0 ? collapsed.join(',') : undefined,
    }
    await apiUser.updateRole(code, payload)
    ElMessage.success('角色更新成功')
    editingCode.value = null
    await loadPermissions()
  }
  catch (error: any) {
    ElMessage.error(error?.message || '角色更新失败')
  }
  finally {
    saving.value = false
  }
}

function viewDirectPerms(rawPerms: string | undefined): string[] {
  if (!rawPerms) { return [] }
  return getDirectPerms(splitPermissions(rawPerms))
}

async function loadPermissions() {
  loading.value = true
  try {
    const res = await apiUser.getRoles()
    roles.value = Array.isArray((res as any).data) ? (res as any).data : []
  }
  catch (error: any) {
    ElMessage.error(error?.message || '角色列表加载失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadPermissions)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <p class="eyebrow">
          Permission Management
        </p>
        <h2>权限管理</h2>
        <p class="subtitle">
          管理系统内各角色及其权限配置。修改会实时保存到数据库，用户重新登录后生效。
        </p>
      </div>
      <div class="header-actions">
        <el-segmented v-model="viewMode" :options="[{ label: '卡片视图', value: 'cards' }, { label: '矩阵视图', value: 'matrix' }]" />
        <el-button :loading="loading" @click="loadPermissions">
          刷新
        </el-button>
      </div>
    </div>

    <div v-if="!loading && sortedRoles.length === 0" class="empty-wrap">
      <el-empty description="暂无角色数据" />
    </div>

    <!-- ============ 矩阵视图 ============ -->
    <el-card v-if="viewMode === 'matrix' && sortedRoles.length" shadow="never" class="matrix-card">
      <el-table v-loading="loading" :data="permissionDefs" stripe border>
        <el-table-column label="权限" min-width="220" fixed>
          <template #default="{ row }">
            <div class="matrix-perm-cell">
              <strong>{{ row.shortLabel }}</strong>
              <span class="matrix-perm-code">{{ row.value }}</span>
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
          <template #header>
            <div class="matrix-role-header">
              <span>{{ role.name || role.code }}</span>
              <el-tag v-if="isAdminRole(role.code)" size="small" type="danger" effect="dark">
                ADMIN
              </el-tag>
            </div>
          </template>
          <template #default="{ row }">
            <div
              class="matrix-dot"
              :class="roleHasPermission(role, row.value)"
              :title="roleHasPermission(role, row.value) === 'direct' ? '直接授权' : roleHasPermission(role, row.value) === 'inherited' ? '继承获得' : '无权限'"
            >
              <svg v-if="roleHasPermission(role, row.value) === 'direct'" viewBox="0 0 16 16" width="16" height="16"><path d="M13.485 4.485a1 1 0 0 1 0 1.415l-6.5 6.5a1 1 0 0 1-1.414 0l-3-3a1 1 0 1 1 1.414-1.414L6.278 10.586l5.793-5.793a1 1 0 0 1 1.414 0z" fill="currentColor" /></svg>
              <svg v-else-if="roleHasPermission(role, row.value) === 'inherited'" viewBox="0 0 16 16" width="16" height="16"><circle cx="8" cy="8" r="3" fill="currentColor" /></svg>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="matrix-legend">
        <div class="legend-item">
          <span class="legend-dot direct" />
          <span>直接授权</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot inherited" />
          <span>继承获得</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot none" />
          <span>无权限</span>
        </div>
      </div>
    </el-card>

    <!-- ============ 卡片视图 ============ -->
    <div v-if="viewMode === 'cards'" v-loading="loading" class="roles-grid">
      <el-card
        v-for="role in sortedRoles"
        :key="role.code"
        shadow="never"
        class="role-card"
        :class="{ 'role-card-admin': isAdminRole(role.code) }"
      >
        <!-- ============ 查看模式 ============ -->
        <template v-if="editingCode !== role.code">
          <div class="role-header">
            <div class="role-title">
              <div class="role-icon" :class="isAdminRole(role.code) ? 'role-icon-admin' : ''">
                {{ (role.name || role.code || '?').charAt(0) }}
              </div>
              <div>
                <div class="role-name-row">
                  <strong class="role-name">{{ role.name || role.code }}</strong>
                  <el-tag size="small" :type="isAdminRole(role.code) ? 'danger' : 'info'" effect="plain">
                    {{ role.code }}
                  </el-tag>
                </div>
                <span class="role-perm-count">{{ rolePermCount(role) }} 项权限</span>
              </div>
            </div>
            <el-button v-if="!isAdminRole(role.code)" size="small" type="primary" link @click="startEdit(role)">
              编辑
            </el-button>
          </div>
          <p v-if="role.description" class="role-desc">
            {{ role.description }}
          </p>
          <p v-if="isAdminRole(role.code)" class="admin-note">
            自动拥有全部权限，不可修改
          </p>

          <div class="perm-view">
            <div v-if="viewDirectPermsFor(role).length === 0" class="no-perm">
              未配置权限
            </div>
            <template v-for="[cat, defs] in permsByCategory(viewDirectPermsFor(role))" :key="cat">
              <div class="perm-category">
                <span class="perm-category-label">{{ cat }}</span>
                <div class="perm-category-tags">
                  <el-tooltip
                    v-for="def in defs"
                    :key="def.value"
                    :content="def.value + (def.children.length ? ` → 包含 ${getAllChildren(def.value).join(', ')}` : '')"
                    placement="top"
                  >
                    <el-tag size="small" :type="def.children.length ? 'primary' : 'info'" effect="light">
                      {{ def.shortLabel }}
                    </el-tag>
                  </el-tooltip>
                </div>
              </div>
            </template>
          </div>
        </template>

        <!-- ============ 编辑模式 ============ -->
        <template v-else>
          <div class="role-header">
            <div class="role-title">
              <div class="role-icon">
                {{ (editForm.name || role.code || '?').charAt(0) }}
              </div>
              <div class="edit-fields">
                <el-input v-model="editForm.name" size="small" placeholder="角色名称" />
                <el-tag size="small" type="info" effect="plain">
                  {{ role.code }}
                </el-tag>
              </div>
            </div>
            <div class="role-header-actions">
              <el-button size="small" @click="cancelEdit">
                取消
              </el-button>
              <el-button size="small" type="primary" :loading="saving" @click="saveEdit(role.code!)">
                保存
              </el-button>
            </div>
          </div>
          <el-input v-model="editForm.description" size="small" placeholder="角色描述" class="edit-desc-input" />

          <div class="perm-section">
            <div class="perm-section-header">
              <span class="perm-label">直接权限</span>
              <span class="perm-hint">点击 × 删除（含继承子权限）</span>
            </div>
            <div v-if="editDirectPerms.length > 0" class="perm-tags">
              <el-tag
                v-for="perm in editDirectPerms"
                :key="perm"
                size="small"
                closable
                :disable-transitions="true"
                @close="removePermission(perm)"
              >
                {{ permLabel(perm) }}
              </el-tag>
            </div>
            <span v-else class="no-perm">暂无直接权限</span>
          </div>

          <div v-if="editInheritedPerms.length > 0" class="perm-section">
            <div class="perm-section-header">
              <span class="perm-label inherited-label">继承权限</span>
              <span class="perm-hint">由上级权限自动获得</span>
            </div>
            <div class="perm-tags">
              <el-tag
                v-for="perm in editInheritedPerms"
                :key="perm"
                size="small"
                type="info"
                effect="plain"
                :disable-transitions="true"
              >
                {{ permLabel(perm) }}
              </el-tag>
            </div>
          </div>

          <div class="perm-add-section">
            <p class="perm-label">
              添加权限
            </p>
            <el-select
              size="small"
              placeholder="搜索或选择权限"
              class="perm-add-select"
              filterable
              model-value=""
              @change="addPermission"
            >
              <el-option-group v-for="group in groupedOptions" :key="group.label" :label="group.label">
                <el-option
                  v-for="opt in group.options"
                  :key="opt.value"
                  :value="opt.value"
                  :disabled="isImpliedBy(opt.value)"
                >
                  <span>{{ opt.shortLabel }}</span>
                  <span class="option-hint">{{ opt.value }}<template v-if="opt.children.length"> — 含 {{ opt.children.map(permLabel).join(', ') }}</template></span>
                </el-option>
              </el-option-group>
            </el-select>
          </div>
        </template>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-shell { display: grid; gap: 20px; }
.page-header { display: flex; gap: 16px; align-items: flex-start; justify-content: space-between; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: var(--text-secondary); }
.empty-wrap { padding: 40px 0; }

/* ============ 卡片视图 ============ */
.roles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 16px; }
.role-card { display: flex; flex-direction: column; gap: 12px; transition: border-color 0.2s; }
.role-card-admin { border-color: hsl(var(--primary) / 30%); }
.role-header { display: flex; gap: 12px; align-items: flex-start; justify-content: space-between; }
.role-title { display: flex; gap: 12px; align-items: center; }

.role-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 18px;
  font-weight: 700;
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 8%);
  border-radius: 10px;
}

.role-icon-admin {
  color: hsl(var(--destructive));
  background: hsl(var(--destructive) / 8%);
}
.role-name-row { display: flex; gap: 8px; align-items: center; }
.role-name { font-size: 16px; }
.role-perm-count { font-size: 12px; color: var(--text-tertiary); }
.role-desc { margin: 0; font-size: 13px; line-height: 1.6; color: var(--text-secondary); }
.admin-note {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.role-header-actions { display: flex; flex-shrink: 0; gap: 8px; align-items: center; }
.edit-fields { display: flex; gap: 8px; align-items: center; }
.edit-fields :deep(.el-input) { width: 140px; }
.edit-desc-input { margin-top: 4px; }

/* 权限分类展示 */
.perm-view { display: flex; flex-direction: column; gap: 10px; margin-top: 4px; }
.perm-category { display: flex; flex-direction: column; gap: 6px; }

.perm-category-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.perm-category-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.no-perm { padding: 8px 0; font-size: 12px; color: var(--text-hint); }

/* 编辑模式 */
.perm-section { margin-top: 4px; }
.perm-section-header { display: flex; gap: 8px; align-items: baseline; margin-bottom: 8px; }
.perm-label { font-size: 12px; font-weight: 600; color: var(--text-tertiary); text-transform: uppercase; letter-spacing: 0.06em; }
.inherited-label { opacity: 0.7; }
.perm-hint { font-size: 11px; font-weight: 400; color: var(--text-hint); text-transform: none; letter-spacing: normal; }
.perm-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.perm-add-section { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.perm-add-select { width: 100%; }
.option-hint { margin-left: 4px; font-size: 11px; color: var(--text-tertiary); }

/* ============ 矩阵视图 ============ */
.matrix-card { overflow-x: auto; }
.matrix-perm-cell { display: flex; flex-direction: column; gap: 2px; }
.matrix-perm-code { font-family: monospace; font-size: 11px; color: var(--text-tertiary); }
.matrix-role-header { display: flex; flex-direction: column; gap: 4px; align-items: center; }

.matrix-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin: 0 auto;
  border-radius: 8px;
}
.matrix-dot.direct { color: hsl(var(--primary)); background: hsl(var(--primary) / 10%); }
.matrix-dot.inherited { color: var(--text-tertiary); background: var(--surface-alt); }
.matrix-dot.none { /* no visual indicator for missing permissions */ }
.matrix-legend { display: flex; gap: 20px; align-items: center; padding-top: 12px; margin-top: 16px; border-top: 1px solid var(--divider); }
.legend-item { display: flex; gap: 6px; align-items: center; font-size: 12px; color: var(--text-secondary); }
.legend-dot { width: 14px; height: 14px; border-radius: 4px; }
.legend-dot.direct { background: hsl(var(--primary) / 15%); border: 1px solid hsl(var(--primary) / 30%); }
.legend-dot.inherited { background: var(--surface-alt); border: 1px solid var(--divider); }
.legend-dot.none { background: transparent; border: 1px solid var(--divider); }
</style>
