<script setup lang="ts">
import type { ExternalArchiveSession } from '@/api/modules/external-archive'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  exchangeExternalArchiveTicket,
  getExternalArchiveContext,
} from '@/api/modules/external-archive'

const EXTERNAL_SESSION_STORAGE_KEY = 'MRR-EXTERNAL-ARCHIVE:session'
const DEFAULT_REQUEST_TIMEOUT = 60_000
const EXTENDED_REQUEST_TIMEOUT = 180_000
const RECOVERABLE_ERROR_CODES = new Set(['ECONNABORTED', 'ETIMEDOUT', 'ERR_NETWORK'])
const RECOVERABLE_HTTP_STATUS = new Set([408, 425, 429, 502, 503, 504])

interface InitializeOptions {
  timeout?: number
  preferExistingContext?: boolean
}

interface ExternalArchiveError {
  code?: string
  isAxiosError?: boolean
  message?: string
  response?: {
    status?: number
  }
}

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')
const recoverableError = ref(false)

function firstQueryValue(value: unknown): string {
  return String(Array.isArray(value) ? value[0] : value ?? '').trim()
}

function persistSession(value: ExternalArchiveSession) {
  sessionStorage.setItem(EXTERNAL_SESSION_STORAGE_KEY, JSON.stringify(value))
}

function isRecoverableError(error: unknown): boolean {
  const candidate = error as ExternalArchiveError
  if (candidate.code && RECOVERABLE_ERROR_CODES.has(candidate.code)) {
    return true
  }

  const status = candidate.response?.status
  if (status && RECOVERABLE_HTTP_STATUS.has(status)) {
    return true
  }

  if (candidate.isAxiosError && !candidate.response) {
    return true
  }

  return /timeout|timed out|network error/i.test(candidate.message || '')
}

async function requestExternalSession(ticket: string, options: Required<InitializeOptions>) {
  const requestOptions = { timeout: options.timeout }
  if (!ticket) {
    return getExternalArchiveContext(requestOptions)
  }

  if (options.preferExistingContext) {
    try {
      return await getExternalArchiveContext(requestOptions)
    }
    catch (error: unknown) {
      if (isRecoverableError(error)) {
        throw error
      }
    }
  }

  return exchangeExternalArchiveTicket(ticket, requestOptions)
}

async function initialize(options: InitializeOptions = {}) {
  const resolvedOptions: Required<InitializeOptions> = {
    timeout: options.timeout ?? DEFAULT_REQUEST_TIMEOUT,
    preferExistingContext: options.preferExistingContext ?? false,
  }

  loading.value = true
  errorMessage.value = ''
  recoverableError.value = false
  try {
    const ticket = firstQueryValue(route.query.ticket)
    const response = await requestExternalSession(ticket, resolvedOptions)
    const nextSession = response.data
    if (!nextSession?.cases?.length) {
      throw new Error('外部系统未授权任何可访问的影像病案')
    }
    persistSession(nextSession)

    const currentBah = firstQueryValue(route.query.bah)
    const currentSjh = firstQueryValue(route.query.sjh)
    const selected = nextSession.cases.find(item => item.bah === currentBah && (item.sjh || '') === currentSjh)
      || nextSession.cases[0]

    await router.replace({
      path: '/archive',
      query: {
        external: 'ticket',
        bah: selected.bah,
        ...(selected.sjh ? { sjh: selected.sjh } : {}),
      },
    })
  }
  catch (error: unknown) {
    if (isRecoverableError(error)) {
      recoverableError.value = true
      errorMessage.value = '网络较慢或服务暂时没有响应。你可以继续等待，或重新发起访问。'
      return
    }

    sessionStorage.removeItem(EXTERNAL_SESSION_STORAGE_KEY)
    errorMessage.value = (error as ExternalArchiveError)?.message || '外部影像访问链接无效或已过期'
  }
  finally {
    loading.value = false
  }
}

function continueWaiting() {
  void initialize({
    timeout: EXTENDED_REQUEST_TIMEOUT,
    preferExistingContext: true,
  })
}

function retry() {
  void initialize({
    timeout: DEFAULT_REQUEST_TIMEOUT,
    preferExistingContext: true,
  })
}

function goBack() {
  router.back()
}

onMounted(() => void initialize())
</script>

<template>
  <div v-if="loading" class="external-archive-state">
    <el-result icon="info" title="正在验证外部影像访问票据" sub-title="验证完成后将自动打开影像档案袋" />
  </div>
  <div v-else-if="recoverableError" class="external-archive-state">
    <el-result icon="warning" title="连接时间较长" :sub-title="errorMessage">
      <template #extra>
        <div class="recovery-actions">
          <el-button type="primary" @click="continueWaiting">
            继续等待
          </el-button>
          <el-button @click="retry">
            重新尝试
          </el-button>
          <el-button @click="goBack">
            返回上一页
          </el-button>
        </div>
      </template>
    </el-result>
  </div>
  <div v-else-if="errorMessage" class="external-archive-state">
    <el-result icon="error" title="无法访问影像档案袋" :sub-title="errorMessage" />
  </div>
</template>

<style scoped>
.external-archive-state {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: var(--surface-muted);
}

.recovery-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
</style>
