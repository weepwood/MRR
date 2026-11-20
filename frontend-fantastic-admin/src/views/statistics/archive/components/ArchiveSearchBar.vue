<script setup lang="ts">
import { computed, watch } from 'vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'
import MrrInputLengthBadge from '@/components/MrrInputLengthBadge/index.vue'
import {
  describeIdCardInput,
  describeMedicalRecordCodeInput,
  normalizeIdCardInput,
  normalizeMedicalRecordCodeInput,
} from '@/utils/identifier-input'
import { padMedicalRecordCode, requiresSjhForBah } from '@/utils/medical-record-code'
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

/** 病案号、上架号都是 8 位；身份证 15 位或 18 位。 */
const CODE_LENGTH = 8

const showSearchCard = computed(() => shouldShowArchiveSearchCard(archiveAccessMode.value))

const bahHint = computed(() => describeMedicalRecordCodeInput(searchBah.value))
const sjhHint = computed(() => describeMedicalRecordCodeInput(searchSjh.value))
const idCardHint = computed(() => describeIdCardInput(searchIdCard.value))

/** 身份证号优先于病案号/上架号生效，此时上架号不再强制。 */
const idCardTakesOver = computed(() => Boolean(normalizeIdCardInput(searchIdCard.value)))
const sjhRequired = computed(() => !idCardTakesOver.value && requiresSjhForBah(searchBah.value))

/**
 * 粘贴常带上空格、全角空格或末位小写 x。只在归一化前后不一致时回写，
 * 手敲数字时值不变、不会触发写回，也就不会有光标跳到末尾的问题。
 */
const normalizers: [typeof searchBah, (value: unknown) => string][] = [
  [searchBah, normalizeMedicalRecordCodeInput],
  [searchSjh, normalizeMedicalRecordCodeInput],
  [searchIdCard, normalizeIdCardInput],
]

for (const [model, normalize] of normalizers) {
  watch(model, (value) => {
    const normalized = normalize(value)
    if (normalized !== value) {
      model.value = normalized
    }
  })
}

/**
 * 离开输入框时把不足 8 位的纯数字编号补前导零。
 *
 * 台账里的号是 8 位（00123456），但录入时常写成 123456。补零放在失焦时做：
 * 敲键过程中不打断输入，离开后输入框里留下的就是实际会查的那个号，
 * 用户能一眼看出系统按哪个号匹配。非纯数字的历史异常值原样保留。
 */
function padCodeOnBlur(target: 'bah' | 'sjh') {
  // 模板里 defineModel 的 ref 会被自动解包，只能在这里按字段取回 ref 本身
  const model = target === 'bah' ? searchBah : searchSjh
  const padded = padMedicalRecordCode(model.value)
  if (padded !== model.value) {
    model.value = padded
  }
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
            @blur="padCodeOnBlur('bah')"
            @keyup.enter="emit('search')"
          >
            <template #suffix>
              <MrrInputLengthBadge :length="bahHint.length" :total="CODE_LENGTH" :tone="bahHint.tone" label="病案号" />
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
            @blur="padCodeOnBlur('sjh')"
            @keyup.enter="emit('search')"
          >
            <template #suffix>
              <MrrInputLengthBadge :length="sjhHint.length" :total="CODE_LENGTH" :tone="sjhHint.tone" label="上架号" />
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
          >
            <template #suffix>
              <MrrInputLengthBadge :length="idCardHint.length" :tone="idCardHint.tone" label="身份证号" />
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
</style>
