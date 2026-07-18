<script setup lang="ts">
import type { ExternalArchiveSession } from '@/api/modules/external-archive'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  exchangeExternalArchiveTicket,
  getExternalArchiveContext,
} from '@/api/modules/external-archive'
import ArchivePage from './archive.vue'

const EXTERNAL_SESSION_STORAGE_KEY = 'MRR-EXTERNAL-ARCHIVE:session'
const route = useRoute()
const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')

function firstQueryValue(value: unknown): string {
  return String(Array.isArray(value) ? value[0] : value ?? '').trim()
}

function persistSession(session: ExternalArchiveSession) {
  sessionStorage.setItem(EXTERNAL_SESSION_STORAGE_KEY, JSON.stringify(session))
}

async function initialize() {
  loading.value = true
  errorMessage.value = ''
  try {
    const ticket = firstQueryValue(route.query.ticket)
    const response = ticket
      ? await exchangeExternalArchiveTicket(ticket)
      : await getExternalArchiveContext()
    const session = response.data
    if (!session?.cases?.length) {
      throw new Error('外部系统未授权任何可访问的影像病案')
    }
    persistSession(session)

    const currentBah = firstQueryValue(route.query.bah)
    const currentSjh = firstQueryValue(route.query.sjh)
    const selected = session.cases.find(item => item.bah === currentBah && (item.sjh || '') === currentSjh)
      || session.cases[0]

    await router.replace({
      path: '/archive/external',
      query: {
        bah: selected.bah,
        ...(selected.sjh ? { sjh: selected.sjh } : {}),
      },
    })
  }
  catch (error: unknown) {
    sessionStorage.removeItem(EXTERNAL_SESSION_STORAGE_KEY)
    errorMessage.value = (error as { message?: string })?.message || '外部影像访问链接无效或已过期'
  }
  finally {
    loading.value = false
  }
}

onMounted(initialize)
</script>

<template>
  <div v-if="loading" class="external-archive-state">
    <el-result icon="info" title="正在验证外部影像访问票据" sub-title="验证完成后将自动打开影像档案袋" />
  </div>
  <div v-else-if="errorMessage" class="external-archive-state">
    <el-result icon="error" title="无法访问影像档案袋" :sub-title="errorMessage" />
  </div>
  <ArchivePage v-else />
</template>

<style scoped>
.external-archive-state {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: var(--surface-muted);
}
</style>
