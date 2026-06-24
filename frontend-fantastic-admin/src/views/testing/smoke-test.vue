<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { runSmokeTests } from '@/api/modules/testing'
import type { SmokeTestItem } from '@/api/types'

defineOptions({ name: 'SmokeTestPage' })

const loading = ref(false)
const results = ref<SmokeTestItem[]>([])

async function execute() {
  loading.value = true
  try {
    const res = await runSmokeTests()
    results.value = (res?.data || []) as SmokeTestItem[]
    const failed = results.value.filter(r => r.status === 'FAIL' || r.status === 'ERROR')
    if (failed.length === 0) {
      ElMessage.success('冒烟测试全部通过')
    } else {
      ElMessage.warning(`${failed.length} 项检查未通过`)
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '冒烟测试执行失败')
  } finally {
    loading.value = false
  }
}

function tagType(status?: string) {
  if (status === 'PASS') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'FAIL' || status === 'ERROR') return 'danger'
  return 'info'
}
</script>

<template>
  <div>
    <div class="flex items-center gap-3 mb-4">
      <el-button type="primary" :loading="loading" @click="execute">
        {{ loading ? '执行中...' : '执行冒烟测试' }}
      </el-button>
      <span class="text-sm text-muted-foreground">检测数据库、JVM 内存、OSS 配置、API 自检</span>
    </div>

    <div v-if="results.length" class="grid gap-3">
      <article
        v-for="item in results"
        :key="item.name"
        class="flex items-center justify-between p-4 rd-3"
        :class="item.status === 'PASS' ? 'bg-#f0fdf4' : item.status === 'WARN' ? 'bg-#fffbeb' : item.status === 'SKIP' ? 'bg-#f8fafc' : 'bg-#fef2f2'"
      >
        <div>
          <strong>{{ item.name }}</strong>
          <p class="mt-1 text-sm text-muted-foreground">{{ item.detail }}</p>
        </div>
        <el-tag :type="tagType(item.status)" size="large">
          {{ item.status === 'PASS' ? '通过' : item.status === 'WARN' ? '告警' : item.status === 'SKIP' ? '跳过' : '失败' }}
        </el-tag>
      </article>
    </div>

    <el-empty v-else-if="!loading" description="点击「执行冒烟测试」开始检测" />
  </div>
</template>
