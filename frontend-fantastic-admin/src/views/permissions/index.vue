<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

defineOptions({ name: 'PermissionsPage' })

const loading = ref(false)

async function loadPermissions() {
  loading.value = true
  try {
    // TODO: 实现权限列表加载
  }
  catch (error: any) {
    ElMessage.error(error?.message || '权限列表加载失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadPermissions)
</script>

<template>
  <div class="page-shell">
    <div>
      <p class="eyebrow">
        Permission Management
      </p>
      <h2>权限管理</h2>
      <p class="subtitle">
        配置角色权限与访问控制策略。
      </p>
    </div>

    <el-card shadow="never" :loading="loading">
      <template #header>
        权限配置
      </template>
      <el-empty description="权限管理功能开发中" />
    </el-card>
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 20px;
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
</style>
