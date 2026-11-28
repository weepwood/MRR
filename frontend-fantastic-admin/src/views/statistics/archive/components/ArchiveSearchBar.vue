<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrInputLengthBadge from '@/components/MrrInputLengthBadge/index.vue'
import {
  describeIdCardInput,
  describeMedicalRecordCodeInput,
  normalizeIdCardInput,
} from '@/utils/identifier-input'
import { MEDICAL_RECORD_CODE_LENGTH, requiresSjhForBah } from '@/utils/medical-record-code'
import {
  archiveAccessMode,
  shouldShowArchiveSearchCard,
} from '../access-mode'

defineOptions({ name: 'ArchiveSearchBar' })

const props = withDefaults(defineProps<{
  loading?: boolean
  /**
   * 空态（尚未查询）使用：显示可见字段标签与查询规则说明。
   * 查询后的 320px 侧栏保持紧凑结构（标签容器 display: contents），号码宽度约束不变。
   */
  labelled?: boolean
  embedded?: boolean
  disabled?: boolean
  errorId?: string
}>(), {
  loading: false,
  labelled: false,
  embedded: false,
  disabled: false,
})

const emit = defineEmits<{
  search: []
}>()
const searchBah = defineModel<string>('searchBah', { default: '' })
const searchSjh = defineModel<string>('searchSjh', { default: '' })
const searchIdCard = defineModel<string>('searchIdCard', { default: '' })

const showSearchCard = computed(() => shouldShowArchiveSearchCard(archiveAccessMode.value))
const hintId = `archive-search-hint-${useId()}`
const fieldTag = computed(() => props.labelled ? 'label' : 'div')
const describedBy = computed(() => [props.labelled ? hintId : undefined, props.errorId].filter(Boolean).join(' ') || undefined)

const bahHint = computed(() => describeMedicalRecordCodeInput(searchBah.value))
const sjhHint = computed(() => describeMedicalRecordCodeInput(searchSjh.value))
const idCardHint = computed(() => describeIdCardInput(searchIdCard.value))

/**
 * 位数徽标只在输入框聚焦时显示：离开输入框后号码仍按原始值展示，
 * 常驻徽标反而和号码抢 141px 半栏的空间。
 */
const focusedField = ref<'bah' | 'sjh' | 'idCard' | null>(null)

/** 身份证号优先于病案号/上架号生效，此时上架号不再强制。 */
const idCardTakesOver = computed(() => Boolean(normalizeIdCardInput(searchIdCard.value)))
const sjhRequired = computed(() => !idCardTakesOver.value && requiresSjhForBah(searchBah.value))

watch(searchIdCard, (value) => {
  const normalized = normalizeIdCardInput(value)
  if (normalized !== value) {
    searchIdCard.value = normalized
  }
})

function submitSearch() {
  if (!props.disabled && !props.loading) { emit('search') }
}

function onFieldFocus(field: 'bah' | 'sjh' | 'idCard') {
  focusedField.value = field
}

function onFieldBlur() {
  focusedField.value = null
}
</script>

