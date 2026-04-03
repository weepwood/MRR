<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'

defineOptions({ name: 'UsersPage' })

const loading = ref(false)
const users = ref<any[]>([])

async function loadUsers() {
  loading.value = true
  try {
    // TODO: 实现用户列表加载
    users.value = []
  }
  catch (error: any) {
    ElMessage.error(error?.message || '用户列表加载失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadUsers)
</script>

<template>
  <div class="page-shell">
    <div>
      <p class="eyebrow">
        User Management
      </p>
      <h2>用户管理</h2>
      <p class="subtitle">
        管理系统用户账户、角色分配与权限控制。
      </p>
    </div>

    <el-card shadow="never" :loading="loading">
      <template #header>
        用户列表
      </template>
      <el-empty description="用户管理功能开发中" />
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
