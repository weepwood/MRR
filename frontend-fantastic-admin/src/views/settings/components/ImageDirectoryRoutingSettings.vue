<script setup lang="ts">
import type { DirectoryRule } from './image-directory-routing'
import { ElMessageBox } from 'element-plus'
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { deleteSetting, getSettingsSchema, setSetting } from '@/api/modules/settings'
import { DIRECTORY_ROUTING_KEY, parseDirectoryRules, previewDirectoryRule, routingNodes, validateDirectoryRules } from './image-directory-routing'

const rules = ref<DirectoryRule[]>([])
const defaults = ref<DirectoryRule[]>([])
const baseline = ref('[]')
const overridden = ref(false)
const loaded = ref(false)
const loading = ref(false)
const busy = ref(false)
const needsReload = ref(false)
const feedback = ref('')
const feedbackType = ref<'error' | 'success'>('error')
const folder = ref('')
let alive = true
const active = ref(true)
let generation = 0
const isCurrent = (attempt: number) => alive && active.value && generation === attempt
const isDirty = computed(() => JSON.stringify(rules.value) !== baseline.value)
const invalid = computed(() => validateDirectoryRules(rules.value))
const disabled = computed(() => !active.value || loading.value || busy.value || !loaded.value || needsReload.value)
const canRestore = computed(() => active.value && loaded.value && overridden.value && !loading.value && !busy.value)
const preview = computed(() => previewDirectoryRule(folder.value, rules.value))

async function load() {
  if (!active.value || loading.value || busy.value) { return }
  const attempt = generation
  loading.value = true
  try {
    if (isDirty.value && !await confirm('重新加载会丢弃本页未保存的目录规则。', '确认重新加载')) { return }
    if (!isCurrent(attempt)) { return }
    const response = await getSettingsSchema()
    if (!isCurrent(attempt)) { return }
    const item = response.data?.items.find(item => item.key === DIRECTORY_ROUTING_KEY)
    if (!item) { throw new Error('missing schema') }
    const builtIn = parseDirectoryRules(item.defaultValue)
    defaults.value = builtIn
    overridden.value = Boolean(item.overrideValue?.trim()) || (item.valid === false && item.overrideValue != null)
    loaded.value = true
    let effective: DirectoryRule[]
    try {
      if (item.valid === false) { throw new Error('invalid server rules') }
      effective = parseDirectoryRules(item.effectiveValue)
    }
    catch {
      needsReload.value = true
      feedbackType.value = 'error'
      feedback.value = '服务器上的目录规则无效，读取已停用。请恢复内置目录规则，或联系管理员修复后重新加载。'
      return
    }
    rules.value = effective
    baseline.value = JSON.stringify(effective)
    needsReload.value = false
    feedback.value = ''
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedbackType.value = 'error'
      feedback.value = '目录路由加载失败，当前编辑不可保存。请重新加载；已有服务器配置未被本页修改。'
    }
  }
  finally { if (alive) { loading.value = false } }
}

async function confirm(message: string, title: string) {
  const attempt = generation
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    return isCurrent(attempt)
  }
  catch { return false }
}

async function save(restore = false) {
  if (restore ? !canRestore.value : disabled.value || !isDirty.value || !!invalid.value) { return }
  const attempt = generation
  const snapshot = JSON.stringify(rules.value)
  busy.value = true
  try {
    const message = restore
      ? '清除服务器上的规则覆盖，恢复原有内置日期分流；本页未保存编辑也会被丢弃。'
      : `本次保存 ${rules.value.length} 条规则${rules.value.length ? '' : '，所有有目录记录将使用通用节点'}。`
    if (!await confirm(`${message}保存后影响后续影像读取；正在运行的导出或迁移不会冻结旧规则，请先暂停相关任务。`, restore ? '恢复内置目录规则' : '确认保存目录规则')) { return }
    if (restore) { await deleteSetting(DIRECTORY_ROUTING_KEY) }
    else { await setSetting(DIRECTORY_ROUTING_KEY, snapshot) }
    if (!isCurrent(attempt)) { return }
    rules.value = restore ? defaults.value.map(rule => ({ ...rule })) : parseDirectoryRules(snapshot)
    baseline.value = JSON.stringify(rules.value)
    overridden.value = !restore
    needsReload.value = false
    feedbackType.value = 'success'
    feedback.value = restore ? '已恢复内置目录规则。' : '目录规则已保存，后续读取使用新规则。'
  }
  catch {
    if (isCurrent(attempt)) {
      needsReload.value = true
      feedbackType.value = 'error'
      loaded.value = false
      feedback.value = '未能确认保存结果，服务器可能已接收变更。请重新加载核对后再操作。'
    }
  }
  finally { if (alive) { busy.value = false } }
}

