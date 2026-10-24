<script setup lang="ts">
import type { ClassificationSuggestion, OcrItem } from '@/api/modules/classification'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getClassificationSuggestions, getOcrItems, getRecordTypes, getReviewImage, reviewClassification } from '@/api/modules/classification'
import { updateImageType } from '@/api/modules/image'
import useAuth from '@/utils/composables/useAuth'
import { ocrAutoStatusLabels, ocrItemStatusLabels, ocrLabel, ocrReasonLabels, ocrReviewStatusLabels } from '@/utils/ocr-labels'

const props = defineProps<{ jobId: string }>()
const { auth } = useAuth()
const suggestions = ref<ClassificationSuggestion[]>([])
const unresolved = ref<OcrItem[]>([])
const types = ref<{ value: number, label: string }[]>([])
const after = ref(0)
const itemAfter = ref(0)
const nextItemAfter = ref(0)
const error = ref('')
const manualTypes = ref<Record<number, number>>({})
const busy = ref(false)
const preview = ref('')
const previewOpen = ref(false)
let generation = 0
let previewGeneration = 0
async function showImage(scanId: number) {
  const version = generation
  const request = ++previewGeneration
  try {
    const result = await getReviewImage(scanId)
    if (version !== generation || request !== previewGeneration) { return }
    if (preview.value) { URL.revokeObjectURL(preview.value) }
    preview.value = URL.createObjectURL(result.data)
    previewOpen.value = true
  }
  catch { error.value = '原图读取失败' }
}
onBeforeUnmount(() => {
  generation++; if (preview.value) { URL.revokeObjectURL(preview.value) }
})

async function load(reset = false) {
  if (reset) { after.value = 0; itemAfter.value = 0 }
  const version = ++generation
  try {
    const [list, items, dictionary] = await Promise.all([getClassificationSuggestions(props.jobId, after.value), getOcrItems(props.jobId, itemAfter.value), getRecordTypes()])
    if (version !== generation) { return }
    suggestions.value = list.data || []
    unresolved.value = (items.data || []).filter(item => ['NO_MATCH', 'LOW_CONFIDENCE', 'FAILED'].includes(item.status) && !item.suggestedType)
    types.value = dictionary.data || []
    error.value = ''
    const last = items.data?.[items.data.length - 1]
    nextItemAfter.value = last?.scanId || itemAfter.value
  }
  catch {
    if (version === generation) { error.value = '审核队列读取失败' }
  }
}
async function review(row: ClassificationSuggestion, action: string) {
  busy.value = true
  try {
    await reviewClassification(row.id, action, manualTypes.value[row.scanId])
    await load()
  }
  catch { error.value = '审核失败，影像、类型、源文件或权限可能已变化' }
  finally { busy.value = false }
}
async function manual(row: OcrItem) {
  const type = manualTypes.value[row.scanId]
  if (type === undefined) { return }
  busy.value = true
  try { await updateImageType(row.scanId, { btype: type }); unresolved.value = unresolved.value.filter(item => item.scanId !== row.scanId) }
  catch { error.value = '人工分类失败，请刷新后重试' }
  finally { busy.value = false }
}
function nextPage() {
  after.value = suggestions.value[suggestions.value.length - 1]?.id || after.value
  itemAfter.value = nextItemAfter.value
  void load()
}
onMounted(() => void load(true))
watch(() => props.jobId, () => void load(true))
</script>

<template>
  <section v-if="auth('classification:read')" v-loading="busy">
    <h3>分类建议与待处理页面</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="suggestions">
      <el-table-column prop="scanId" label="影像 ID" />
      <el-table-column label="建议">
        <template #default="{ row }">
          {{ types.find(type => type.value === row.suggestedType)?.label || row.suggestedType }}
        </template>
      </el-table-column>
      <el-table-column label="分类依据">
        <template #default="{ row }">
          {{ ocrLabel(ocrReasonLabels, row.reasonCode) }}
        </template>
      </el-table-column>
      <el-table-column prop="ruleScore" label="规则分数" />
      <el-table-column label="状态">
        <template #default="{ row }">
          {{ row.autoStatus === 'APPLIED' ? ocrLabel(ocrAutoStatusLabels, row.autoStatus) : ocrLabel(ocrReviewStatusLabels, row.reviewStatus) }}
        </template>
      </el-table-column>
      <el-table-column label="原图">
        <template #default="{ row }">
          <el-button link @click="showImage(row.scanId)">
            核对原图
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="审核" min-width="210">
        <template #default="{ row }">
          <template v-if="row.reviewStatus === 'PENDING' && auth('classification:review')">
            <el-button @click="review(row as ClassificationSuggestion, 'ACCEPT')">
              确认
            </el-button><el-button @click="review(row as ClassificationSuggestion, 'REJECT')">
              拒绝
            </el-button><template v-if="auth('classification:override')">
              <el-select v-model="manualTypes[row.scanId]" placeholder="人工类型">
                <el-option v-for="type in types" :key="type.value" :value="type.value" :label="type.label" />
              </el-select><el-button :disabled="manualTypes[row.scanId] === undefined" @click="review(row as ClassificationSuggestion, 'OVERRIDE')">
                人工改类
              </el-button>
            </template>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-table :data="unresolved">
      <el-table-column prop="scanId" label="需人工处理的影像 ID" />
      <el-table-column label="原因">
        <template #default="{ row }">
          {{ ocrLabel(ocrItemStatusLabels, row.status) }}
        </template>
      </el-table-column>
      <el-table-column label="原图">
        <template #default="{ row }">
          <el-button link @click="showImage(row.scanId)">
            核对原图
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="人工分类">
        <template #default="{ row }">
          <template v-if="auth('record:edit')">
            <el-select v-model="manualTypes[row.scanId]" placeholder="选择正式类型">
              <el-option v-for="type in types" :key="type.value" :value="type.value" :label="type.label" />
            </el-select><el-button :disabled="manualTypes[row.scanId] === undefined" @click="manual(row as OcrItem)">
              保存
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-button @click="load(true)">
      返回首批 / 刷新
    </el-button>
    <el-button :disabled="!suggestions.length && nextItemAfter === itemAfter" @click="nextPage">
      下一批（最多 50 页）
    </el-button>
    <el-dialog v-model="previewOpen" title="核对影像原图" append-to-body>
      <img v-if="preview" :src="preview" alt="待审核影像原图" style="width: 100%;">
    </el-dialog>
  </section>
</template>
