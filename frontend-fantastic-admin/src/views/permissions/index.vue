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

interface PermDef {
  value: string
  label: string
  category: string
  children: string[]
}

const permissionDefs: PermDef[] = [
  { value: 'record:manage', label: '病案管理（完整 CRUD）', category: '病案管理', children: ['record:edit', 'record:read'] },
  { value: 'record:edit', label: '病案编辑', category: '病案管理', children: ['record:read'] },
  { value: 'record:read', label: '病案查看', category: '病案管理', children: [] },
  { value: 'search:read', label: '病案搜索', category: '病案检索', children: [] },
  { value: 'statistics:read', label: '统计分析查看', category: '统计分析', children: [] },
  { value: 'user:manage', label: '用户管理', category: '用户管理', children: [] },
  { value: 'role:manage', label: '角色管理（完整 CRUD）', category: '角色管理', children: ['role:read'] },
  { value: 'role:read', label: '角色查看', category: '角色管理', children: [] },
  { value: 'log:read', label: '日志审计查看', category: '日志管理', children: [] },
  { value: 'system:read', label: '系统设置/监控', category: '系统管理', children: [] },
  { value: 'test:read', label: '测试中心访问', category: '测试管理', children: [] },
]

const defMap = new Map(permissionDefs.map(d => [d.value, d]))

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
      children.forEach(c => { if (c !== p) expanded.add(c) })
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
    if (!map.has(def.category))
      map.set(def.category, [])
    map.get(def.category)!.push(def)
  }
  return Array.from(map.entries()).map(([label, options]) => ({ label, options }))
})

const editForm = reactive({
  name: '',
  description: '',
  permissions: [] as string[],
})

const editDirectPerms = computed(() => getDirectPerms(editForm.permissions))
const editInheritedPerms = computed(() => getInheritedPerms(editForm.permissions))

const sortedRoles = computed(() =>
  [...roles.value].sort((a, b) => (a.sortOrder ?? 99) - (b.sortOrder ?? 99)),
)

function splitPermissions(raw: string | undefined): string[] {
  if (!raw) { return [] }
  return raw.split(',').map(p => p.trim()).filter(Boolean)
}

