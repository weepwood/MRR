<script setup lang="ts">
import type { ScanRecord } from '@/api/types'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getScanByBah, findByCondition } from '@/api/modules/records'

defineOptions({ name: 'ArchiveSearchPage' })

const route = useRoute()
const router = useRouter()

const searchInput = ref('')
const searchMode = ref<'bah' | 'sjh'>('bah')
const loading = ref(false)
const results = ref<ScanRecord[]>([])
const searched = ref(false)

function detectMode(val: string): 'bah' | 'sjh' {
  const num = Number(val.replace(/\D/g, ''))
  return Number.isFinite(num) && num >= 10000000 ? 'sjh' : 'bah'
}

function doSearch() {
  const q = searchInput.value.trim()
  if (!q) { ElMessage.warning('请输入搜索内容'); return }
  searched.value = true
  loading.value = true
  results.value = []
  router.replace({ query: { ...route.query, q, mode: searchMode.value } })

  const req = searchMode.value === 'bah'
    ? getScanByBah(q)
    : findByCondition({ sjh: q })

  req.then(res => {
    results.value = Array.isArray(res.data) ? res.data : []
    if (!results.value.length) ElMessage.info('未找到匹配的档案')
  }).catch(() => {
    ElMessage.error('搜索失败，请重试')
  }).finally(() => {
    loading.value = false
  })
}

function toggleMode() {
  searchMode.value = searchMode.value === 'bah' ? 'sjh' : 'bah'
}

// Route-based search on mount
onMounted(() => {
  const q = String(route.query.q || route.params.keyword || '')
  const mode = String(route.query.mode || '')
  if (q) {
    searchInput.value = q
    searchMode.value = (mode === 'sjh' || mode === 'bah') ? mode : detectMode(q)
    doSearch()
  }
})

watch(() => route.query.q, (val) => {
  if (val && val !== searchInput.value) {
    searchInput.value = String(val)
    searchMode.value = detectMode(String(val))
    doSearch()
  }
})
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <h2>档案搜索</h2>
      <p class="subtitle">输入病案号或上架号搜索对应档案记录</p>
    </div>

    <section class="search-section">
      <div class="search-row">
        <div class="search-input-wrap">
          <input
            v-model="searchInput"
            class="search-input"
            :placeholder="searchMode === 'bah' ? '输入病案号 (BAH)' : '输入上架号 (SJH)'"
            @keydown.enter="doSearch"
          >
          <button class="mode-toggle" :class="searchMode" @click="toggleMode">
            <template v-if="searchMode === 'bah'">BAH</template>
            <template v-else>SJH</template>
          </button>
        </div>
        <button class="btn-primary" :disabled="loading" @click="doSearch">
          {{ loading ? '搜索中...' : '搜索' }}
        </button>
      </div>
      <p v-if="detectMode(searchInput) === 'sjh' && searchInput" class="mode-hint">
        数值 ≥ 10000000，默认按上架号搜索
        <button class="link-btn" @click="toggleMode">切换为病案号搜索</button>
      </p>
    </section>

    <section v-if="loading" class="loading-section">
      <div class="spinner" />
      <p>搜索中...</p>
    </section>

    <section v-else-if="searched && !results.length" class="empty-section">
      <div class="empty-icon">🔍</div>
      <p class="empty-title">未找到匹配的档案</p>
      <p class="empty-desc">请检查输入的病案号或上架号是否正确</p>
    </section>

    <section v-else-if="results.length" class="results-section">
      <div class="results-meta">
        共找到 <strong>{{ results.length }}</strong> 条档案记录
      </div>
      <div class="results-grid">
        <div v-for="item in results" :key="item.id" class="result-card">
          <div class="card-header">
            <span class="card-bah">{{ item.bah || '-' }}</span>
            <span class="card-id">ID: {{ item.id }}</span>
          </div>
          <div class="card-body">
            <div class="card-row">
              <span class="card-label">病人序号</span>
              <span class="card-value">{{ item.brxh || '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">上架号</span>
              <span class="card-value">{{ item.sjh || '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">文件名</span>
              <span class="card-value card-filename">{{ item.filename || '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">文件夹</span>
              <span class="card-value">{{ item.folder || '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">页码</span>
              <span class="card-value">{{ item.pages ?? '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">类型</span>
              <span class="card-value">{{ item.btype ?? '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">扫描人员</span>
              <span class="card-value">{{ item.openerNo || '-' }}</span>
            </div>
            <div class="card-row">
              <span class="card-label">上传日期</span>
              <span class="card-value">{{ item.uploadDate || '-' }}</span>
            </div>
          </div>
          <div class="card-footer">
            <a
              class="card-link"
              :href="`/api/v1/img/${item.bah}`"
              target="_blank"
            >查看图片</a>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  max-width: 1200px;
  padding: 20px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
}

.subtitle {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748b;
}

.search-section {
  margin-bottom: 24px;
}

.search-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input-wrap {
  display: flex;
  flex: 1;
  max-width: 520px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.search-input {
  flex: 1;
  padding: 10px 14px;
  font-family: inherit;
  font-size: 14px;
  color: #1e293b;
  outline: none;
  background: transparent;
  border: none;
}

.search-input::placeholder {
  color: #94a3b8;
}

.mode-toggle {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.5px;
  white-space: nowrap;
  cursor: pointer;
  background: #f8fafc;
  border: none;
  border-left: 1px solid #e2e8f0;
  transition: all 0.15s;
}

.mode-toggle:hover {
  background: #e2e8f0;
}

.mode-toggle.bah {
  color: #0ea5e9;
}

.mode-toggle.sjh {
  color: #8b5cf6;
}

.mode-hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: #64748b;
}

.link-btn {
  padding: 0;
  font-family: inherit;
  font-size: 13px;
  color: #0ea5e9;
  text-decoration: underline;
  cursor: pointer;
  background: none;
  border: none;
}

.btn-primary {
  padding: 10px 24px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  cursor: pointer;
  background: #0ea5e9;
  border: none;
  border-radius: 8px;
  transition: background 0.15s;
}

.btn-primary:hover {
  background: #0284c7;
}

.btn-primary:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.loading-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: #64748b;
}

.spinner {
  width: 32px;
  height: 32px;
  margin-bottom: 12px;
  border: 3px solid #e2e8f0;
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  color: #64748b;
}

.empty-icon {
  margin-bottom: 12px;
  font-size: 40px;
  opacity: 0.5;
}

.empty-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #94a3b8;
}

.empty-desc {
  margin: 0;
  font-size: 14px;
}

.results-section {
  margin-top: 8px;
}

.results-meta {
  margin-bottom: 16px;
  font-size: 14px;
  color: #64748b;
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.result-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.result-card:hover {
  border-color: #0ea5e9;
  box-shadow: 0 4px 16px rgb(14 165 233 / 10%);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.card-bah {
  font-family: "Cascadia Code", "Fira Code", monospace;
  font-size: 15px;
  font-weight: 700;
  color: #0ea5e9;
}

.card-id {
  font-family: "Cascadia Code", "Fira Code", monospace;
  font-size: 11px;
  color: #94a3b8;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
}

.card-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #64748b;
}

.card-value {
  max-width: 60%;
  font-family: "Cascadia Code", "Fira Code", monospace;
  font-size: 13px;
  color: #1e293b;
  text-align: right;
  word-break: break-all;
}

.card-filename {
  color: #0ea5e9;
}

.card-footer {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid #e2e8f0;
}

.card-link {
  font-size: 13px;
  font-weight: 500;
  color: #0ea5e9;
  text-decoration: none;
}

.card-link:hover {
  text-decoration: underline;
}
</style>
