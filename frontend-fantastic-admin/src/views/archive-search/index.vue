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
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 6px 0 0;
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
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.search-input {
  flex: 1;
  padding: 10px 14px;
  border: none;
  outline: none;
  font-size: 14px;
  font-family: inherit;
  color: #1e293b;
  background: transparent;
}

.search-input::placeholder {
  color: #94a3b8;
}

.mode-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border: none;
  border-left: 1px solid #e2e8f0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .5px;
  cursor: pointer;
  background: #f8fafc;
  color: #64748b;
  transition: all .15s;
  white-space: nowrap;
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
  font-size: 13px;
  color: #64748b;
  margin: 8px 0 0;
}

.link-btn {
  background: none;
  border: none;
  color: #0ea5e9;
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
  text-decoration: underline;
  padding: 0;
}

.btn-primary {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  background: #0ea5e9;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background .15s;
  font-family: inherit;
  white-space: nowrap;
}

.btn-primary:hover {
  background: #0284c7;
}

.btn-primary:disabled {
  opacity: .5;
  cursor: not-allowed;
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
  border: 3px solid #e2e8f0;
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin .6s linear infinite;
  margin-bottom: 12px;
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
  font-size: 40px;
  margin-bottom: 12px;
  opacity: .5;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #94a3b8;
  margin: 0 0 4px;
}

.empty-desc {
  font-size: 14px;
  margin: 0;
}

.results-section {
  margin-top: 8px;
}

.results-meta {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 16px;
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.result-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  transition: box-shadow .2s, border-color .2s;
}

.result-card:hover {
  border-color: #0ea5e9;
  box-shadow: 0 4px 16px rgba(14, 165, 233, .1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.card-bah {
  font-size: 15px;
  font-weight: 700;
  color: #0ea5e9;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
}

.card-id {
  font-size: 11px;
  color: #94a3b8;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
}

.card-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-label {
  font-size: 12px;
  color: #64748b;
  flex-shrink: 0;
}

.card-value {
  font-size: 13px;
  color: #1e293b;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  text-align: right;
  word-break: break-all;
  max-width: 60%;
}

.card-filename {
  color: #0ea5e9;
}

.card-footer {
  padding: 10px 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  gap: 8px;
}

.card-link {
  font-size: 13px;
  color: #0ea5e9;
  text-decoration: none;
  font-weight: 500;
}

.card-link:hover {
  text-decoration: underline;
}
</style>
