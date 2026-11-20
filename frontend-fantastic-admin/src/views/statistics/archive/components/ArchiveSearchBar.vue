<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{
  search: []
}>()
const searchBah = defineModel<string>('searchBah', { default: '' })
const searchSjh = defineModel<string>('searchSjh', { default: '' })
const searchIdCard = defineModel<string>('searchIdCard', { default: '' })

const showSearchCard = computed(() => shouldShowArchiveSearchCard(archiveAccessMode.value))

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

function onFieldFocus(field: 'bah' | 'sjh' | 'idCard') {
  focusedField.value = field
}

function onFieldBlur() {
  focusedField.value = null
}
</script>

<template>
  <section v-if="showSearchCard" class="search-card">
    <div class="search-bar">
      <div class="search-fields">
        <div class="record-search-fields">
          <el-input
            v-model="searchBah"
            name="archive-bah"
            autocomplete="off"
            aria-label="病案号"
            clearable
            placeholder="病案号"
            @focus="onFieldFocus('bah')"
            @blur="onFieldBlur()"
            @keyup.enter="emit('search')"
          >
            <template #suffix>
              <MrrInputLengthBadge v-if="focusedField === 'bah'" :length="bahHint.length" :total="MEDICAL_RECORD_CODE_LENGTH" :tone="bahHint.tone" label="病案号" />
            </template>
          </el-input>
          <el-input
            v-model="searchSjh"
            name="archive-sjh"
            autocomplete="off"
            aria-label="上架号"
            :aria-required="sjhRequired"
            clearable
            :placeholder="sjhRequired ? '上架号 *' : '上架号'"
            @focus="onFieldFocus('sjh')"
            @blur="onFieldBlur()"
            @keyup.enter="emit('search')"
          >
            <template #suffix>
              <MrrInputLengthBadge v-if="focusedField === 'sjh'" :length="sjhHint.length" :total="MEDICAL_RECORD_CODE_LENGTH" :tone="sjhHint.tone" label="上架号" />
            </template>
          </el-input>
        </div>
        <div class="id-card-search-fields">
          <el-input
            v-model="searchIdCard"
            name="archive-id-card"
            autocomplete="off"
            aria-label="身份证号"
            clearable
            maxlength="18"
            placeholder="身份证号"
            @keyup.enter="emit('search')"
            @focus="onFieldFocus('idCard')"
            @blur="onFieldBlur()"
          >
            <template #suffix>
              <MrrInputLengthBadge v-if="focusedField === 'idCard'" :length="idCardHint.length" :tone="idCardHint.tone" label="身份证号" />
            </template>
          </el-input>
          <MrrFilterActionButton action="search" :pending="loading" :busy="loading" type="primary" @click="emit('search')">
            查询
          </MrrFilterActionButton>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.search-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: var(--mrr-radius-card);
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

/*
 * 半栏只有 141px：号码 14px + 0.09em 字距，逐位更好认；字体跟随项目统一字体，
 * tabular-nums 让数字等宽、位数变化时不抖动。
 * 字号变大后的空间由内边距和徽标让出来 —— 收窄左右内边距、缩小后缀图标与徽标，
 * 保证 8 位病案号/上架号、18 位身份证都能整段显示，不出现横向滚动。
 */
.search-fields :deep(.el-input__inner) {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.07em;
}

/* 身份证 18 位是病案号的两倍多字符，收一号字才装得下完整号码。 */
.id-card-search-fields :deep(.el-input__inner) {
  font-size: 13px;
}

.search-fields :deep(.el-input__wrapper) {
  padding: 1px 6px 1px 8px;
}

/* 清空叉号在 14px 号码旁边显得过大，缩到与文字同高，顺带再省几像素。 */
.search-fields :deep(.el-input__clear) {
  font-size: 12px;
}

/* 清空按钮和位数徽标共用 suffix 区，留一点间距免得挤成一个图标。 */
.search-fields :deep(.el-input__suffix-inner) {
  gap: 3px;
}

/* Element 给后缀首个元素加了 8px 左外边距，与外层内边距重复，这里让给号码。 */
.search-fields :deep(.el-input__suffix-inner > :first-child) {
  margin-left: 0;
}

/*
 * 位数徽标排在清空叉号左侧（EP 的 DOM 顺序叉号在前，用 order 反转）：
 * 徽标随聚焦出现/消失，宽度变化只发生在叉号左侧，叉号永远贴住右缘、位置不动。
 */
.search-fields :deep(.el-input__suffix-inner .length-badge) {
  order: -1;
  margin-left: 0;
}
</style>
