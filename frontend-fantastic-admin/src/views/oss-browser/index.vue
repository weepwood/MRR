<script setup lang="ts">
import MrrPageHeader from '@/components/MrrPageHeader/index.vue'
import MrrPageShell from '@/components/MrrPageShell/index.vue'
import OssFileExplorer from '@/views/oss-migration/components/OssFileExplorer.vue'

defineOptions({ name: 'OssBrowserPage' })
</script>

<template>
  <MrrPageShell>
    <MrrPageHeader
      title="OSS 文件浏览"
      description="浏览受管 Bucket 中的目录与对象。"
    >
      <template #badge>
        <el-tag effect="plain" type="info" size="small">
          只读
        </el-tag>
      </template>
    </MrrPageHeader>
    <OssFileExplorer />
  </MrrPageShell>
</template>
