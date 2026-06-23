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

const permissionOptions = [
  { value: 'record:manage', label: '病案管理（完整 CRUD）', category: '病案管理' },
  { value: 'record:edit', label: '病案编辑 → 继承 病案查看', category: '病案管理' },
  { value: 'record:read', label: '病案查看', category: '病案管理' },
  { value: 'search:read', label: '病案搜索', category: '病案检索' },
  { value: 'statistics:read', label: '统计分析查看', category: '统计分析' },
  { value: 'user:manage', label: '用户管理（创建/编辑/删除）', category: '用户管理' },
  { value: 'role:manage', label: '角色管理（创建/编辑/删除）', category: '角色管理' },
  { value: 'role:read', label: '角色查看', category: '角色管理' },
  { value: 'log:read', label: '日志审计查看', category: '日志管理' },
  { value: 'system:read', label: '系统设置/监控', category: '系统管理' },
  { value: 'test:read', label: '测试中心访问', category: '测试管理' },
]

function collapsePermissions(perms: string[]): string[] {
  return perms.filter(p => {
    return !perms.some(other => other !== p && PERMISSION_HIERARCHY[other]?.includes(p))
  })
}

function isImplied(perm: string, perms: string[]): boolean {
  return perms.some(other => other !== perm && PERMISSION_HIERARCHY[other]?.includes(perm))
}

const groupedOptions = computed(() => {
  const map = new Map<string, { value: string; label: string }[]>()
  for (const opt of permissionOptions) {
    if (!map.has(opt.category))
      map.set(opt.category, [])
    map.get(opt.category)!.push({ value: opt.value, label: opt.label })
  }
  return Array.from(map.entries()).map(([label, options]) => ({ label, options }))
})

const editForm = reactive({
  name: '',
  description: '',
  permissions: [] as string[],
})

const sortedRoles = computed(() =>
  [...roles.value].sort((a, b) => (a.sortOrder ?? 99) - (b.sortOrder ?? 99)),
)

function splitPermissions(raw: string | undefined): string[] {
  if (!raw) { return [] }
  return raw.split(',').map(p => p.trim()).filter(Boolean)
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

function addPermission(perm: string) {
  if (!perm) { return }
  if (editForm.permissions.includes(perm)) {
    ElMessage.warning(`权限 "${perm}" 已存在`)
    return
  }
  editForm.permissions.push(perm)
}

function removePermission(perm: string) {
  editForm.permissions = editForm.permissions.filter(p => p !== perm)
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
      <el-button :loading="loading" @click="loadPermissions">
        刷新
      </el-button>
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
        <!-- 查看模式 -->
        <template v-if="editingCode !== role.code">
          <div class="role-header">
            <div>
              <strong class="role-name">{{ role.name || role.code }}</strong>
              <el-tag size="small" class="role-code-tag">
                {{ role.code }}
              </el-tag>
            </div>
            <div class="role-header-actions">
              <span v-if="role.sortOrder !== undefined" class="role-order">
                排序 {{ role.sortOrder }}
              </span>
              <el-button size="small" type="primary" link @click="startEdit(role)">
                编辑
              </el-button>
            </div>
          </div>

          <p v-if="role.description" class="role-desc">
            {{ role.description }}
          </p>

          <div class="perm-section">
            <p class="perm-label">权限列表</p>
            <div class="perm-tags">
              <template v-if="splitPermissions(role.permissions).length > 0">
                <el-tag
                  v-for="perm in splitPermissions(role.permissions)"
                  :key="perm"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ perm }}
                </el-tag>
              </template>
              <span v-else class="no-perm">未配置权限</span>
            </div>
          </div>
        </template>

        <!-- 编辑模式 -->
        <template v-else>
          <div class="role-header">
            <div>
              <el-input
                v-model="editForm.name"
                size="small"
                placeholder="角色名称"
                class="edit-name-input"
              />
              <el-tag size="small" class="role-code-tag">
                {{ role.code }}
              </el-tag>
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

          <el-input
            v-model="editForm.description"
            size="small"
            placeholder="角色描述"
            class="edit-desc-input"
          />

          <div class="perm-section">
            <p class="perm-label">权限列表 <span class="perm-hint">点击标签删除</span></p>
            <div class="perm-tags">
              <el-tag
                v-for="perm in editForm.permissions"
                :key="perm"
                size="small"
                closable
                :type="isImplied(perm, editForm.permissions) ? 'info' : perm.startsWith('role:') ? 'warning' : perm.startsWith('user:') ? 'success' : undefined"
                :effect="isImplied(perm, editForm.permissions) ? 'plain' : 'light'"
                :disable-transitions="true"
                @close="removePermission(perm)"
              >
                {{ perm }}
              </el-tag>
              <span v-if="editForm.permissions.length === 0" class="no-perm">暂无权限</span>
            </div>
            <div class="perm-add">
              <el-select
                size="small"
                placeholder="选择权限标识添加"
                class="perm-add-select"
                :model-value="''"
                @change="addPermission"
              >
                <el-option-group
                  v-for="group in groupedOptions"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="opt in group.options"
                    :key="opt.value"
                    :value="opt.value"
                    :label="`${opt.value} — ${opt.label}`"
                  />
                </el-option-group>
              </el-select>
            </div>
          </div>
        </template>
      </el-card>
    </div>
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

.empty-wrap {
  padding: 40px 0;
}

.roles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.role-name {
  display: inline-block;
  margin-right: 8px;
  font-size: 16px;
}

.role-code-tag {
  vertical-align: middle;
}

.role-order {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.role-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}

.perm-section {
  margin-top: 4px;
}

.perm-label {
  margin: 0 0 8px;
  font-size: 12px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.no-perm {
  font-size: 12px;
  color: #cbd5e1;
}

.role-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.edit-name-input {
  display: inline-block;
  width: 160px;
  margin-right: 8px;
}

.edit-desc-input {
  margin-top: 4px;
}

.perm-hint {
  font-weight: 400;
  color: #94a3b8;
}

.perm-add {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.perm-add-select {
  flex: 1;
  max-width: 360px;
}
</style>
