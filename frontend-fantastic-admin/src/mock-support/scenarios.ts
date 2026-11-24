export const SCENARIO_KEY = 'mrr:pages:scenario:v1'
export const DEMO_SCENARIOS = [
  { value: 'standard', label: '默认综合场景', description: '近期与历史记录、迁移及异常样本。' },
  { value: 'migration-backlog', label: 'OSS 迁移积压', description: '已迁移样本恢复为待迁移，保留失败与重试。' },
  { value: 'ocr-review', label: 'OCR 待识别', description: '增加未分类的有效影像，方便演练识别。' },
] as const

export type DemoScenario = typeof DEMO_SCENARIOS[number]['value']
type ScenarioStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function valid(value: unknown): value is DemoScenario {
  return DEMO_SCENARIOS.some(item => item.value === value)
}

export function readScenario(storage?: ScenarioStorage): DemoScenario {
  try {
    const value = storage?.getItem(SCENARIO_KEY)
    return valid(value) ? value : 'standard'
  }
  catch {
    return 'standard'
  }
}

export function writeScenario(storage: ScenarioStorage | undefined, value: unknown) {
  if (!storage || !valid(value)) { return false }
  try {
    if (value === 'standard') { storage.removeItem(SCENARIO_KEY) }
    else { storage.setItem(SCENARIO_KEY, value) }
    return true
  }
  catch {
    return false
  }
}

export function pagesScenarioStorage() {
  if (import.meta.env.MODE !== 'pages' || String(import.meta.env.VITE_BUILD_MOCK) !== 'true') { return undefined }
  try {
    return typeof window === 'undefined' ? undefined : window.localStorage
  }
  catch {
    return undefined
  }
}
