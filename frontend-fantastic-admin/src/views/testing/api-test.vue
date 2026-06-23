<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { runApiTest } from '@/api/modules/testing'
import type { ApiTestResponse } from '@/api/types'

defineOptions({ name: 'ApiTestPage' })

const loading = ref(false)
const response = ref<ApiTestResponse | null>(null)
const activeTab = ref<'body' | 'headers'>('body')

const form = reactive({
  method: 'GET',
  url: 'http://localhost:18045/api/v1/system/health',
  headersRaw: '',
  body: '',
})

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']

async function execute() {
  if (!form.url.trim()) {
    ElMessage.warning('请输入 URL')
    return
  }
  loading.value = true
  response.value = null
  try {
    let headers: Record<string, string> | undefined
    if (form.headersRaw.trim()) {
      headers = {}
      form.headersRaw.trim().split('\n').forEach((line) => {
        const idx = line.indexOf(':')
        if (idx > 0) {
          headers![line.slice(0, idx).trim()] = line.slice(idx + 1).trim()
        }
      })
    }
    const apiRes = await runApiTest({
      url: form.url,
      method: form.method,
      headers,
      body: ['POST', 'PUT', 'PATCH'].includes(form.method) ? form.body : undefined,
    })
    response.value = (apiRes?.data || null) as ApiTestResponse | null
  } catch (error: any) {
    ElMessage.error(error?.message || '请求失败')
  } finally {
    loading.value = false
  }
}

function statusColor(code?: number) {
  if (!code) return 'info'
  if (code < 300) return 'success'
  if (code < 400) return 'warning'
  return 'danger'
}

function prettyBody(body?: string) {
  if (!body) return ''
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body
  }
}
</script>

<template>
  <div class="grid gap-4">
    <el-card shadow="never">
      <template #header>
        <span>接口调试</span>
      </template>
      <el-form :model="form" label-width="0">
        <div class="flex gap-2 mb-3">
          <el-select v-model="form.method" class="w-30" size="large">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
          <el-input v-model="form.url" placeholder="请求 URL" size="large" clearable />
          <el-button type="primary" :loading="loading" size="large" @click="execute">
            发送
          </el-button>
        </div>

        <el-collapse>
          <el-collapse-item title="Headers" name="headers">
            <el-input
              v-model="form.headersRaw"
              type="textarea"
              :rows="4"
              placeholder="Content-Type: application/json&#10;Authorization: Bearer xxx"
            />
          </el-collapse-item>
          <el-collapse-item v-if="['POST', 'PUT', 'PATCH'].includes(form.method)" title="Body" name="body">
            <el-input
              v-model="form.body"
              type="textarea"
              :rows="6"
              placeholder='{"key": "value"}'
            />
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </el-card>

    <el-card v-if="response" shadow="never">
      <template #header>
        <div class="flex items-center gap-3">
          <span>响应</span>
          <el-tag :type="statusColor(response.statusCode)" size="small">
            {{ response.statusCode || '-' }}
          </el-tag>
          <el-tag v-if="response.latencyMs != null" type="info" size="small">
            {{ response.latencyMs }}ms
          </el-tag>
          <el-tag v-if="response.error" type="danger" size="small">
            ERROR
          </el-tag>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="响应 Body" name="body">
          <pre class="response-body">{{ response.error || prettyBody(response.body) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="响应 Headers" name="headers">
          <div class="grid gap-1 text-sm font-mono">
            <div v-for="(v, k) in response.responseHeaders" :key="k" class="flex gap-2">
              <span class="color-#64748b">{{ k }}:</span>
              <span>{{ v }}</span>
            </div>
            <el-empty v-if="!response.responseHeaders || !Object.keys(response.responseHeaders).length" description="无响应头" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.response-body {
  margin: 0;
  padding: 12px;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}
</style>
