<script setup lang="ts">
import type { ClassificationBundle, ClassificationVersion } from '@/api/modules/classification'
import { computed, onMounted, ref } from 'vue'
import { createClassificationDraft, getClassificationBundle, getClassificationVersions, getRecordTypes, publishClassificationDraft, restoreClassificationVersion, saveClassificationDraft, testClassificationVersion } from '@/api/modules/classification'
import useAuth from '@/utils/composables/useAuth'

const { auth } = useAuth()
const versions = ref<ClassificationVersion[]>([])
const selected = ref('')
const bundle = ref<ClassificationBundle>({ name: '病案分类规则', rules: [], lexicon: [] })
const types = ref<{ value: number, label: string }[]>([])
const busy = ref(false)
const message = ref('')
const testScan = ref<number>()
const editable = computed(() => !selected.value || versions.value.find(v => v.version === selected.value)?.status === 'DRAFT')

async function attempt(work: () => Promise<void>) {
  busy.value = true
  message.value = ''
  try { await work() }
  catch { message.value = '操作失败，请检查规则、权限或版本状态' }
  finally { busy.value = false }
}
async function refresh() {
  const [list, dictionary] = await Promise.all([getClassificationVersions(), getRecordTypes()])
  versions.value = list.data || []
  types.value = (dictionary.data || []).filter(t => t.value > 0)
}
async function load() {
  if (selected.value) { bundle.value = (await getClassificationBundle(selected.value)).data || bundle.value }
}
function addRule() {
  bundle.value.rules.push({ targetType: 1, keyword: '', matchScope: 'TITLE', matchMode: 'EXACT', weight: 1, priority: 100, negative: false, required: false, enabled: true })
}
async function save() {
  await attempt(async () => {
    if (selected.value) {
      await saveClassificationDraft(selected.value, bundle.value)
    }
    else { selected.value = (await createClassificationDraft(bundle.value)).data || '' }
    await refresh()
    message.value = '草稿已保存'
  })
}
async function publish() {
  await attempt(async () => {
    await publishClassificationDraft(selected.value)
    await refresh()
    message.value = '规则已发布；自动分类仍需独立质量验收'
  })
}
async function restore() {
  await attempt(async () => {
    selected.value = (await restoreClassificationVersion(selected.value)).data || ''
    await refresh()
    await load()
  })
}
async function test() {
  if (!testScan.value) { return }
  await attempt(async () => {
    const result = (await testClassificationVersion(selected.value, testScan.value!)).data
    message.value = result ? `建议类型 ${result.targetType ?? '无匹配'}，规则分数 ${result.score}，原因 ${result.reason}` : '未取得测试结果'
  })
}
onMounted(() => {
  if (auth('ocr:config')) { void attempt(refresh) }
})
</script>

<template>
  <div v-if="auth('ocr:config')" v-loading="busy" class="classification-rules">
    <el-alert v-if="message" :title="message" :closable="false" type="info" />
    <div class="rule-toolbar">
      <el-select v-model="selected" placeholder="新建草稿" clearable @change="attempt(load)">
        <el-option v-for="version in versions" :key="version.version" :value="version.version" :label="`${version.name} · ${version.status} · ${version.version.slice(0, 8)}`" />
      </el-select>
      <el-button @click="selected = ''; bundle = { name: '病案分类规则', rules: [], lexicon: [] }">
        新建草稿
      </el-button>
      <el-button :disabled="!editable" @click="save">
        保存草稿
      </el-button>
      <el-button :disabled="!selected || !editable" @click="publish">
        发布已保存草稿
      </el-button>
      <el-button :disabled="!selected" @click="restore">
        复制此版本为草稿
      </el-button>
    </div>
    <el-input v-model="bundle.name" :disabled="!editable" maxlength="128" aria-label="规则集名称" />
    <el-table :data="bundle.rules" max-height="420">
      <el-table-column label="分类" min-width="170">
        <template #default="{ row }">
          <el-select v-model="row.targetType" :disabled="!editable">
            <el-option v-for="type in types" :key="type.value" :value="type.value" :label="type.label" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="关键词 / 标题" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.keyword" :disabled="!editable" maxlength="500" />
        </template>
      </el-table-column>
      <el-table-column label="位置" width="115">
        <template #default="{ row }">
          <el-select v-model="row.matchScope" :disabled="!editable">
            <el-option label="标题" value="TITLE" /><el-option label="正文" value="BODY" /><el-option label="任意" value="ANY" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="匹配" width="115">
        <template #default="{ row }">
          <el-select v-model="row.matchMode" :disabled="!editable">
            <el-option label="完全匹配" value="EXACT" /><el-option label="包含" value="CONTAINS" /><el-option label="正则" value="REGEX" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="权重" width="105">
        <template #default="{ row }">
          <el-input-number v-model="row.weight" :disabled="!editable" :min="0.001" :max="1000" :controls="false" />
        </template>
      </el-table-column>
      <el-table-column label="选项" min-width="205">
        <template #default="{ row }">
          <el-checkbox v-model="row.negative" :disabled="!editable">
            排除
          </el-checkbox><el-checkbox v-model="row.required" :disabled="!editable">
            必需
          </el-checkbox><el-checkbox v-model="row.enabled" :disabled="!editable">
            启用
          </el-checkbox>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="75">
        <template #default="{ $index }">
          <el-button :disabled="!editable" link @click="bundle.rules.splice($index, 1)">
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button :disabled="!editable" @click="addRule">
      添加分类规则
    </el-button>
    <h4>识别词库</h4>
    <el-table :data="bundle.lexicon" max-height="300">
      <el-table-column label="标准词">
        <template #default="{ row }">
          <el-input v-model="row.term" :disabled="!editable" maxlength="256" />
        </template>
      </el-table-column>
      <el-table-column label="明确别名（逗号分隔）">
        <template #default="{ row }">
          <el-input :model-value="row.aliases.join(',')" :disabled="!editable" @update:model-value="row.aliases = String($event).split(',').map(s => s.trim()).filter(Boolean)" />
        </template>
      </el-table-column>
      <el-table-column label="常见错字（仅辅助建议）">
        <template #default="{ row }">
          <el-input :model-value="row.commonOcrErrors.join(',')" :disabled="!editable" @update:model-value="row.commonOcrErrors = String($event).split(',').map(s => s.trim()).filter(Boolean)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="75">
        <template #default="{ $index }">
          <el-button :disabled="!editable" link @click="bundle.lexicon.splice($index, 1)">
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button :disabled="!editable" @click="bundle.lexicon.push({ term: '', aliases: [], commonOcrErrors: [] })">
      添加词条
    </el-button>
    <div v-if="auth('ocr:text-read')" class="rule-toolbar">
      <el-input-number v-model="testScan" :min="1" placeholder="测试影像 ID" aria-label="测试影像 ID" />
      <el-button :disabled="!selected || !testScan" @click="test">
        测试已保存规则
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.classification-rules { display: grid; gap: 12px; }
.rule-toolbar { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
