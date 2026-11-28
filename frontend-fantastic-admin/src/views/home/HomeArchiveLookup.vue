<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArchiveCasesByIdCard } from '@/api/modules/search'
import { useUserStore } from '@/store/modules/user'
import { describeIdCardInput, ID_CARD_PATTERN, normalizeIdCardInput } from '@/utils/identifier-input'
import { getArchiveLookupValidationMessage, normalizeMedicalRecordCode } from '@/utils/medical-record-code'
import { hasPermission } from '@/utils/session'
import ArchiveSearchBar from '@/views/statistics/archive/components/ArchiveSearchBar.vue'

defineOptions({ name: 'HomeArchiveLookup' })
const props = withDefaults(defineProps<{ size?: 'small' | 'wide' | 'large', editing?: boolean }>(), { size: 'wide', editing: false })
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const form = reactive({ bah: '', sjh: '', idCard: '' })
const errorMessage = ref('')
const loading = ref(false)
let generation = 0
let controller: AbortController | undefined

function cancelLookup() {
  generation++
  controller?.abort()
  controller = undefined
  loading.value = false
}
function clearSensitive() {
  cancelLookup()
  form.idCard = ''
  errorMessage.value = ''
}
watch(() => [form.bah, form.sjh, form.idCard], () => {
  cancelLookup()
  errorMessage.value = ''
}, { flush: 'sync' })
watch(() => [userStore.sessionEpoch, userStore.isSessionVerified, hasPermission('record:read'), route.path, props.editing], clearSensitive, { flush: 'sync' })
onDeactivated(clearSensitive)
onBeforeUnmount(clearSensitive)

async function submit() {
  if (props.editing || loading.value || !userStore.isSessionVerified || !hasPermission('record:read')) { return }
  const bah = normalizeMedicalRecordCode(form.bah)
  const sjh = normalizeMedicalRecordCode(form.sjh)
  const idCard = normalizeIdCardInput(form.idCard)
  errorMessage.value = ''
  if (idCard) {
    if (!ID_CARD_PATTERN.test(idCard)) {
      errorMessage.value = describeIdCardInput(idCard).message || '请输入正确的 15 位或 18 位身份证号'
      return
    }
    const revision = ++generation
    controller = new AbortController()
    loading.value = true
    const valid = () => revision === generation && userStore.isSessionVerified && hasPermission('record:read') && !props.editing && route.path === '/'
    try {
      const response = await getArchiveCasesByIdCard(idCard, controller.signal)
      if (!valid()) { return }
      const result = response.data
      const first = result?.cases?.[0]
      if (!result?.token || !first) {
        errorMessage.value = '未查询到该患者的影像病案'
        return
      }
      const firstBah = normalizeMedicalRecordCode(first.bah)
      const firstSjh = normalizeMedicalRecordCode(first.sjh)
      // Only the server-issued token and case identifiers enter the route.
      await router.push({ path: '/archive/embed', query: { id: result.token, bah: firstBah, ...(firstSjh ? { sjh: firstSjh } : {}) } })
      form.idCard = ''
    }
    catch {
      if (valid()) { errorMessage.value = '身份证查询失败，请重试' }
    }
    finally {
      if (revision === generation) {
        loading.value = false
        controller = undefined
      }
    }
    return
  }
  errorMessage.value = getArchiveLookupValidationMessage(bah, sjh)
  if (errorMessage.value) { return }
  await router.push({ path: '/archive/embed', query: { ...(bah ? { bah } : {}), ...(sjh ? { sjh } : {}) } })
}
</script>

<template>
  <section class="home-lookup" aria-label="病案影像查询" @click.stop @keydown.stop>
    <ArchiveSearchBar
      v-model:search-bah="form.bah"
      v-model:search-sjh="form.sjh"
      v-model:search-id-card="form.idCard"
      labelled
      embedded
      :disabled="props.editing"
      :loading="loading"
      error-id="home-lookup-error"
      @search="submit"
    />
    <p id="home-lookup-error" class="home-lookup__error" :class="{ 'sr-only': !errorMessage }" role="alert" aria-live="polite">
      {{ errorMessage }}
    </p>
  </section>
</template>

<style scoped>
.home-lookup {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
}

.home-lookup__error {
  margin: 0;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-destructive);
}
</style>
