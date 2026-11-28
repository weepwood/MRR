<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import type { ExampleId } from '../catalog'
import { computed, reactive, ref } from 'vue'
import { MrrDualAxisChart, MrrHorizontalBarChart, MrrLineChart } from '@/components/charts'
import { mrrChartColorToken } from '@/config/chart-theme'

defineProps<{ example: ExampleId }>()
const selected = ref<string[]>([])
const open = ref(false)
const editTrigger = ref<{ $el: HTMLButtonElement }>()
const formRef = ref<FormInstance>()
const form = reactive({ name: '', source: 'local', note: '' })
const notice = ref('尚未执行示例操作')
const sampleRows = ['演示记录 A', '演示记录 B', '演示记录 C']
const formRules = { name: [{ required: true, message: '请输入演示名称', trigger: 'blur' }] }

async function saveDemo() {
  if (!await formRef.value?.validate().catch(() => false)) { return }
  notice.value = `已保存 ${form.name}（仅示例）`
  open.value = false
}

const empty = ref(false)
const chartLoading = ref(false)
const dualAxis = ref(false)
const points = ref(16)
const ranking = computed(() => empty.value ? [] : [{ label: '接口 A', count: 240 }, { label: '接口 B', count: 135 }, { label: '接口 C', count: 78 }])
const dates = Array.from({ length: 24 }, (_, i) => `2026-11-${String(i + 1).padStart(2, '0')}`)
const series = computed(() => empty.value
  ? []
  : [
      { name: '客户端耗时', data: dates.map((_, i) => 80 + (i * 13 % 46)), color: mrrChartColorToken('primary'), smooth: false },
      { name: '服务端耗时', data: dates.map((_, i) => 45 + (i * 7 % 21)), color: mrrChartColorToken('neutral'), smooth: false, dashed: true },
    ])
</script>

<template>
  <div v-if="example === 'business-patterns'" class="example-stack">
    <div class="example-row">
      <el-button ref="editTrigger" type="primary" @click="open = true">
        编辑业务示例
      </el-button>
      <el-button :disabled="!selected.length" @click="notice = `已处理 ${selected.length} 项（仅示例）`; selected = []">
        批量处理（{{ selected.length }}）
      </el-button>
    </div>
    <el-checkbox-group v-model="selected" aria-label="批量选择示例">
      <el-checkbox v-for="row in sampleRows" :key="row" :value="row">
        {{ row }}
      </el-checkbox>
    </el-checkbox-group>
    <p class="example-note" role="status">
      {{ notice }}
    </p>
    <MrrDialog v-model="open" title="编辑业务示例" description="采用实际业务编辑表单，验证失败不关闭。" @closed="editTrigger?.$el.focus()">
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="mrr-edit-form mrr-edit-form--paired">
        <el-form-item label="演示名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source">
            <el-option label="本地" value="local" /><el-option label="归档" value="archive" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" class="mrr-edit-form__full">
          <el-input v-model="form.note" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">
          取消
        </el-button>
        <el-button type="primary" @click="saveDemo">
          保存示例
        </el-button>
      </template>
    </MrrDialog>
  </div>
  <div v-else class="example-stack">
    <div class="example-row">
      <el-switch v-model="empty" aria-label="模拟无图表数据" />
      <span>模拟空结果</span>
      <el-switch v-model="chartLoading" aria-label="模拟图表加载" />
      <span>模拟加载</span>
      <el-switch v-model="dualAxis" aria-label="组合图模式" />
      <span>双轴组合</span>
      <el-select v-model="points" aria-label="示例趋势范围" class="demo-trend-range mrr-single-select" style="width: 180px;">
        <el-option label="最近 16 个记录日" :value="16" />
        <el-option label="全部记录日" :value="24" />
      </el-select>
    </div>
    <MrrDualAxisChart v-if="dualAxis" :loading="chartLoading" :categories="empty ? [] : dates" :bars="empty ? [] : [{ name: '扫描页数', data: dates.map((_, i) => 200 + i * 13), color: mrrChartColorToken('primary') }]" :lines="empty ? [] : [{ name: '扫描记录', data: dates.map((_, i) => 20 + i * 2), color: mrrChartColorToken('neutral'), smooth: false }]" left-axis-name="页数" right-axis-name="记录数" left-unit="页" right-unit="条" :initial-visible-count="points" :height="260" />
    <MrrLineChart v-else :loading="chartLoading" :categories="empty ? [] : dates" :series="series" :visible-points="points" unit="ms" :height="260" :min-interval="0" />
    <MrrHorizontalBarChart :loading="chartLoading" :data="ranking" :height="180" unit="ms" />
  </div>
</template>