function isAdminRole(code: string | undefined): boolean {
  return String(code || '').toUpperCase() === 'ADMIN'
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
    ElMessage.warning(`权限 "${value}" 已存在`)
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

// View mode helpers

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
        <p class="eyebrow">Permission Management</p>
        <h2>权限管理</h2>
        <p class="subtitle">
          管理系统内各角色及其权限配置。修改会实时保存到数据库，用户重新登录后生效。
        </p>
      </div>
      <el-button :loading="loading" @click="loadPermissions">刷新</el-button>
    </div>

    <div v-if="!loading && sortedRoles.length === 0" class="empty-wrap">
      <el-empty description="暂无角色数据" />
    </div>

    <div v-loading="loading" class="roles-grid">
      <el-card
        v-for="role in sortedRoles"
        :key="role.code"
        shadow="never"
        class="role-card"
      >
        <!-- ============ 查看模式 ============ -->
        <template v-if="editingCode !== role.code">
          <div class="role-header">
            <div>
              <strong class="role-name">{{ role.name || role.code }}</strong>
              <el-tag size="small" class="role-code-tag">{{ role.code }}</el-tag>
            </div>
            <div class="role-header-actions">
              <span v-if="role.sortOrder !== undefined" class="role-order">排序 {{ role.sortOrder }}</span>
              <el-button v-if="!isAdminRole(role.code)" size="small" type="primary" link @click="startEdit(role)">编辑</el-button>
            </div>
          </div>
          <p v-if="role.description" class="role-desc">{{ role.description }}</p>
          <el-alert
            v-if="isAdminRole(role.code)"
            type="info"
            :closable="false"
            class="admin-note"
            title="管理员自动拥有所有权限"
            description="权限由系统统一授予，不可手动修改。"
            show-icon
          />

          <div class="perm-view">
            <div v-if="viewDirectPermsFor(role).length === 0" class="no-perm">未配置权限</div>
            <div
              v-for="perm in viewDirectPermsFor(role)"
              :key="perm"
              class="perm-group"
            >
              <div class="perm-direct">
                <el-tag size="small">{{ perm }}</el-tag>
                <span v-if="defMap.get(perm)?.children.length" class="perm-implies">→ 包含: {{ getAllChildren(perm).join(', ') }}</span>
              </div>
            </div>
          </div>
        </template>

        <!-- ============ 编辑模式 ============ -->
        <template v-else>
          <div class="role-header">
            <div>
              <el-input v-model="editForm.name" size="small" placeholder="角色名称" class="edit-name-input" />
              <el-tag size="small" class="role-code-tag">{{ role.code }}</el-tag>
            </div>
            <div class="role-header-actions">
              <el-button size="small" @click="cancelEdit">取消</el-button>
              <el-button size="small" type="primary" :loading="saving" @click="saveEdit(role.code!)">保存</el-button>
            </div>
          </div>
          <el-input v-model="editForm.description" size="small" placeholder="角色描述" class="edit-desc-input" />

          <div class="perm-section">
            <p class="perm-label">直接权限 <span class="perm-hint">点击 × 删除（含继承子权限）</span></p>
            <div v-if="editDirectPerms.length > 0" class="perm-tags">
              <el-tag
                v-for="perm in editDirectPerms"
                :key="perm"
                size="small"
                closable
                :disable-transitions="true"
                @close="removePermission(perm)"
              >
                {{ perm }}
              </el-tag>
            </div>
            <span v-else class="no-perm">暂无直接权限</span>
          </div>

          <div v-if="editInheritedPerms.length > 0" class="perm-section">
            <p class="perm-label inherited-label">继承权限 <span class="perm-hint">由上级权限自动获得，不可单独删除</span></p>
            <div class="perm-tags">
              <el-tag
                v-for="perm in editInheritedPerms"
                :key="perm"
                size="small"
                type="info"
                effect="plain"
                :disable-transitions="true"
              >
                {{ perm }}
              </el-tag>
            </div>
          </div>

          <div class="perm-add">
            <el-select
              size="small"
              placeholder="添加权限"
              class="perm-add-select"
              :model-value="''"
              @change="addPermission"
            >
              <el-option-group v-for="group in groupedOptions" :key="group.label" :label="group.label">
                <el-option
                  v-for="opt in group.options"
                  :key="opt.value"
                  :value="opt.value"
                  :disabled="isImpliedBy(opt.value)"
                >
                  <span>{{ opt.value }}</span>
                  <span v-if="opt.children.length" class="option-hint"> — 包含 {{ opt.children.join(', ') }}</span>
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
.eyebrow { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.12em; }
h2 { margin: 0; font-size: 28px; }
.subtitle { margin: 8px 0 0; color: var(--text-secondary); }
.empty-wrap { padding: 40px 0; }
.roles-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.role-card { display: flex; flex-direction: column; gap: 12px; }
.role-header { display: flex; gap: 12px; align-items: flex-start; justify-content: space-between; }
.role-name { display: inline-block; margin-right: 8px; font-size: 16px; }
.role-code-tag { vertical-align: middle; }
.role-order { font-size: 12px; color: var(--text-tertiary); white-space: nowrap; }
.role-desc { margin: 0; font-size: 13px; line-height: 1.6; color: var(--text-secondary); }
.admin-note { margin: 4px 0; }
.role-header-actions { display: flex; gap: 8px; align-items: center; }
.edit-name-input { display: inline-block; width: 160px; margin-right: 8px; }
.edit-desc-input { margin-top: 4px; }
.perm-section { margin-top: 4px; }
.perm-label { margin: 0 0 8px; font-size: 12px; color: var(--text-tertiary); text-transform: uppercase; letter-spacing: 0.06em; }
.inherited-label { color: var(--text-tertiary); opacity: 0.7; }
.perm-hint { font-weight: 400; color: var(--text-hint); text-transform: none; letter-spacing: normal; }
.perm-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.no-perm { font-size: 12px; color: var(--text-hint); }
.perm-view { display: flex; flex-direction: column; gap: 6px; }
.perm-group { display: flex; flex-direction: column; gap: 2px; }
.perm-direct { display: flex; gap: 8px; align-items: center; }
.perm-implies { font-size: 11px; color: var(--text-tertiary); }
.perm-add { display: flex; gap: 6px; margin-top: 8px; }
.perm-add-select { flex: 1; max-width: 360px; }
.option-hint { font-size: 11px; color: var(--text-tertiary); }
</style>
