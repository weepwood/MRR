<script setup lang="ts">
import type { SettingSchema, SettingSchemaItem } from '@/api/modules/settings'
import { ElMessage } from 'element-plus'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { deleteSetting, getSettingsSchema, saveSystemSettings } from '@/api/modules/settings'
import AppLoading from '@/components/AppLoading/index.vue'
import { actionDialog } from '@/ui/components/ActionDialog'
import { DIRECTORY_ROUTING_KEY } from './image-directory-routing'

const schema = ref<SettingSchema>({ groups: [], items: [] })
const booleanValues = reactive<Record<string, boolean>>({})
const numberValues = reactive<Record<string, number>>({})
const textValues = reactive<Record<string, string>>({})
/** 数据库中的显式覆盖值；空串表示"未覆盖，沿用配置文件与环境变量"。 */
const overrides = reactive<Record<string, string>>({})
const savedMap = ref<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const needsReload = ref(false)
const active = ref(true)
const feedback = ref('')
const keyword = ref('')
const filterMode = ref('all')
const filterOptions = [{ label: '全部', value: 'all' }, { label: '数据库覆盖', value: 'override' }, { label: '待保存', value: 'changed' }]
let alive = true
let generation = 0
const isCurrent = (attempt: number) => alive && active.value && generation === attempt
const controlsDisabled = computed(() => !active.value || loading.value || saving.value || !loaded.value || needsReload.value)

function isNumeric(item: SettingSchemaItem) {
  return item.type === 'integer' || item.type === 'decimal'
}

function serializeOne(item: SettingSchemaItem): string {
  if (item.type === 'boolean') {
    return booleanValues[item.key] ? 'true' : 'false'
  }
  if (item.type === 'integer') {
    return String(Math.round(numberValues[item.key] ?? 0))
  }
  if (item.type === 'decimal') {
    return String(numberValues[item.key] ?? 0)
  }
  return (textValues[item.key] ?? '').trim()
}

const changedItems = computed(() => schema.value.items.filter(
  item => serializeOne(item) !== (savedMap.value[item.key] ?? ''),
))
const isDirty = computed(() => changedItems.value.length > 0)

function itemChanged(item: SettingSchemaItem) {
  return serializeOne(item) !== (savedMap.value[item.key] ?? '')
}

const overriddenCount = computed(() => schema.value.items.filter(isOverridden).length)
const visibleItems = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  return schema.value.items.filter((item) => {
    const group = schema.value.groups.find(group => group.key === item.group)
    const matches = !query || [item.label, item.key, item.description, group?.label].some(value => value?.toLocaleLowerCase('zh-CN').includes(query))
    return matches && (filterMode.value === 'all' || (filterMode.value === 'override' ? isOverridden(item) : itemChanged(item)))
  })
})
const visibleGroups = computed(() => schema.value.groups.filter(group => visibleItems.value.some(item => item.group === group.key)))

function itemsOf(groupKey: string) {
  return visibleItems.value.filter(item => item.group === groupKey)
}

function resetFilters() {
  keyword.value = ''
  filterMode.value = 'all'
}

function isOverridden(item: SettingSchemaItem) {
  return (overrides[item.key] ?? '').trim().length > 0
}

function rangeLabel(item: SettingSchemaItem) {
  if (item.min === null && item.max === null) {
    return ''
  }
  const unit = item.unit ? ` ${item.unit}` : ''
  return `取值范围 ${item.min ?? '-'} 至 ${item.max ?? '-'}${unit}`
}

function defaultValueLabel(item: SettingSchemaItem) {
  return item.defaultValue === '' ? '空' : item.defaultValue
}

