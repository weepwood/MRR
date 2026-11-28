<script setup lang="ts">
import type { ExampleId } from '../catalog'
import type { MrrTableAction } from '@/components/MrrTableActions/types'
import { computed, ref, useId } from 'vue'
import { useTableActionLayout } from '@/composables/useTableActionLayout'
import { pageRowOffset, tableSequenceColumn } from '@/utils/table-sequence'

defineProps<{ example: ExampleId }>()

const id = useId()
const keyword = ref('')
const query = ref('')
const page = ref(1)
const cursorMode = ref(false)
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

const { maxInlineActions, actionColumnWidth } = useTableActionLayout(actions.length, 1)

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
    <MrrMetricCard label="演示项目" :value="24" suffix="项" icon="i-lucide:layers" />
    <MrrMetricCard icon="i-lucide:circle-check" label="演示通过率" :value="92" suffix="%" note="合成指标，示例分母为 100 项" tone="green" value-tone="success" />
    <MrrMetricCard icon="i-lucide:clock" label="演示状态" value="等待复核" compact-value tone="amber" />
  </div>
  <div v-else class="example-stack">
    <div class="example-row">
      <el-switch v-model="cursorMode" aria-label="游标分页示例" @change="page = 1" />
      <span>游标分页示例（仅合成数据，不假设总页数）</span>
    </div>
    <MrrDataTablePanel title="组件验收示例" :count="cursorMode ? undefined : matches.length" :result-key="visibleRows">
      <template #filters>
        <form @submit.prevent="search()">
          <MrrFilterBar embedded :focus-ring="false">
            <div class="mrr-filter-field mrr-filter-field--keyword">
              <el-input :id="`${id}-query`" v-model="keyword" aria-label="筛选示例" placeholder="示例编号或名称" />
            </div>
            <template #actions>
              <MrrFilterActionButton action="search" feedback="request" :auto-insert-space="false" native-type="submit">
                查询示例
              </MrrFilterActionButton>
              <MrrFilterActionButton action="reset" feedback="request" :auto-insert-space="false" native-type="button" @click="search(true)">
                重置筛选
              </MrrFilterActionButton>
            </template>
          </MrrFilterBar>
        </form>
      </template>
      <el-table :data="visibleRows" stripe empty-text="没有匹配的演示项目">
        <el-table-column v-bind="tableSequenceColumn(pageRowOffset(page, 3), visibleRows.length)" />
        <el-table-column prop="id" label="示例编号" min-width="125" />
        <el-table-column prop="name" label="示例名称" min-width="160" />
        <el-table-column label="状态" min-width="110" class-name="mrr-table-status-column">
          <template #default="{ row }">
            <MrrStatusTag :status="row.status" :label="row.label" />
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="actionColumnWidth" fixed="right" align="center">
          <template #default="{ row }">
            <MrrTableActions :actions="actions" :max-inline="maxInlineActions" @select="key => message = `${row.id}：已触发${key === 'view' ? '查看' : '编辑'}示例`" />
          </template>
        </el-table-column>
      </el-table>
      <template #pagination>
        <MrrCursorPagination v-if="cursorMode" :page="page" :max-reachable-page="page + (page * 3 < matches.length ? 1 : 0)" :can-previous="page > 1" :can-next="page * 3 < matches.length" @jump="page = $event">
          <template #summary>
            本批 {{ visibleRows.length }} 项 · 总数未知
          </template>
        </MrrCursorPagination>
        <el-pagination v-else v-model:current-page="page" :page-size="3" :total="matches.length" layout="total, prev, pager, next" small />
      </template>
    </MrrDataTablePanel>
    <p class="example-note" role="status">
      {{ message }}
    </p>
    <p class="example-note">
      业务状态使用状态标签；分类、计数和可关闭项使用普通标签。操作列按业务页面规则响应：宽屏直显，中屏保留首项，窄屏收入更多操作；禁用操作不可执行。
    </p>
    <div class="example-row" aria-label="状态标签示例">
      <MrrStatusTag status="completed" label="已完成" />
      <MrrStatusTag status="processing" label="进行中" />
      <MrrStatusTag status="pending" label="待处理" />
      <MrrStatusTag status="failed" label="失败" />
      <MrrStatusTag label="未知" />
    </div>
    <div class="example-row" aria-label="普通标签示例">
      <el-tag effect="plain">
        分类标签
      </el-tag>
      <el-tag type="info" effect="plain" round>
        胶囊标签
      </el-tag>
    </div>
  </div>
</template>