<template>
  <section v-if="showSearchCard" class="search-card" :class="{ 'is-labelled': props.labelled, 'is-embedded': props.embedded }">
    <div class="search-bar">
      <div class="search-fields">
        <div class="record-search-fields">
          <component :is="fieldTag" class="search-field">
            <span v-if="props.labelled" class="search-field__label">病案号</span>
            <el-input
              v-model="searchBah"
              name="archive-bah"
              class="archive-identifier-input"
              autocomplete="off"
              aria-label="病案号"
              :aria-describedby="describedBy"
              :disabled="props.disabled"
              clearable
              placeholder="病案号"
              @focus="onFieldFocus('bah')"
              @blur="onFieldBlur()"
              @keyup.enter="submitSearch"
            >
              <template #suffix>
                <MrrInputLengthBadge v-if="focusedField === 'bah'" :length="bahHint.length" :total="MEDICAL_RECORD_CODE_LENGTH" :tone="bahHint.tone" label="病案号" />
              </template>
            </el-input>
          </component>
          <component :is="fieldTag" class="search-field">
            <span v-if="props.labelled" class="search-field__label">
              上架号<em v-if="sjhRequired">（必填）</em>
            </span>
            <el-input
              v-model="searchSjh"
              name="archive-sjh"
              class="archive-identifier-input"
              autocomplete="off"
              aria-label="上架号"
              :aria-required="sjhRequired"
              :aria-describedby="describedBy"
              :disabled="props.disabled"
              clearable
              :placeholder="sjhRequired ? '上架号 *' : '上架号'"
              @focus="onFieldFocus('sjh')"
              @blur="onFieldBlur()"
              @keyup.enter="submitSearch"
            >
              <template #suffix>
                <MrrInputLengthBadge v-if="focusedField === 'sjh'" :length="sjhHint.length" :total="MEDICAL_RECORD_CODE_LENGTH" :tone="sjhHint.tone" label="上架号" />
              </template>
            </el-input>
          </component>
        </div>
        <div class="id-card-search-fields">
          <component :is="fieldTag" class="search-field">
            <span v-if="props.labelled" class="search-field__label">身份证号</span>
            <el-input
              v-model="searchIdCard"
              name="archive-id-card"
              class="archive-identifier-input"
              autocomplete="off"
              aria-label="身份证号"
              :aria-describedby="describedBy"
              :disabled="props.disabled"
              clearable
              maxlength="18"
              placeholder="身份证号"
              @keyup.enter="submitSearch"
              @focus="onFieldFocus('idCard')"
              @blur="onFieldBlur()"
            >
              <template #suffix>
                <MrrInputLengthBadge v-if="focusedField === 'idCard'" :length="idCardHint.length" :tone="idCardHint.tone" label="身份证号" />
              </template>
            </el-input>
          </component>
          <MrrFilterActionButton action="search" :pending="loading" :busy="loading" :disabled="props.disabled" type="primary" @click="submitSearch">
            查询
          </MrrFilterActionButton>
        </div>
      </div>
      <p v-if="props.labelled" :id="hintId" class="search-hint" :class="{ 'sr-only': props.embedded }">
        病案号或上架号填写其一即可；病案号大于等于 10000000 时须同时填写上架号。填写身份证号时优先按身份证查询，并列出该患者的全部病案。
      </p>
    </div>
  </section>
</template>

<style src="./archive-identifier-input.css"></style>

<style scoped>
.search-card {
  display: grid;
  gap: var(--mrr-space-3);
  padding: var(--mrr-card-inset-compact);
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
}

.search-card.is-embedded {
  padding: 0;
  background: transparent;
  border: 0;
}

.search-bar {
  display: grid;
  gap: 8px;
}

.search-fields {
  display: grid;
  gap: 8px;
}

.record-search-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.record-search-fields .el-input {
  min-width: 0;
}

/* 紧凑模式：标签容器不参与布局，输入框仍是网格的直接子项，与改造前完全一致。 */
.search-field {
  display: contents;
}

.is-labelled .search-field {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
}

.search-field__label {
  font-size: var(--mrr-font-size-xs);
  font-weight: 600;
  color: var(--mrr-foreground);
}

.search-field__label em {
  font-style: normal;
  color: var(--mrr-tone-warning);
}

/* 带标签时查询按钮与输入框底边对齐。 */
.is-labelled .id-card-search-fields {
  align-items: end;
}

.is-labelled .search-fields {
  gap: var(--mrr-space-3);
}

.search-hint {
  margin: 0;
  font-size: var(--mrr-font-size-xs);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.id-card-search-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

/* 查询按钮收窄左右内边距，把侧栏里最紧的十几像素让给 18 位身份证号。 */
.id-card-search-fields :deep(.el-button) {
  padding-right: 6px;
  padding-left: 6px;
}

/* 身份证 18 位是病案号的两倍多字符，收一号字才装得下完整号码。 */
.id-card-search-fields :deep(.el-input__inner) {
  font-size: var(--mrr-font-size-sm);
}
</style>
