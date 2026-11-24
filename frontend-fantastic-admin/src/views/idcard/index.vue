<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import MrrFilterActionButton from '@/components/MrrFilterActionButton/index.vue'

const ScanImgPage = defineAsyncComponent(() => import('../scan-img/index.vue'))
const userStore = useUserStore()
const idCard = ref('')
const activeId = ref('')

const isValid = computed(() => /^\d{15}(?:\d{2}[\dX])?$/i.test(idCard.value.trim()))

function handleSearch() {
  if (!isValid.value) {
    return
  }
  // 仅在当前页面内存传递，不进入路由、浏览器历史或持久化存储。
  activeId.value = idCard.value.trim()
  idCard.value = ''
}
function resetSearch() {
  activeId.value = ''
  idCard.value = ''
}

// 账号切换/退出时同步销毁当前查询；刷新或离开路由也不会恢复身份证。
watch(() => userStore.token, resetSearch, { flush: 'sync' })
</script>

<template>
  <div v-if="activeId" class="query-results">
    <div class="query-results-toolbar">
      <el-button @click="resetSearch">
        返回查询
      </el-button>
    </div>
    <ScanImgPage :id-card="activeId" />
  </div>
  <div v-else class="search-page">
    <div class="search-card">
      <!-- Logo / 标题区 -->
      <div class="search-header">
        <div class="search-logo">
          <i class="i-lucide:file-search" />
        </div>
        <h1 class="search-title">
          病案图像查询
        </h1>
        <p class="search-subtitle">
          请输入患者身份证号，查询对应的住院病案图像
        </p>
      </div>

      <!-- 搜索区 -->
      <div class="search-form">
        <div class="search-input-wrap">
          <el-input
            v-model="idCard"
            size="large"
            placeholder="请输入18位身份证号"
            maxlength="18"
            clearable
            autocomplete="off"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <i class="i-lucide:contact-round prefix-icon" />
            </template>
          </el-input>
        </div>
        <MrrFilterActionButton
          action="search"
          type="primary"
          size="large"
          :disabled="!isValid"
          class="search-btn"
          @click="handleSearch"
        >
          查询病案
        </MrrFilterActionButton>
      </div>

      <!-- 输入提示 -->
      <p v-if="idCard && !isValid" class="search-hint">
        请输入正确格式的身份证号
      </p>
    </div>
  </div>
</template>

<style scoped>
.query-results {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
}

.query-results-toolbar {
  flex: none;
  padding: 8px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--mrr-border);
}

.query-results :deep(.image-gallery) {
  flex: 1;
  height: auto;
  min-height: 0;
}

.search-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(135deg, var(--mrr-muted) 0%, var(--mrr-background) 60%, var(--mrr-muted) 100%);
}

.prefix-icon {
  font-size: 16px;
}

.search-card {
  width: 100%;
  max-width: 480px;
  padding: 48px var(--mrr-space-8) var(--mrr-space-8);
  background: var(--surface);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  box-shadow: var(--mrr-shadow-md);
}

/* ===== 头部 ===== */
.search-header {
  margin-bottom: var(--mrr-space-8);
  text-align: center;
}

.search-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: var(--mrr-space-5);
  font-size: 32px;
  color: var(--surface);
  background: linear-gradient(135deg, var(--color-primary), var(--mrr-tone-violet));
  border-radius: var(--mrr-radius-2xl);
}

.search-title {
  margin: 0 0 var(--mrr-space-2);
  font-size: 24px;
  font-weight: 700;
  color: var(--mrr-foreground);
  letter-spacing: -0.02em;
}

.search-subtitle {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
}

/* ===== 搜索区 ===== */
.search-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-input-wrap {
  width: 100%;
}

.search-btn {
  width: 100%;
  height: var(--mrr-control-height);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-radius: var(--mrr-radius-lg);
}

/* ===== 提示 ===== */
.search-hint {
  margin: 8px 0 0;
  font-size: var(--mrr-font-size-xs);
  color: var(--el-color-warning);
}

/* ===== 响应式 ===== */
@media (max-width: 520px) {
  .search-card {
    padding: var(--mrr-space-8) 24px 32px;
  }

  .search-title {
    font-size: 20px;
  }
}
</style>