/** 先构造完整快照，成功且仍属当前激活代次时才更新页面。 */
async function readSettings(attempt: number): Promise<boolean> {
  const response = await getSettingsSchema()
  if (!isCurrent(attempt)) { return false }
  const payload = response.data
  if (!payload || !Array.isArray(payload.items) || !Array.isArray(payload.groups)) {
    throw new Error('missing schema')
  }
  const items = payload.items.filter(item => item.key !== DIRECTORY_ROUTING_KEY)
  const booleans: Record<string, boolean> = {}
  const numbers: Record<string, number> = {}
  const texts: Record<string, string> = {}
  const rawOverrides: Record<string, string> = {}
  const saved: Record<string, string> = {}
  for (const item of items) {
    const effective = String(item.effectiveValue ?? '').trim()
    rawOverrides[item.key] = String(item.overrideValue ?? '').trim()
    if (item.type === 'boolean') {
      booleans[item.key] = effective === 'true'
      saved[item.key] = booleans[item.key] ? 'true' : 'false'
    }
    else if (isNumeric(item)) {
      const parsed = Number(effective)
      numbers[item.key] = Number.isFinite(parsed) ? parsed : 0
      saved[item.key] = String(item.type === 'integer' ? Math.round(numbers[item.key]) : numbers[item.key])
    }
    else {
      texts[item.key] = effective
      saved[item.key] = effective
    }
  }
  schema.value = { items, groups: payload.groups.filter(group => items.some(item => item.group === group.key)) }
  Object.assign(booleanValues, booleans)
  Object.assign(numberValues, numbers)
  Object.assign(textValues, texts)
  Object.assign(overrides, rawOverrides)
  savedMap.value = saved
  loaded.value = true
  needsReload.value = false
  feedback.value = ''
  return true
}

const synced = computed(() => loaded.value && !needsReload.value && !loading.value && !saving.value)
const canSave = computed(() => !controlsDisabled.value && isDirty.value)
const statusLabel = computed(() => {
  if (saving.value) { return '正在保存或核对运行参数' }
  if (loading.value) { return '正在读取运行参数' }
  if (needsReload.value) { return '运行参数待重新核对' }
  if (!loaded.value) { return '运行参数尚未读取' }
  return isDirty.value ? '运行参数修改待保存' : '运行参数已同步'
})

async function loadSettings(): Promise<boolean> {
  if (!active.value || loading.value || saving.value) { return false }
  const attempt = generation
  loading.value = true
  try {
    if (isDirty.value) {
      try {
        await actionDialog.confirm('重新加载会丢弃本页所有未保存的运行参数修改，是否继续？', '重新加载运行参数', { type: 'warning', confirmButtonText: '确认重新加载', cancelButtonText: '取消' })
      }
      catch { return false }
    }
    if (!isCurrent(attempt)) { return false }
    return await readSettings(attempt)
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = '运行参数读取失败，当前编辑已保留。请重新加载核对后再保存。'
      ElMessage.error(feedback.value)
    }
    return false
  }
  finally { if (alive) { loading.value = false } }
}

async function saveSettings(): Promise<boolean> {
  if (!canSave.value) { return false }
  const attempt = generation
  const items = changedItems.value.map(item => ({ ...item }))
  const payload = Object.fromEntries(items.map(item => [item.key, serializeOne(item)]))
  const sensitiveLabels = items.filter(item => item.sensitive).map(item => item.label)
  let applied = false
  saving.value = true
  try {
    if (sensitiveLabels.length > 0) {
      try {
        await actionDialog.confirm(
          `本次保存 ${items.length} 项修改，包含 ${sensitiveLabels.length} 项敏感配置：${sensitiveLabels.join('、')}。保存后立即影响所有用户，无需重启服务。`,
          '确认修改敏感配置',
          { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' },
        )
      }
      catch { return false }
    }
    if (!isCurrent(attempt)) { return false }
    await saveSystemSettings(payload)
    applied = true
    if (!isCurrent(attempt) || !await readSettings(attempt)) { return false }
    ElMessage.success('运行参数已保存并核对最新配置')
    return true
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = applied
        ? '运行参数已保存，但读取最新配置失败。请重新加载核对，不要重复保存。'
        : '未能确认运行参数保存结果，服务器可能已应用变更。请重新加载核对后再操作。'
      ElMessage.error(feedback.value)
    }
    return false
  }
  finally { if (alive) { saving.value = false } }
}

