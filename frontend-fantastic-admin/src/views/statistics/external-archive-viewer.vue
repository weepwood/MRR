<script setup lang="ts">
import type { GalleryImage } from './archive/types'
import type { ExternalArchiveCase, ExternalArchiveSession } from '@/api/modules/external-archive'
import type { BAHImageData } from '@/api/types'
import { ArrowLeft, ArrowRight, Download, Printer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadExternalArchive, getExternalArchiveImages } from '@/api/modules/external-archive'
import { normalizeMedicalRecordCode } from '@/utils/medical-record-code'

const props = defineProps<{
  session: ExternalArchiveSession
}>()

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const downloading = ref(false)
const errorMessage = ref('')
const images = ref<GalleryImage[]>([])
const selectedIndex = ref(0)

function queryText(value: unknown): string {
  return String(Array.isArray(value) ? value[0] : value ?? '').trim()
}

function caseKey(item: ExternalArchiveCase): string {
  return `${normalizeMedicalRecordCode(item.bah)}:${normalizeMedicalRecordCode(item.sjh || '')}`
}

const activeCase = computed(() => {
  const bah = normalizeMedicalRecordCode(queryText(route.query.bah))
  const sjh = normalizeMedicalRecordCode(queryText(route.query.sjh))
  return props.session.cases.find(item => caseKey(item) === `${bah}:${sjh}`)
    || props.session.cases[0]
})
const activeCaseKey = computed(() => activeCase.value ? caseKey(activeCase.value) : '')
const currentImage = computed(() => images.value[selectedIndex.value] || null)
const previewUrls = computed(() => images.value.map(item => item.imageUrl || ''))

async function loadActiveCase(forceRefresh = false) {
  const selected = activeCase.value
  if (!selected) {
    images.value = []
    errorMessage.value = '当前外部会话没有可访问的病案'
    return
  }
  loading.value = true
  errorMessage.value = ''
  selectedIndex.value = 0
  try {
    const response = await getExternalArchiveImages(selected.bah, selected.sjh, forceRefresh)
    const list = Array.isArray(response.data) ? response.data : []
    images.value = list.map((item: BAHImageData) => ({
      ...item,
      bah: normalizeMedicalRecordCode(item.bah),
      sjh: normalizeMedicalRecordCode(item.sjh),
      imageUrl: item.img_url || item.ossUrl || '',
    }))
    if (!images.value.length) {
      errorMessage.value = '该病案暂未查询到影像'
    }
  }
  catch (error: unknown) {
    images.value = []
    errorMessage.value = (error as { message?: string })?.message || '影像加载失败'
  }
  finally {
    loading.value = false
  }
}

async function selectCase(item: ExternalArchiveCase) {
  await router.replace({
    path: '/archive/external',
    query: {
      bah: item.bah,
      ...(item.sjh ? { sjh: item.sjh } : {}),
    },
  })
}

function navigate(delta: number) {
  if (!images.value.length) {
    return
  }
  selectedIndex.value = Math.max(0, Math.min(images.value.length - 1, selectedIndex.value + delta))
}

function printPage() {
  window.print()
}

