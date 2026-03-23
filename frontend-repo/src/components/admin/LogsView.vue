<template>
  <div class="logs-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统日志</span>
          <div class="header-actions">
            <el-select v-model="localLogLevel" placeholder="日志级别" style="width: 120px; margin-right: 10px;">
              <el-option label="全部" value="" />
              <el-option label="错误" value="error" />
              <el-option label="警告" value="warn" />
              <el-option label="信息" value="info" />
              <el-option label="调试" value="debug" />
            </el-select>
            <el-button @click="$emit('refresh')" type="primary">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button @click="$emit('clear')" type="danger">
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
          </div>
        </div>
      </template>
      
      <div class="logs-container">
        <div v-for="log in filteredLogs" :key="log.id" class="log-item" :class="log.level">
          <div class="log-time">{{ log.time }}</div>
          <div class="log-level">{{ log.level.toUpperCase() }}</div>
          <div class="log-message">{{ log.message }}</div>
          <div v-if="log.details" class="log-details">{{ log.details }}</div>
        </div>
        
        <div v-if="filteredLogs.length === 0" class="no-logs">
          <el-empty description="暂无日志数据" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Refresh, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  logs: {
    type: Array,
    required: true
  },
  logLevel: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['refresh', 'clear', 'filter'])

// 创建本地的logLevel响应式变量
const localLogLevel = ref(props.logLevel)

// 监听props变化，同步到本地变量
watch(() => props.logLevel, (newValue) => {
  localLogLevel.value = newValue
})

// 监听本地变量变化，触发emit
watch(localLogLevel, (newValue) => {
  emit('filter', newValue)
})

const filteredLogs = computed(() => {
  if (!localLogLevel.value) {
    return props.logs
  }
  return props.logs.filter(log => log.level === localLogLevel.value)
})
</script>

<style scoped>
.logs-view {
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
}

.logs-container {
  max-height: 600px;
  overflow-y: auto;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  background: #fafafa;
}

.log-item {
  display: grid;
  grid-template-columns: 150px 80px 1fr;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #e6e6e6;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  align-items: start;
}

.log-item:last-child {
  border-bottom: none;
}

.log-item.error {
  background: #fef2f2;
  border-left: 4px solid #f56c6c;
}

.log-item.warn {
  background: #fefbf0;
  border-left: 4px solid #e6a23c;
}

.log-item.info {
  background: #f0f9ff;
  border-left: 4px solid #409eff;
}

.log-item.debug {
  background: #f8f9fa;
  border-left: 4px solid #909399;
}

.log-time {
  color: #666;
  font-size: 12px;
}

.log-level {
  font-weight: bold;
  text-align: center;
}

.log-level.error {
  color: #f56c6c;
}

.log-level.warn {
  color: #e6a23c;
}

.log-level.info {
  color: #409eff;
}

.log-level.debug {
  color: #909399;
}

.log-message {
  color: #333;
  word-break: break-word;
}

.log-details {
  grid-column: 1 / -1;
  margin-top: 8px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  font-size: 12px;
  color: #666;
}

.no-logs {
  padding: 40px;
  text-align: center;
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
  
  .log-item {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  
  .log-time {
    font-size: 11px;
  }
  
  .log-level {
    text-align: left;
  }
}
</style>