async function clearOverride(item: SettingSchemaItem) {
  if (controlsDisabled.value || !schema.value.items.some(current => current.key === item.key)) { return }
  const attempt = generation
  const key = item.key
  const label = item.label
  const defaultLabel = defaultValueLabel(item)
  let applied = false
  saving.value = true
  try {
    try {
      await actionDialog.confirm(
        `将删除「${label}」的数据库覆盖，恢复配置默认值（${defaultLabel}）。重新读取会丢弃本页其他未保存修改。`,
        '恢复配置默认',
        { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' },
      )
    }
    catch { return }
    if (!isCurrent(attempt)) { return }
    await deleteSetting(key)
    applied = true
    if (!isCurrent(attempt) || !await readSettings(attempt)) { return }
    ElMessage.success(`${label} 已恢复并核对最新配置`)
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedback.value = applied
        ? '覆盖值已清除，但读取最新配置失败。请重新加载核对，不要重复恢复。'
        : '未能确认恢复结果，服务器可能已应用变更。请重新加载核对后再操作。'
      ElMessage.error(feedback.value)
    }
  }
  finally { if (alive) { saving.value = false } }
}

function fillWithConfigDefaults() {
  if (controlsDisabled.value) { return }
  for (const item of schema.value.items) {
    if (item.effect === 'RESTART_REQUIRED') { continue }
    if (item.type === 'boolean') {
      booleanValues[item.key] = item.defaultValue === 'true'
    }
    else if (isNumeric(item)) {
      const parsed = Number(item.defaultValue)
      numberValues[item.key] = Number.isFinite(parsed) ? parsed : 0
    }
    else {
      textValues[item.key] = item.defaultValue
    }
  }
  ElMessage.info('已填入配置文件默认值，保存后才会写入数据库并覆盖配置')
}

defineExpose({ saving, isDirty, canSave, statusLabel, synced, saveSettings, loadSettings, fillWithConfigDefaults })

onMounted(loadSettings)
onActivated(() => { active.value = true })
onDeactivated(() => {
  active.value = false
  generation++
  if (loading.value || saving.value) {
    needsReload.value = true
    feedback.value = '页面切换中断了结果核对。已发送的请求仍可能生效，请重新加载服务器参数后再操作。'
  }
})
onBeforeUnmount(() => { alive = false; active.value = false; generation++ })
</script>

