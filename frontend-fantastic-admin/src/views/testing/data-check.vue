<script setup lang="ts">
import type { DataCheckItem } from '@/api/types'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { runDataCheck } from '@/api/modules/testing'

defineOptions({ name: 'DataCheckPage' })

const loading = ref(false)
const items = ref<DataCheckItem[]>([])

async function execute() {
  loading.value = true
  try {
    const res = await runDataCheck()
    items.value = (res?.data || []) as DataCheckItem[]
    const failed = items.value.filter(i => i.status === 'FAIL')
    const warned = items.value.filter(i => i.status === 'WARN')
    if (failed.length === 0 && warned.length === 0) {
      ElMessage.success('数据完整性检查全部通过')
    }
    else {
      const parts: string[] = []
      if (failed.length) { parts.push(`${failed.length} 项失败`) }
      if (warned.length) { parts.push(`${warned.length} 项告警`) }
      ElMessage.warning(parts.join('，'))
    }
  }
  catch (error: any) {
    ElMessage.error(error?.message || '数据检查执行失败')
  }
  finally {
    loading.value = false
  }
}

function tagType(status?: string) {
  if (status === 'PASS') { return 'success' }
  if (status === 'WARN') { return 'warning' }
  if (status === 'FAIL' || status === 'ERROR') { return 'danger' }
  return 'info'
}

const expandedRows = ref<string[]>([])
</script>

<template>
  <div>
    <div class="mb-4 flex items-center gap-3">
      <el-button type="primary" :loading="loading" @click="execute">
        {{ loading ? '检查中...' : '执行数据检查' }}
      </el-button>
      <span class="text-sm text-muted-foreground">检查数据完整性、一致性、异常记录</span>
    </div>

    <div v-if="items.length" class="grid gap-3">
      <el-card
        v-for="item in items"
        :key="item.checkName"
        shadow="never"
        :class="item.status === 'PASS' ? 'border-l-4 border-l-green-500' : item.status === 'WARN' ? 'border-l-4 border-l-yellow-500' : item.status === 'FAIL' ? 'border-l-4 border-l-red-500' : ''"
      >
        <div class="flex items-center justify-between">
          <div>
            <strong>{{ item.checkName }}</strong>
            <p class="mt-1 text-sm text-muted-foreground">
              {{ item.summary }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <el-tag v-if="item.issueCount" type="warning">
              {{ item.issueCount }} 个问题
            </el-tag>
            <el-tag :type="tagType(item.status)" size="large">
              {{ item.status === 'PASS' ? '通过' : item.status === 'WARN' ? '告警' : item.status === 'FAIL' ? '失败' : item.status === 'ERROR' ? '错误' : '-' }}
            </el-tag>
          </div>
        </div>

        <el-collapse v-if="item.details?.length" v-model="expandedRows" class="mt-3">
          <el-collapse-item title="查看详情" :name="item.checkName">
            <ul class="grid list-disc gap-1 pl-5 text-sm text-muted-foreground">
              <li v-for="(d, i) in item.details" :key="i">
                {{ d }}
              </li>
            </ul>
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </div>

    <el-empty v-else-if="!loading" description="点击「执行数据检查」开始检测" />
  </div>
</template>
