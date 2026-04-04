<script setup lang="ts">
import type { AuthRole } from '@/api/types'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import apiUser from '@/api/modules/user'

defineOptions({ name: 'PermissionsPage' })

const loading = ref(false)
const roles = ref<AuthRole[]>([])

// 按 sortOrder 升序排列
const sortedRoles = computed(() =>
  [...roles.value].sort((a, b) => (a.sortOrder ?? 99) - (b.sortOrder ?? 99)),
)

function splitPermissions(raw: string | undefined): string[] {
  if (!raw) { return [] }
  return raw.split(',').map(p => p.trim()).filter(Boolean)
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
          查看系统内各角色及其权限配置（只读）。角色写入功能请通过后端直接配置数据库。
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
        <div class="role-header">
          <div>
            <strong class="role-name">{{ role.name || role.code }}</strong>
            <el-tag size="small" class="role-code-tag">
              {{ role.code }}
            </el-tag>
          </div>
          <span v-if="role.sortOrder !== undefined" class="role-order">
            排序 {{ role.sortOrder }}
          </span>
        </div>

        <p v-if="role.description" class="role-desc">
          {{ role.description }}
        </p>

        <div class="perm-section">
          <p class="perm-label">
            权限列表
          </p>
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
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
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
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.role-name {
  font-size: 16px;
  display: inline-block;
  margin-right: 8px;
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
  color: #64748b;
  line-height: 1.6;
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
</style>