<template>
  <!-- 首次读取完成前没有服务器参数，用骨架屏代替默认值表单；之后的重新加载保留内容，只叠加遮罩。 -->
  <AppLoading v-if="loading && !loaded" type="table" :rows="6" :cols="3" />
  <section v-else v-loading="loading" class="setting-section runtime-settings">
    <div class="setting-group runtime-overview">
      <div class="group-heading runtime-heading">
        <div>
          <strong>配置管理</strong>
          <p>数据库覆盖优先于部署配置；未覆盖的参数沿用配置文件及环境变量。</p>
        </div>
        <div class="actions-buttons">
          <el-button :disabled="loading || saving || !active" @click="loadSettings">
            <FaIcon name="i-lucide:refresh-cw" class="mrr-icon-interactive" />重新加载
          </el-button>
          <el-button :disabled="controlsDisabled" @click="fillWithConfigDefaults">
            填入配置默认值
          </el-button>
        </div>
      </div>
      <div class="runtime-summary" role="status">
        <span class="runtime-summary-status">{{ statusLabel }}</span>
        <template v-if="loaded">
          <span>参数总数 <b>{{ schema.items.length }}</b></span>
          <span>数据库覆盖 <b>{{ overriddenCount }}</b></span>
          <span>待保存 <b>{{ changedItems.length }}</b></span>
        </template>
      </div>
      <p class="runtime-note">
        填入默认值仅修改草稿；单项“恢复配置默认”会删除数据库覆盖，并重新读取服务器配置。
      </p>
      <el-alert v-if="feedback" :title="feedback" type="warning" :closable="false" show-icon role="status" />
      <div v-if="loaded" class="runtime-filter">
        <el-input v-model="keyword" clearable aria-label="搜索运行参数" placeholder="搜索参数名称或说明">
          <template #prefix>
            <FaIcon name="i-lucide:search" aria-hidden="true" />
          </template>
        </el-input>
        <el-segmented v-model="filterMode" :options="filterOptions" aria-label="参数筛选" />
        <span class="runtime-match-count">显示 {{ visibleItems.length }} / {{ schema.items.length }} 项</span>
      </div>
    </div>

    <div v-if="loaded && !visibleItems.length" class="setting-group runtime-empty">
      <p>{{ schema.items.length ? '没有匹配的运行参数' : '当前没有可配置的运行参数' }}</p>
      <el-button v-if="schema.items.length" @click="resetFilters">
        清除筛选
      </el-button>
    </div>

    <div v-for="group in visibleGroups" :key="group.key" class="setting-group runtime-group">
      <div class="group-heading runtime-heading">
        <div><strong>{{ group.label }}</strong><p>{{ itemsOf(group.key).length }} 项参数 · 服务端校验取值范围</p></div>
      </div>
      <el-form label-position="top" @submit.prevent>
        <div class="runtime-rows">
          <div v-for="item in itemsOf(group.key)" :key="item.key" class="runtime-row" :class="{ 'is-changed': itemChanged(item) }" :data-setting-key="item.key">
            <div class="runtime-copy">
              <div class="field-label">
                <strong>{{ item.label }}</strong>
                <el-tag v-if="itemChanged(item)" size="small" type="warning" effect="plain">
                  待保存
                </el-tag>
                <el-tag v-if="isOverridden(item)" size="small" type="info" effect="plain">
                  数据库覆盖
                </el-tag>
                <span v-else class="runtime-source">沿用配置</span>
                <el-tag v-if="item.effect === 'RESTART_REQUIRED'" size="small" type="danger" effect="plain">
                  需重启
                </el-tag>
                <span v-if="item.sensitive" class="runtime-sensitive">敏感配置</span>
              </div>
              <p v-if="item.description" class="field-hint">
                {{ item.description }}
              </p>
            </div>

            <div class="runtime-value">
              <div class="runtime-input-line">
                <el-switch v-if="item.type === 'boolean'" v-model="booleanValues[item.key]" :aria-label="item.label" :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'" />
                <div v-else-if="isNumeric(item)" class="number-control">
                  <el-input-number v-model="numberValues[item.key]" :aria-label="item.label" :min="item.min ?? undefined" :max="item.max ?? undefined" :step="item.type === 'decimal' ? 0.01 : 1" :precision="item.type === 'decimal' ? 2 : 0" :readonly="controlsDisabled" :disabled="item.effect === 'RESTART_REQUIRED'" controls-position="right" />
                  <span v-if="item.unit" class="runtime-unit">{{ item.unit }}</span>
                </div>
                <el-select v-else-if="item.type === 'enum'" v-model="textValues[item.key]" :aria-label="item.label" :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'" class="runtime-text-control">
                  <el-option v-for="value in item.allowedValues" :key="value" :label="value" :value="value" />
                </el-select>
                <el-input v-else v-model="textValues[item.key]" :aria-label="item.label" :maxlength="item.max ?? undefined" :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'" class="runtime-text-control" />
              </div>
              <div class="field-meta">
                <span>部署默认值 {{ defaultValueLabel(item) }}<template v-if="item.unit"> {{ item.unit }}</template></span>
                <span v-if="rangeLabel(item)">{{ rangeLabel(item) }}</span>
                <span v-if="itemChanged(item)">当前生效值 {{ savedMap[item.key] || '空' }}<template v-if="item.unit"> {{ item.unit }}</template></span>
              </div>
              <el-button v-if="isOverridden(item)" link type="primary" size="small" class="reset-link" :disabled="controlsDisabled || item.effect === 'RESTART_REQUIRED'" @click="clearOverride(item)">
                恢复配置默认
              </el-button>
            </div>
          </div>
        </div>
      </el-form>
    </div>
  </section>
