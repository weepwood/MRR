<script setup lang="ts">
import type { ExampleId } from '../catalog'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { computed, ref, useId } from 'vue'

defineProps<{ example: ExampleId }>()

const id = useId()
const keyword = ref('')
const query = ref('')
const page = ref(1)
const message = ref('表格数据仅用于组件演示。')
const rows = [
  { id: 'DEMO-001', name: '按钮样式验收', status: 'completed', label: '已完成' },
  { id: 'DEMO-002', name: '表单布局验收', status: 'processing', label: '进行中' },
  { id: 'DEMO-003', name: '暗色主题验收', status: 'pending', label: '待处理' },
  { id: 'DEMO-004', name: '错误状态验收', status: 'failed', label: '需检查' },
  { id: 'DEMO-005', name: '窄屏布局验收', status: 'completed', label: '已完成' },
]
const matches = computed(() => rows.filter(row => `${row.id} ${row.name}`.toLowerCase().includes(query.value.toLowerCase())))
const visibleRows = computed(() => matches.value.slice((page.value - 1) * 3, page.value * 3))
const actions: MrrTableAction[] = [
  { key: 'view', label: '查看示例', icon: 'i-lucide:eye' },
  { key: 'edit', label: '编辑示例', icon: 'i-lucide:pencil' },
  { key: 'remove', label: '删除示例', icon: 'i-lucide:trash-2', tone: 'danger', disabled: true, disabledReason: '展示数据不可删除' },
]

function search(reset = false) {
  if (reset) {
    keyword.value = ''
  }
  query.value = keyword.value.trim()
  page.value = 1
}
</script>

<template>
  <div v-if="example === 'metrics'" class="mrr-metric-grid mrr-metric-grid--three">
    <MrrMetricCard label="演示项目" :value="24" suffix="项" note="合成数据，用于检查数字与单位" icon="i-lucide:layers" />
    <MrrMetricCard label="演示通过率" :value="92" suffix="%" note="合成指标，示例分母为 100 项" tone="green" value-tone="success" />
    <MrrMetricCard label="演示状态" value="等待复核" compact-value note="长文本值按内容排版" tone="amber" />
  </div>
  <div v-else class="example-stack">
    <MrrDataTablePanel title="组件验收示例" :count="matches.length">
      <template #filters>
        <form @submit.prevent="search()">
          <MrrFilterBar embedded>
            <div class="example-field example-full-width">
              <label :for="`${id}-query`">筛选示例</label>
              <FaInput :id="`${id}-query`" v-model="keyword" placeholder="示例编号或名称" class="example-full-width" />
            </div>
            <template #actions>
              <FaButton type="submit">
                查询示例
              </FaButton>
              <FaButton type="button" variant="outline" @click="search(true)">
                重置筛选
              </FaButton>
            </template>
          </MrrFilterBar>
        </form>
      </template>
      <el-table :data="visibleRows" empty-text="没有匹配的演示项目">
        <el-table-column prop="id" label="示例编号" min-width="125" />
        <el-table-column prop="name" label="示例名称" min-width="160" />
        <el-table-column label="状态" min-width="110">
          <template #default="{ row }">
            <MrrStatusTag :status="row.status" :label="row.label" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="116" fixed="right">
          <template #default="{ row }">
            <MrrTableActions :actions="actions" :max-inline="1" @select="key => message = `${row.id}：已触发${key === 'view' ? '查看' : '编辑'}示例`" />
          </template>
        </el-table-column>
      </el-table>
      <template #pagination>
        <el-pagination v-model:current-page="page" :page-size="3" :total="matches.length" layout="total, prev, pager, next" small />
      </template>
    </MrrDataTablePanel>
    <p class="example-note" role="status">
      {{ message }}
    </p>
    <div class="example-row" aria-label="状态标签示例">
      <MrrStatusTag status="completed" label="已完成" />
      <MrrStatusTag status="processing" label="进行中" />
      <MrrStatusTag status="pending" label="待处理" />
      <MrrStatusTag status="failed" label="失败" />
      <MrrStatusTag label="未知" />
    </div>
  </div>
</template>
