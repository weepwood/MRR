<script setup lang="ts">
defineOptions({
  name: 'FaSystemInfo',
})

const visible = defineModel<boolean>({ default: false })
const { product, template, dependencies, devDependencies } = __SYSTEM_INFO__
const shortCommit = computed(() => product.gitCommit === 'unknown' ? 'unknown' : product.gitCommit.slice(0, 12))
</script>

<template>
  <FaDrawer v-model="visible" title="系统信息" :footer="false">
    <FaDivider>
      产品版本
    </FaDivider>
    <div class="space-y-2 text-sm">
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">MRR</span>
        <span class="font-sans">v{{ product.version }}</span>
      </div>
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">Git Commit</span>
        <span class="max-w-56 truncate font-mono" :title="product.gitCommit">{{ shortCommit }}</span>
      </div>
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">构建时间</span>
        <span class="font-sans">{{ product.buildTime }}</span>
      </div>
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">配置结构版本</span>
        <span class="font-sans">{{ product.configurationSchemaVersion }}</span>
      </div>
    </div>

    <FaDivider>
      数据库兼容性
    </FaDivider>
    <div class="space-y-2 text-sm">
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">最低迁移版本</span>
        <span class="font-mono">V{{ product.database.minimumCompatibleMigration }}</span>
      </div>
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">最高迁移版本</span>
        <span class="font-mono">V{{ product.database.maximumCompatibleMigration }}</span>
      </div>
      <div class="flex items-center justify-between gap-4 rounded-lg px-2 py-1.5 hover-bg-secondary">
        <span class="font-bold">兼容上一应用版本</span>
        <span>{{ product.database.backwardCompatibleWithPreviousApplication ? '是' : '否' }}</span>
      </div>
      <div class="rounded-lg px-2 py-1.5 hover-bg-secondary">
        <div class="flex items-center justify-between gap-4">
          <span class="font-bold">允许直接回滚应用</span>
          <span>{{ product.applicationRollback.allowed ? '是' : '否' }}</span>
        </div>
        <p v-if="!product.applicationRollback.allowed" class="mb-0 mt-1 text-xs text-secondary">
          {{ product.applicationRollback.reason }}
        </p>
      </div>
    </div>

    <FaDivider>
      前端工程信息
    </FaDivider>
    <div class="flex items-center justify-between rounded-lg px-2 py-1.5 text-sm hover-bg-secondary">
      <span class="font-bold">前端应用版本</span>
      <span class="font-sans">{{ template.version }}</span>
    </div>

    <FaDivider>
      生产环境依赖
    </FaDivider>
    <ul class="list-none text-sm">
      <li v-for="(val, key) in dependencies" :key="key" class="flex items-center justify-between rounded-lg px-2 py-1.5 hover-bg-secondary">
        <div class="font-bold">
          {{ key }}
        </div>
        <div class="font-sans">
          {{ val }}
        </div>
      </li>
    </ul>

    <FaDivider>
      开发环境依赖
    </FaDivider>
    <ul class="list-none text-sm">
      <li v-for="(val, key) in devDependencies" :key="key" class="flex items-center justify-between rounded-lg px-2 py-1.5 hover-bg-secondary">
        <div class="font-bold">
          {{ key }}
        </div>
        <div class="font-sans">
          {{ val }}
        </div>
      </li>
    </ul>
  </FaDrawer>
</template>
