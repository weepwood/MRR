<script setup lang="ts">
import type { ClassificationSuggestion, OcrItem } from '@/api/modules/classification'
import { ElMessage } from 'element-plus'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getClassificationSuggestions, getOcrItems, getRecordTypes, getReviewImage, reviewClassification } from '@/api/modules/classification'
import { updateImageType } from '@/api/modules/image'
import useAuth from '@/utils/composables/useAuth'

const props = defineProps<{ jobId: string }>()
const { auth } = useAuth()
const suggestions = ref<ClassificationSuggestion[]>([])
const unresolved = ref<OcrItem[]>([])
const types = ref<{ value: number, label: string }[]>([])
const after = ref(0)
const itemAfter = ref(0)
const nextItemAfter = ref(0)
const loadError = ref('')
const manualTypes = ref<Record<number, number>>({})
const busy = ref(false)
const preview = ref('')
const referencePreview = ref('')
const pageItems = ref<OcrItem[]>([])
const previewOpen = ref(false)
let generation = 0
let previewGeneration = 0
function clearPreview() {
  previewGeneration++
  if (preview.value) { URL.revokeObjectURL(preview.value) }
  if (referencePreview.value) { URL.revokeObjectURL(referencePreview.value) }
  preview.value = ''; referencePreview.value = ''
}
function explanation(row: ClassificationSuggestion) {
  if (row.reasonCode === 'CONTEXT_REVERSE') { return `疑似正反面 · 参考影像 ${row.referenceScanId}` }
  if (row.reasonCode === 'CONTEXT_CONTINUATION') { return `连续页建议 · 参考影像 ${row.referenceScanId}` }
  return ({ EXACT_TITLE: '明确独占标题', CONFLICT: '存在类型冲突，需人工核对', REVIEW_REQUIRED: '规则建议，需人工核对' } as Record<string, string>)[row.reasonCode] || row.reasonCode
}
function relation(row: ClassificationSuggestion) {
  return row.relationStatus || pageItems.value.find(item => item.scanId === row.scanId)?.relationStatus
}
async function showImage(scanId: number, referenceId?: number | null) {
  const version = generation
  const request = ++previewGeneration
  try {
    const [result, reference] = await Promise.all([getReviewImage(scanId), referenceId ? getReviewImage(referenceId) : Promise.resolve(null)])
    if (version !== generation || request !== previewGeneration) { return }
    clearPreview()
    preview.value = URL.createObjectURL(result)
    referencePreview.value = reference ? URL.createObjectURL(reference) : ''
    previewOpen.value = true
  }
  catch { loadError.value = '原图读取失败' }
}
onBeforeUnmount(() => {
  generation++; clearPreview()
})

async function load(reset = false) {
  if (reset) { after.value = 0; itemAfter.value = 0 }
  const version = ++generation
  try {
    const [list, items, dictionary] = await Promise.all([getClassificationSuggestions(props.jobId, after.value), getOcrItems(props.jobId, itemAfter.value), getRecordTypes()])
    if (version !== generation) { return }
    suggestions.value = list.data || []
    pageItems.value = items.data || []
    unresolved.value = (items.data || []).filter(item => ['NO_MATCH', 'LOW_CONFIDENCE', 'FAILED'].includes(item.status) && !item.suggestedType)
    types.value = dictionary.data || []
    loadError.value = ''
    const last = items.data?.[items.data.length - 1]
    nextItemAfter.value = last?.scanId || itemAfter.value
  }
  catch {
    if (version === generation) { loadError.value = '审核队列读取失败' }
  }
}
async function review(row: ClassificationSuggestion, action: string) {
  busy.value = true
  try {
    await reviewClassification(row.id, action, manualTypes.value[row.scanId])
    await load()
  }
  catch { ElMessage.error('审核失败，影像、类型、源文件或权限可能已变化') }
  finally { busy.value = false }
}
async function manual(row: OcrItem) {
  const type = manualTypes.value[row.scanId]
  if (type === undefined) { return }
  busy.value = true
  try { await updateImageType(row.scanId, { btype: type }); unresolved.value = unresolved.value.filter(item => item.scanId !== row.scanId) }
  catch { ElMessage.error('人工分类失败，请刷新后重试') }
  finally { busy.value = false }
}
function nextPage() {
  after.value = suggestions.value[suggestions.value.length - 1]?.id || after.value
  itemAfter.value = nextItemAfter.value
  void load()
}
onMounted(() => void load(true))
watch(() => props.jobId, () => { previewOpen.value = false; clearPreview(); void load(true) })
</script>