</template>

<style scoped>
.setting-group {
  min-width: 0;
  padding: var(--settings-group-inset, var(--mrr-space-4));
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
}

.group-heading { margin-bottom: var(--mrr-space-3); }
.runtime-heading { display: flex; flex-wrap: wrap; gap: var(--mrr-space-3); align-items: center; justify-content: space-between; }
.runtime-heading > div:first-child { flex: 1 1 240px; min-width: 0; }
.actions-buttons { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); align-items: center; }
.actions-buttons :deep(.el-button) { margin: 0; }

.runtime-summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2) var(--mrr-space-4);
  align-items: center;
  padding: var(--mrr-space-3);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
  background: var(--mrr-muted);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-control-radius);
}

.runtime-summary b { margin-left: 4px; font-size: var(--mrr-font-size-sm); font-variant-numeric: tabular-nums; color: var(--mrr-card-foreground); }
.runtime-summary-status { font-weight: 600; color: var(--mrr-card-foreground); }
.runtime-note { margin: var(--mrr-space-3) 0; font-size: var(--mrr-font-size-xs); line-height: 1.6; color: var(--mrr-muted-foreground); }
.runtime-filter { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); align-items: center; min-width: 0; margin-top: var(--mrr-space-3); }
.runtime-filter > .el-input { flex: 1 1 240px; min-width: 0; max-width: 360px; }
.runtime-filter > .el-segmented { max-width: 100%; }
.runtime-match-count { margin-inline-start: auto; font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }
.runtime-empty { display: flex; flex-wrap: wrap; gap: var(--mrr-space-3); align-items: center; justify-content: center; color: var(--mrr-muted-foreground); }

.runtime-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3) var(--mrr-space-5);
  align-items: start;
  min-width: 0;
  padding-block: var(--mrr-space-3);
  border-top: 1px solid var(--mrr-border);
}
.runtime-copy { flex: 1 1 280px; min-width: 0; overflow-wrap: anywhere; }
.runtime-value { display: grid; flex: 0 1 300px; gap: var(--mrr-space-1); min-width: 0; max-width: 100%; }
.field-label { display: flex; flex-wrap: wrap; gap: var(--mrr-space-2); align-items: center; }
.field-label strong { font-size: var(--mrr-font-size-sm); font-weight: 600; }

.runtime-source,
.runtime-sensitive { font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }
.runtime-sensitive { color: var(--mrr-tone-warning-ink); }

.field-hint,
.field-meta { font-size: var(--mrr-font-size-xs); line-height: 1.6; color: var(--mrr-muted-foreground); }
.field-hint { margin: var(--mrr-space-1) 0 0; }
.field-meta { display: flex; flex-wrap: wrap; gap: 2px var(--mrr-space-2); overflow-wrap: anywhere; }
.runtime-input-line { display: flex; align-items: center; min-width: 0; min-height: var(--mrr-control-height); }
.number-control { display: flex; gap: var(--mrr-space-2); align-items: center; min-width: 0; }
.number-control :deep(.el-input-number) { width: 148px; max-width: 100%; }
.runtime-unit { flex: none; font-size: var(--mrr-font-size-xs); color: var(--mrr-muted-foreground); }
.runtime-text-control { width: 100%; min-width: 0; }
.reset-link { justify-self: start; padding: 0; margin: 0; }
.runtime-row.is-changed .field-label strong { color: var(--mrr-tone-warning-ink); }
</style>
