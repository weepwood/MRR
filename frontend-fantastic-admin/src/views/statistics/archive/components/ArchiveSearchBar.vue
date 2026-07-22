<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { requiresSjhForBah } from '@/utils/medical-record-code'
import {
  resolveArchiveAccessMode,
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

const runtimeAccessMode = typeof document === 'undefined'
  ? 'internal'
  : resolveArchiveAccessMode('internal', document.documentElement.dataset.mrrAccessMode || '')
const showSearchCard = shouldShowArchiveSearchCard(runtimeAccessMode)
const sjhRequired = computed(() => !searchIdCard.value.trim() && requiresSjhForBah(searchBah.value))
</script>

<template>
  <section v-if="showSearchCard" class="search-card">
    <div class="search-bar">
      <div class="search-fields">
        <div class="record-search-fields">
          <el-input v-model="searchBah" name="archive-bah" autocomplete="off" aria-label="病案号" clearable placeholder="病案号" @keyup.enter="emit('search')" />
          <el-input
            v-model="searchSjh"
            name="archive-sjh"
            autocomplete="off"
            aria-label="上架号"
            :aria-required="sjhRequired"
            clearable
            :placeholder="sjhRequired ? '上架号（当前病案号必填）' : '上架号'"
            @keyup.enter="emit('search')"
          />
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
          />
          <el-button type="primary" :icon="Search" :loading="loading" @click="emit('search')">
            查询
          </el-button>
        </div>
      </div>
      <p v-if="sjhRequired" class="search-rule-hint">
        病案号大于等于 10000000 时不再唯一，必须同时输入唯一上架号。
      </p>
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
  border-radius: 12px;
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

.search-rule-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning-dark-2);
}
</style>