async function downloadArchive() {
  if (!props.session.allowDownload) {
    ElMessage.warning('外部系统未授予批量下载权限')
    return
  }
  if (!activeCase.value) {
    ElMessage.warning('当前没有可下载的病案')
    return
  }
  downloading.value = true
  try {
    const response = await downloadExternalArchive(activeCase.value.bah, activeCase.value.sjh)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${activeCase.value.bah}${activeCase.value.sjh ? `-${activeCase.value.sjh}` : ''}.zip`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  catch (error: unknown) {
    ElMessage.error((error as { message?: string })?.message || '档案下载失败')
  }
  finally {
    downloading.value = false
  }
}

watch(
  () => [route.query.bah, route.query.sjh],
  () => void loadActiveCase(),
)

onMounted(() => void loadActiveCase())
</script>

<template>
  <div class="external-viewer">
    <header class="external-header">
      <div>
        <p class="eyebrow">
          External Archive Access
        </p>
        <h1>影像档案袋</h1>
        <p>调用系统：{{ session.clientId }} · 当前用户：{{ session.externalUserId }}</p>
      </div>
      <div class="header-actions">
        <el-tag type="success" effect="plain">
          只读会话
        </el-tag>
        <el-button :icon="Printer" @click="printPage">
          打印当前页
        </el-button>
        <el-button
          v-if="session.allowDownload"
          type="primary"
          :icon="Download"
          :loading="downloading"
          @click="downloadArchive"
        >
          下载档案袋
        </el-button>
      </div>
    </header>

    <div class="external-layout">
      <aside class="case-panel">
        <div class="panel-title">
          <strong>授权病案</strong>
          <span>{{ session.cases.length }} 份</span>
        </div>
        <button
          v-for="item in session.cases"
          :key="caseKey(item)"
          class="case-item"
          :class="{ active: caseKey(item) === activeCaseKey }"
          type="button"
          @click="selectCase(item)"
        >
          <strong>{{ item.patientName || '患者影像' }}</strong>
          <span>病案号 {{ item.bah }}</span>
          <span v-if="item.sjh">上架号 {{ item.sjh }}</span>
          <small>{{ item.department || item.admissionTime || '外部系统授权访问' }}</small>
        </button>
      </aside>

      <section v-loading="loading" class="thumbnail-panel">
        <div class="panel-title">
          <strong>影像目录</strong>
          <span>{{ images.length }} 张</span>
        </div>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <button
          v-for="(image, index) in images"
          :key="image.id || index"
          class="thumbnail-item"
          :class="{ active: index === selectedIndex }"
          type="button"
          @click="selectedIndex = index"
        >
          <img :src="image.imageUrl" :alt="`第 ${index + 1} 张影像`">
          <span>P{{ image.pages ?? index + 1 }}</span>
          <small>类型 {{ image.btype ?? 0 }}</small>
        </button>
        <el-empty v-if="!loading && !images.length && !errorMessage" description="暂无影像" />
      </section>

      <main class="preview-panel">
        <template v-if="currentImage">
          <div class="preview-toolbar">
            <div>
              <strong>{{ activeCase?.patientName || '患者影像' }}</strong>
              <span>{{ selectedIndex + 1 }} / {{ images.length }}</span>
            </div>
            <div>
              <el-button circle :icon="ArrowLeft" :disabled="selectedIndex === 0" @click="navigate(-1)" />
              <el-button circle :icon="ArrowRight" :disabled="selectedIndex >= images.length - 1" @click="navigate(1)" />
            </div>
          </div>
          <el-image
            class="main-image"
            :src="currentImage.imageUrl"
            :preview-src-list="previewUrls"
            :initial-index="selectedIndex"
            fit="contain"
            hide-on-click-modal
            preview-teleported
          />
        </template>
        <el-empty v-else description="请选择有影像的病案" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.external-viewer {
  box-sizing: border-box;
  min-height: 100vh;
  padding: 16px;
  color: var(--text-primary);
  background: var(--surface-muted);
}

.external-header,
.panel-title,
.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.external-header {
  gap: 24px;
  padding: 18px 22px;
  margin-bottom: 12px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 16px;
}

.external-header h1,
.external-header p {
  margin: 0;
}

.external-header h1 {
  font-size: 24px;
}

.external-header p:last-child {
  margin-top: 6px;
  color: var(--text-secondary);
}

.eyebrow {
  margin-bottom: 4px !important;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.external-layout {
  display: grid;
  grid-template-columns: minmax(230px, 280px) minmax(180px, 220px) minmax(0, 1fr);
  gap: 12px;
  height: calc(100vh - 126px);
  min-height: 560px;
}

.case-panel,
.thumbnail-panel,
.preview-panel {
  min-height: 0;
  padding: 12px;
  overflow: auto;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 16px;
}

.panel-title {
  padding: 4px 4px 12px;
  color: var(--text-secondary);
}

.case-item,
.thumbnail-item {
  box-sizing: border-box;
  width: 100%;
  margin-bottom: 8px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--divider);
  border-radius: 12px;
}

.case-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
}

.case-item span,
.case-item small {
  color: var(--text-secondary);
}

.case-item.active,
.thumbnail-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.thumbnail-item {
  position: relative;
  padding: 6px;
}

.thumbnail-item img {
  display: block;
  width: 100%;
  height: 128px;
  object-fit: contain;
  background: rgb(17 17 17);
  border-radius: 8px;
}

.thumbnail-item span,
.thumbnail-item small {
  display: inline-block;
  margin-top: 6px;
}

.thumbnail-item small {
  float: right;
  color: var(--text-secondary);
}

.preview-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-toolbar {
  flex: none;
  padding: 4px 4px 12px;
}

.preview-toolbar span {
  margin-left: 12px;
  color: var(--text-secondary);
}

.main-image {
  width: 100%;
  height: calc(100% - 48px);
  min-height: 0;
  background: rgb(23 23 23);
  border-radius: 12px;
}

@media (width <= 1050px) {
  .external-layout {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  .thumbnail-panel {
    display: none;
  }
}

@media print {
  .case-panel,
  .thumbnail-panel,
  .external-header,
  .preview-toolbar {
    display: none;
  }

  .external-viewer,
  .external-layout,
  .preview-panel,
  .main-image {
    width: 100%;
    height: auto;
    padding: 0;
    margin: 0;
    border: 0;
  }
}
</style>
