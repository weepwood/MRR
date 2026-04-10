<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const idCard = ref('')
const loading = ref(false)
const errorMsg = ref('')

const isValid = computed(() => /^\d{15}(?:\d{2}[\dX])?$/i.test(idCard.value.trim()))

function handleSearch() {
  if (!isValid.value) {
    return
  }
  errorMsg.value = ''
  loading.value = true
  // 跳转到图库页,由目标页负责查询
  router.push(`/idcard/${idCard.value.trim()}`).finally(() => {
    loading.value = false
  })
}
</script>

<template>
  <div class="search-page">
    <div class="search-card">
      <!-- Logo / 标题区 -->
      <div class="search-header">
        <div class="search-logo">
          <i class="i-ant-design:file-search-outlined" />
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
            :disabled="loading"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <i class="i-ant-design:idcard-outlined" style="font-size:16px;" />
            </template>
          </el-input>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          :disabled="!isValid"
          class="search-btn"
          @click="handleSearch"
        >
          查询病案
        </el-button>
      </div>

      <!-- 输入提示 -->
      <p v-if="idCard && !isValid" class="search-hint">
        请输入正确格式的身份证号
      </p>

      <!-- 错误提示 -->
      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="false"
        class="search-error"
      />
    </div>
  </div>
</template>

<style scoped>
.search-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f4ff 0%, #fafbff 60%, #f0f9ff 100%);
  padding: 24px;
}

.search-card {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(59, 130, 246, 0.1);
  padding: 48px 40px 40px;
}

/* ===== 头部 ===== */
.search-header {
  text-align: center;
  margin-bottom: 36px;
}

.search-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 16px;
  margin-bottom: 20px;
  font-size: 32px;
  color: #fff;
}

.search-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.3px;
}

.search-subtitle {
  margin: 0;
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
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
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  letter-spacing: 0.5px;
}

/* ===== 提示 ===== */
.search-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}

.search-error {
  margin-top: 16px;
  border-radius: 10px;
}

/* ===== 响应式 ===== */
@media (max-width: 520px) {
  .search-card {
    padding: 36px 24px 32px;
  }
  .search-title {
    font-size: 20px;
  }
}
</style>