onMounted(load)
onActivated(() => { active.value = true })
onDeactivated(() => {
  active.value = false
  generation++
  if (busy.value || loading.value) {
    needsReload.value = true
    loaded.value = false
    feedbackType.value = 'error'
    feedback.value = '页面切换中断了结果确认，请重新加载服务器规则后再操作。'
  }
})
onBeforeUnmount(() => { alive = false; active.value = false; generation++ })
</script>

<template>
  <section class="routing-settings" aria-labelledby="directory-routing-title">
    <div class="routing-heading">
      <div>
        <h4 id="directory-routing-title">
          目录路由规则
        </h4><p>精确目录优先于年月；未命中使用通用节点。目录名称保持原样，24.04 与 2024.04 分别匹配。</p>
      </div>
      <el-tag v-if="loaded" :type="overridden ? 'warning' : 'info'">
        {{ overridden ? '自定义规则' : '内置规则' }}
      </el-tag>
    </div>
    <el-alert v-if="feedback" :title="feedback" :type="feedbackType" :closable="false" show-icon role="status" />
    <div class="routing-toolbar">
      <span>{{ loading ? '正在读取服务器规则…' : `${rules.length} 条规则${isDirty ? ' · 有未保存修改' : ''}` }}</span>
      <el-button :disabled="loading || busy" @click="load">
        重新加载目录规则
      </el-button>
    </div>
    <fieldset :disabled="disabled" class="routing-fields">
      <legend class="sr-only">
        目录规则编辑
      </legend>
      <div class="routing-list">
        <div v-for="(rule, index) in rules" :key="index" class="routing-row">
          <el-select v-model="rule.type" :aria-label="`规则 ${index + 1} 匹配方式`" :disabled="disabled">
            <el-option label="精确目录" value="exact" /><el-option label="年月" value="month" />
          </el-select>
          <el-input v-model="rule.value" :aria-label="`规则 ${index + 1} 目录`" :placeholder="rule.type === 'exact' ? '2026.06.05' : '2026.06'" :maxlength="10" :disabled="disabled" />
          <el-select v-model="rule.node" :aria-label="`规则 ${index + 1} 目标节点`" :disabled="disabled">
            <el-option v-for="node in routingNodes" :key="node.value" :label="node.label" :value="node.value" />
          </el-select>
          <el-button :aria-label="`删除规则 ${index + 1}`" :disabled="disabled" @click="rules.splice(index, 1)">
            删除
          </el-button>
        </div>
      </div>
      <p v-if="!rules.length" class="routing-note">
        没有日期分流规则；保存后所有有目录记录均走通用节点，无目录记录仍走默认节点。
      </p>
      <el-button :disabled="disabled || rules.length >= 200" @click="rules.push({ type: 'exact', value: '', node: 'ba01' })">
        添加目录规则
      </el-button>
    </fieldset>
    <p v-if="invalid && loaded" class="routing-error" role="alert">
      {{ invalid }}
    </p>
    <div class="routing-preview">
      <label for="directory-route-preview">目录匹配预览</label>
      <el-input id="directory-route-preview" v-model="folder" :disabled="!loaded" placeholder="输入完整目录名，如 2026.06.05" :maxlength="128" />
      <p aria-live="polite">
        {{ preview }}（基于本页规则，不访问影像文件）
      </p>
    </div>
    <div class="routing-actions">
      <span>目录规则单独保存，不随页面上方的系统设置保存。</span>
      <el-button v-auth="'system:manage'" :disabled="!canRestore" @click="save(true)">
        恢复内置目录规则
      </el-button>
      <el-button v-auth="'system:manage'" type="primary" :loading="busy" :disabled="disabled || !isDirty || !!invalid" @click="save()">
        保存目录规则
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.routing-settings { display: grid; gap: var(--mrr-space-4); padding: var(--mrr-space-5); background: var(--mrr-card); border: 1px solid var(--mrr-border); border-radius: var(--mrr-radius-xl); }

.routing-heading,
.routing-toolbar,
.routing-actions { display: flex; flex-wrap: wrap; gap: var(--mrr-space-3); align-items: center; justify-content: space-between; }
.routing-heading h4 { margin: 0; font-size: 15px; }

.routing-heading p,
.routing-note,
.routing-preview p,
.routing-actions span,
.routing-toolbar { margin: 4px 0 0; font-size: 12px; color: var(--mrr-muted-foreground); }
.routing-fields { display: grid; gap: var(--mrr-space-3); padding: 0; margin: 0; border: 0; }
.routing-list { display: grid; gap: var(--mrr-space-3); max-height: 400px; overflow-y: auto; }
.routing-row { display: grid; grid-template-columns: 120px minmax(120px, 1fr) 120px auto; gap: var(--mrr-space-2); }
.routing-preview { display: grid; gap: var(--mrr-space-2); }
.routing-preview label { font-size: 13px; }
.routing-error { margin: 0; color: var(--mrr-destructive); }

@media (max-width: 1100px) { .routing-row { grid-template-columns: minmax(100px, 1fr) minmax(120px, 1fr); } }
</style>