<template>
  <section v-if="auth('classification:read')" v-loading="busy">
    <h3>分类建议与待处理页面</h3>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-table :data="suggestions">
      <el-table-column prop="pageNumber" label="页码" width="75" />
      <el-table-column prop="scanId" label="影像 ID" />
      <el-table-column label="建议">
        <template #default="{ row }">
          {{ types.find(type => type.value === row.suggestedType)?.label || row.suggestedType }}
        </template>
      </el-table-column>
      <el-table-column label="分类依据" min-width="230">
        <template #default="{ row }">
          {{ explanation(row as ClassificationSuggestion) }}
          <p v-if="row.referenceScanId">
            上下文建议需人工审核
          </p>
        </template>
      </el-table-column>
      <el-table-column label="规则分数">
        <template #default="{ row }">
          {{ row.referenceScanId ? '—' : row.ruleScore }}
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          {{ row.autoStatus === 'APPLIED' ? '已自动归类' : ({ PENDING: '待审核', ACCEPTED: '已确认', REJECTED: '已拒绝', OVERRIDDEN: '人工改类', SUPERSEDED: '已过期' } as Record<string, string>)[row.reviewStatus] || row.reviewStatus }}
        </template>
      </el-table-column>
      <el-table-column label="原图" min-width="140">
        <template #default="{ row }">
          <el-button link @click="showImage(row.scanId)">
            核对原图
          </el-button>
          <el-button v-if="row.referenceScanId" link @click="showImage(row.scanId, row.referenceScanId)">
            对照前后页
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="正反面关联" min-width="180">
        <template #default="{ row }">
          <template v-if="row.reasonCode === 'CONTEXT_REVERSE'">
            <span>{{ relation(row as ClassificationSuggestion) === 'CONFIRMED' ? '已确认关联' : relation(row as ClassificationSuggestion) === 'REJECTED' ? '关联已解除' : '待核对' }}</span>
            <template v-if="auth('classification:review')">
              <el-button v-if="relation(row as ClassificationSuggestion) === 'SUGGESTED'" link @click="review(row as ClassificationSuggestion, 'CONFIRM_PAIR')">
                确认关联
              </el-button>
              <el-button v-if="['SUGGESTED', 'CONFIRMED'].includes(relation(row as ClassificationSuggestion) || '')" link @click="review(row as ClassificationSuggestion, 'REJECT_PAIR')">
                解除关联
              </el-button>
            </template>
          </template>
          <span v-else>—</span>
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
      <el-table-column prop="pageNumber" label="页码" width="75" />
      <el-table-column prop="scanId" label="需人工处理的影像 ID" />
      <el-table-column prop="status" label="原因" />
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
    <el-dialog v-model="previewOpen" title="核对影像原图" width="85%" append-to-body @closed="clearPreview">
      <div class="classification-comparison">
        <figure v-if="referencePreview">
          <figcaption>参考页</figcaption><img :src="referencePreview" alt="参考页">
        </figure>
        <figure v-if="preview">
          <figcaption>当前审核页</figcaption><img :src="preview" alt="当前审核页">
        </figure>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.classification-comparison {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.classification-comparison figure {
  flex: 1;
  min-width: 0;
  margin: 0;
}
.classification-comparison img { width: 100%; }

@media (max-width: 760px) {
  .classification-comparison { flex-direction: column; }
}
</style>
