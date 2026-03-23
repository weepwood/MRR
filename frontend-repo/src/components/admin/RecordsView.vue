<template>
  <div class="records-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>病案管理</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              @keyup.enter="handleSearch"
              placeholder="搜索病案号或患者姓名"
              style="width: 300px; margin-right: 10px;"
              clearable
            >
              <template #append>
                <el-button @click="handleSearch">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
            <el-button type="primary" @click="exportRecords" :loading="exporting">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
            <el-button type="success" @click="refreshData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <el-table 
        :data="records" 
        style="width: 100%" 
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="bah" label="病案号" width="100" />
        <el-table-column prop="brxh" label="病人序号" width="100" />
        <el-table-column prop="filename" label="文件名" />
        <el-table-column prop="btype" label="类型" width="150">
          <template #default="scope">
            <el-tag :type="getBtypeType(scope.row.btype)">
              {{ getBtypeText(scope.row.btype) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pages" label="页码" width="80" />
        <el-table-column prop="openerNo" label="扫描负责人" width="100" />
        <el-table-column prop="folder" label="文件夹" width="100" />
        <el-table-column prop="uploadDate" label="扫描日期" width="160">
          <template #default="scope">
            {{ formatDate(scope.row.uploadDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadFlag" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.uploadFlag === 1 ? 'success' : 'warning'">
              {{ scope.row.uploadFlag === 1 ? '正常' : '删除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="scope">
            <el-button 
              size="small" 
              @click="viewRecord(scope.row)"
              type="primary"
              link
            >
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button 
              size="small" 
              @click="editRecord(scope.row)"
              type="primary"
              link
            >
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button 
              size="small" 
              @click="downloadRecord(scope.row)"
              type="success"
              link
            >
              <el-icon><Download /></el-icon>
              下载
            </el-button>
            <el-button 
              size="small" 
              @click="handleDeleteRecord(scope.row)"
              type="danger"
              link
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
    
    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="病案详情"
      width="60%"
    >
      <el-descriptions :column="2" border v-if="currentRecord">
        <el-descriptions-item label="病案号">{{ currentRecord.bah }}</el-descriptions-item>
        <el-descriptions-item label="病人序号">{{ currentRecord.brxh }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ currentRecord.filename }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="getBtypeType(currentRecord.btype)">
            {{ getBtypeText(currentRecord.btype) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="页数">{{ currentRecord.pages }}</el-descriptions-item>
        <el-descriptions-item label="扫描负责人">{{ currentRecord.openerNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentRecord.uploadFlag === 1 ? 'success' : 'warning'">
            {{ currentRecord.uploadFlag === 1 ? '正常' : '删除' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件夹">{{ currentRecord.folder }}</el-descriptions-item>
        <el-descriptions-item label="上传日期" :span="2">
          {{ formatDate(currentRecord.uploadDate) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    
    <!-- 编辑对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑病案"
      width="50%"
    >
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="病案号">
          <el-input v-model="editForm.bah" :disabled="true" />
        </el-form-item>
        <el-form-item label="病人序号">
          <el-input v-model="editForm.brxh" :disabled="true" />
        </el-form-item>
        <el-form-item label="文件名">
          <el-input v-model="editForm.filename" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editForm.btype" style="width: 100%">
            <el-option label="01-病案首页" :value="1" />
            <el-option label="02-病程记录" :value="2" />
            <el-option label="03-手术记录" :value="3" />
            <el-option label="04-术后病程录" :value="4" />
            <el-option label="05-护理记录" :value="5" />
            <el-option label="06-会诊单" :value="6" />
            <el-option label="07-特殊检查" :value="7" />
            <el-option label="08-检验单" :value="8" />
            <el-option label="09-医嘱" :value="9" />
            <el-option label="10-体温单" :value="10" />
            <el-option label="12-出院记录" :value="12" />
            <el-option label="13-大病历" :value="13" />
            <el-option label="14-其它" :value="14" />
          </el-select>
        </el-form-item>
        <el-form-item label="页数">
          <el-input-number v-model="editForm.pages" :min="1" />
        </el-form-item>
        <el-form-item label="扫描负责人">
          <el-input v-model="editForm.openerNo" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, View, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import { getRecords, updateRecord, deleteRecord } from '@/utils/api'

const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const records = ref([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedRows = ref([])

// 对话框控制
const dialogVisible = ref(false)
const editDialogVisible = ref(false)
const currentRecord = ref(null)
const editForm = ref({})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    
    // 如果有搜索关键词，添加到参数中
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    
    const response = await getRecords(params)
    
    // 处理响应数据
    if (response.data && response.data.code === 200) {
      const responseData = response.data.data
      // 判断返回数据结构
      if (responseData && Array.isArray(responseData.records)) {
        // 如果是分页对象 { records: [], total: 100 }
        records.value = responseData.records
        total.value = responseData.total || responseData.records.length
      } else if (responseData && Array.isArray(responseData.list)) {
        // 如果是分页对象 { list: [], total: 100, page: 1, size: 10 }
        records.value = responseData.list
        total.value = responseData.total || responseData.list.length
      } else if (Array.isArray(responseData)) {
        // 如果直接是数组
        records.value = responseData
        total.value = responseData.length
      } else if (responseData && typeof responseData === 'object') {
        // 如果是单个对象，转为数组
        records.value = [responseData]
        total.value = 1
      } else {
        console.warn('⚠️ 未识别的数据格式:', responseData)
        ElMessage.warning('未获取到有效数据')
        records.value = []
        total.value = 0
      }
      
      if (records.value.length === 0) {
        ElMessage.info('暂无数据')
      }
    } else {
      console.warn('⚠️ 响应码不是 200:', response.data)
      ElMessage.warning('未获取到数据')
      records.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('❌ 加载病案数据失败:', error)
    console.error('❌ 错误详情:', error.response?.data || error.message)
    ElMessage.error('加载病案数据失败：' + (error.message || '未知错误'))
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

// 刷新数据
const refreshData = () => {
  loadData()
}

// 分页处理
const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadData()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadData()
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

// 查看详情
const viewRecord = (row) => {
  currentRecord.value = row
  dialogVisible.value = true
}

// 编辑病案
const editRecord = (row) => {
  editForm.value = { ...row }
  editDialogVisible.value = true
}

// 保存编辑
const saveEdit = async () => {
  saving.value = true
  try {
    const { id, bah, brxh, filename, btype, pages, openerNo } = editForm.value
    await updateRecord(id, { bah, brxh, filename, btype, pages, openerNo })
    ElMessage.success('更新成功')
    editDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('更新病案失败:', error)
    ElMessage.error('更新病案失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 删除病案
const handleDeleteRecord = (row) => {
  ElMessageBox.confirm(
    `确定要删除病案 "${row.bah}" 吗？`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await deleteRecord(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error('删除病案失败:', error)
      ElMessage.error('删除病案失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {})
}

// 下载病案
const downloadRecord = (row) => {
  ElMessage.info(`开始下载病案 ${row.bah}`)
  // TODO: 实现下载逻辑
}

// 导出病案
const exportRecords = async () => {
  exporting.value = true
  try {
    // TODO: 实现导出逻辑
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败：' + (error.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取类型样式
const getBtypeType = (btype) => {
  const types = {
    1: 'primary',
    2: 'success',
    3: 'warning',
    4: 'danger',
    5: 'info',
    6: '',
    7: 'primary',
    8: 'success',
    9: 'warning',
    10: 'danger',
    12: 'info',
    13: '',
    14: 'info'
  }
  return types[btype] || 'info'
}

// 获取类型文本
const getBtypeText = (btype) => {
  const texts = {
    1: '01-病案首页',
    2: '02-病程记录',
    3: '03-手术记录',
    4: '04-术后病程录',
    5: '05-护理记录',
    6: '06-会诊单',
    7: '07-特殊检查',
    8: '08-检验单',
    9: '09-医嘱',
    10: '10-体温单',
    12: '12-出院记录',
    13: '13-大病历',
    14: '14-其它'
  }
  return texts[btype] || '未知'
}

// 初始化加载
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.records-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
  
  .header-actions {
    flex-direction: column;
    gap: 10px;
  }
  
  .header-actions .el-input {
    width: 100% !important;
    margin-right: 0 !important;
  }
}

/* 按钮样式 - 现代化圆角设计 */
:deep(.el-button) {
  border-radius: 12px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.2);
}

:deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

:deep(.el-button--success) {
  background: linear-gradient(135deg, #34c759 0%, #28a745 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(52, 199, 89, 0.2);
}

:deep(.el-button--success:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(52, 199, 89, 0.3);
}

:deep(.el-button--warning) {
  background: linear-gradient(135deg, #ff9500 0%, #ff7f00 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(255, 149, 0, 0.2);
}

:deep(.el-button--warning:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 149, 0, 0.3);
}

:deep(.el-button--danger) {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff3b30 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.2);
}

:deep(.el-button--danger:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.3);
}

:deep(.el-button--info) {
  background: linear-gradient(135deg, #8e8e93 0%, #636366 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(142, 142, 147, 0.2);
}

:deep(.el-button--info:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(142, 142, 147, 0.3);
}
</style>
