import { ElButton, ElInput, ElOption, ElSelect } from 'element-plus'
import { createPinia } from 'pinia'
import { createApp, h, ref } from 'vue'
import { useSettingsStore } from '../../src/store/modules/settings'
import FaButton from '../../src/ui/components/FaButton/index.vue'
import FaDrawer from '../../src/ui/components/FaDrawer/index.vue'
import '@unocss/reset/tailwind-compat.css'
import 'virtual:uno.css'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '../../src/assets/styles/globals.css'
import '../../src/assets/styles/motion.css'

// 独立的合成控件夹具，复用真实组件，不加入生产路由或认证流程。
useSettingsStore(createPinia())
createApp({
  setup() {
    const value = ref('')
    const choice = ref('全部')
    const count = ref(0)
    const drawer = ref(false)
    const closed = ref(0)
    return () => h('main', { style: 'max-width:760px;margin:48px auto;padding:24px;color:var(--mrr-foreground);background:var(--mrr-card);border:1px solid var(--mrr-border);border-radius:var(--mrr-radius-card)' }, [
      h('h1', { style: 'font-size:24px;margin:0 0 8px' }, '控件交互验收'),
      h('p', { style: 'color:var(--mrr-muted-foreground);margin:0 0 24px' }, '合成示例 · 使用 Tab 检查焦点，按下按钮检查反馈'),
      h('label', { for: 'review-query' }, '查询条件'),
      h(ElInput, { 'id': 'review-query', 'modelValue': value.value, 'onUpdate:modelValue': (v: string) => value.value = v, 'placeholder': '请输入示例条件', 'style': 'display:block;margin:8px 0 16px' }),
      h(ElSelect, { 'modelValue': choice.value, 'onUpdate:modelValue': (v: string) => choice.value = v, 'aria-label': '示例范围', 'style': 'width:180px;margin-bottom:24px' }, () => ['全部', '待处理'].map(label => h(ElOption, { label, value: label }))),
      h('div', { style: 'display:flex;gap:12px;flex-wrap:wrap' }, [
        h(FaButton, { onClick: () => count.value++ }, () => '确认查询'),
        h(FaButton, { variant: 'outline' }, () => '重置条件'),
        h(FaButton, { disabled: true }, () => '不可操作'),
        h(FaButton, { loading: true }, () => '处理中'),
        h(ElButton, { type: 'primary' }, () => 'Element 操作'),
        h(FaButton, { onClick: () => drawer.value = true }, () => '打开抽屉'),
      ]),
      h('p', { role: 'status', style: 'margin-top:24px' }, `已操作 ${count.value} 次`),
      h('p', { 'data-testid': 'drawer-closed' }, `关闭完成 ${closed.value} 次`),
      h(FaDrawer, { 'modelValue': drawer.value, 'onUpdate:modelValue': (v: boolean) => drawer.value = v, 'title': '合成设置抽屉', 'destroyOnClose': false, 'onClosed': () => closed.value++ }, { default: () => '合成内容' }),
    ])
  },
}).mount('#app')
