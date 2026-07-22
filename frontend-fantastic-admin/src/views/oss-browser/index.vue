<script setup lang="ts">
import OssFileExplorer from '@/views/oss-migration/components/OssFileExplorer.vue'

defineOptions({ name: 'OssBrowserPage' })
</script>

<template>
  <div class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">
          OSS Repository Browser
        </p>
        <h2>OSS 文件浏览</h2>
        <p class="subtitle">
          以 Windows 文件资源管理器的方式查看已经上传到 OSS 的病案影像文件。
        </p>
      </div>
    </header>

    <el-alert
      title="当前页面为只读浏览器"
      description="只提供目录查看、文件详情和临时签名预览，不提供删除、移动或重命名，防止误操作生产影像。"
      type="info"
      :closable="false"
      show-icon
    />

    <OssFileExplorer />
  </div>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 18px;
  padding: var(--g-main-padding);
}

.page-header h2,
.subtitle,
.eyebrow {
  margin: 0;
}

.page-header h2 {
  margin-top: 4px;
  font-size: 26px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtitle {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}
</style>
