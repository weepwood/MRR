<script setup lang="ts">
const scroll = useTemplateRef('scroll')
const count = ref(2)
const code = ref('123456')
const completed = ref(0)
const actionCount = ref(0)
</script>

<template>
  <div class="example-stack">
    <div class="example-field">
      <strong>横向滚动与边缘遮罩</strong>
      <div class="example-row">
        <el-button @click="count = 12">
          增加滚动内容
        </el-button>
        <el-button @click="count = 2">
          缩减滚动内容
        </el-button>
        <el-button @click="scroll?.scrollTo(0)">
          回到起点
        </el-button>
      </div>
      <FaScrollArea ref="scroll" horizontal mask :scrollbar="false" gradient-color="var(--mrr-card)" class="special-scroll" data-testid="special-scroll">
        <div class="special-scroll-items">
          <span v-for="n in count" :key="n" class="special-scroll-item">演示 {{ n }}</span>
        </div>
      </FaScrollArea>
    </div>
    <div class="example-field">
      <strong>验证码输入（仅本地合成值）</strong>
      <FaPinInput v-model="code" :length="6" @complete="completed++" />
      <div class="example-row">
        <el-button @click="code = ''">
          清空验证码
        </el-button>
        <el-button @click="code = '012345'">
          填入合成验证码
        </el-button>
      </div>
      <p class="example-note" role="status">
        当前已输入 {{ code.length }} 位 · 完成事件 {{ completed }} 次
      </p>
    </div>
    <div class="example-field">
      <strong>复合字段紧凑操作</strong>
      <div class="special-field">
        <el-input model-value="DEMO-TRACE-001" readonly aria-label="演示追踪编号" />
        <el-button text class="mrr-embedded-icon-action" aria-label="触发紧凑示例操作" @click="actionCount++">
          <FaIcon name="i-lucide:copy" aria-hidden="true" />
        </el-button>
      </div>
      <p class="example-note" role="status">
        已触发紧凑操作 {{ actionCount }} 次（不写入系统剪贴板）
      </p>
    </div>
  </div>
</template>

<style scoped>
.special-scroll {
  width: 100%;
  min-width: 0;
  max-width: 480px;
  padding: var(--mrr-space-2);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.special-scroll-items {
  display: flex;
  gap: var(--mrr-space-2);
  width: max-content;
}

.special-scroll-item {
  display: grid;
  flex: none;
  place-items: center;
  width: 100px;
  height: 42px;
  background: var(--mrr-muted);
  border-radius: var(--mrr-radius-sm);
}

.special-field {
  display: flex;
  gap: var(--mrr-space-1);
  align-items: center;
  min-width: 0;
  max-width: 400px;
  padding: 0 var(--mrr-space-1);
  background: var(--mrr-control-bg);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-sm);
}

.special-field :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.special-field :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
}
</style>
