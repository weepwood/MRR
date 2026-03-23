<template>
  <div class="users-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="$emit('add-user')">
            <el-icon><Plus /></el-icon>
            添加用户
          </el-button>
        </div>
      </template>
      
      <el-table :data="users" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色">
          <template #default="scope">
            <el-tag :type="getRoleType(scope.row.role)">
              {{ scope.row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">
              {{ scope.row.status === 'active' ? '活跃' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLogin" label="最后登录" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="$emit('edit-user', scope.row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              @click="$emit('delete-user', scope.row)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

defineProps({
  users: {
    type: Array,
    required: true
  }
})

defineEmits(['edit-user', 'delete-user', 'add-user'])

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(100)

const getRoleType = (role) => {
  const roleTypes = {
    '管理员': 'danger',
    '医生': 'primary',
    '护士': 'success',
    '普通用户': 'info'
  }
  return roleTypes[role] || 'info'
}

const handleSizeChange = (val) => {
  pageSize.value = val
  // 这里可以触发重新加载数据
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  // 这里可以触发重新加载数据
}
</script>

<style scoped>
.users-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .pagination-container {
    overflow-x: auto;
  }
}
</style>
