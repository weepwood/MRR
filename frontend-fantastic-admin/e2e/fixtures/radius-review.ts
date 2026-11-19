import { ElButton, ElButtonGroup, ElCheckbox, ElDatePicker, ElInput, ElInputNumber, ElOption, ElPagination, ElRadioButton, ElRadioGroup, ElSelect, ElTabPane, ElTabs, ElTag } from 'element-plus'
import { createApp, h, ref } from 'vue'
import MrrStatusTag from '../../src/components/MrrStatusTag/index.vue'
import '../../src/assets/styles/globals.css'
// 模拟 Element Plus 按需样式晚于项目样式注入的实际顺序。
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

createApp({
  setup() {
    const count = ref(1)
    const choice = ref('a')
    const shown = ref(true)
    return () => h('main', { style: 'display:grid;gap:12px;padding:24px' }, [
      ...(['small', 'default', 'large'] as const).map(size => h('div', { class: `case-${size}` }, [
        h(ElInput, { size, placeholder: '普通输入' }),
        h(ElSelect, { size, 'modelValue': 'a', 'aria-label': '选择' }, () => h(ElOption, { label: '示例', value: 'a' })),
        h(ElInputNumber, { size, 'modelValue': count.value, 'onUpdate:modelValue': (v: number) => count.value = v }),
        h(ElInputNumber, { size, 'modelValue': count.value, 'controlsPosition': 'right', 'onUpdate:modelValue': (v: number) => count.value = v }),
        h(ElDatePicker, { size, type: 'daterange', modelValue: null }),
        h(ElRadioGroup, { size, 'modelValue': choice.value, 'onUpdate:modelValue': (v: string) => choice.value = v }, () => [
          h(ElRadioButton, { value: 'a' }, () => '选项 A'),
          h(ElRadioButton, { value: 'b' }, () => '选项 B'),
          h(ElRadioButton, { value: 'c' }, () => '选项 C'),
        ]),
      ])),
      h('div', { class: 'case-group' }, [
        h(ElInput, { placeholder: '组合输入' }, { prepend: () => '前缀', append: () => '后缀' }),
        h(ElRadioGroup, { modelValue: 'a' }, () => h(ElRadioButton, { value: 'a' }, () => '单选项')),
      ]),
      h('div', { class: 'case-other' }, [
        h(ElPagination, { total: 100, pageSize: 10, layout: 'prev, pager, next' }),
        h(ElButtonGroup, {}, () => [h(ElButton, {}, () => '前'), h(ElButton, {}, () => '中'), h(ElButton, {}, () => '后')]),
        ...(['top', 'left', 'right', 'bottom'] as const).map(position => h(ElTabs, { type: 'card', tabPosition: position, class: `tabs-${position}`, modelValue: 'a' }, () => [
          h(ElTabPane, { name: 'a', label: '页签 A' }, () => '内容'),
          h(ElTabPane, { name: 'b', label: '页签 B' }, () => '内容'),
        ])),
      ]),
      h('div', { class: 'case-tags' }, [
        h(MrrStatusTag, { status: 'completed', label: '已完成' }),
        shown.value ? h(ElTag, { type: 'success', closable: true, onClose: () => shown.value = false }, () => '可关闭标签') : null,
        h(ElTag, { round: true }, () => '胶囊标签'),
        h(ElCheckbox, { modelValue: false }, () => '复选框'),
        h(ElButton, { 'circle': true, 'aria-label': '圆形按钮' }, () => '+'),
      ]),
    ])
  },
}).mount('#app')
