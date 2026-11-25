import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'
import { demoCsvResponse, demoImageBlob, demoWorkbookBlob } from '@/api/demo-file'
import { demoArchiveBoxStore } from './archive-box-store'
import { demoCsv, demoPatientWorkbook, demoTemplate } from './data-exchange'
import { runtimeSchema, validateRuntimeSetting } from './runtime-settings'

function data<T extends { code: number }>(result: T) {
  expect(result.code).toBe(200)
  if (!('data' in result)) { throw new Error('Missing data') }
  return result.data as Extract<T, { data: unknown }>['data']
}

describe('演示数据交换与运行参数', () => {
  it('模板严格限定，导出字段与共享样本及过滤条件一致', () => {
    expect(data(demoTemplate('patients')).text).toBe('\uFEFFbah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\r\n')
    expect(demoTemplate('archives').code).toBe(404)
    expect(demoCsv('__proto__').code).toBe(404)
    expect(demoCsv('scan', { afterId: 63 }).code).toBe(200)
    expect(data(demoCsv('scan', { afterId: 63 })).text).toContain('26070031')
    expect(data(demoCsv('patients', { keyword: '26070000' })).text.split('\r\n')).toHaveLength(3)
    expect(data(demoCsv('patients', { keyword: '26070000' })).text).not.toContain('00000020000101')
    expect(demoCsv('scan', { afterId: 'not-a-number' }).code).toBe(400)
    expect(demoCsv('patients', { arbitrary: 'field' }).code).toBe(400)
    expect(data(demoCsv('statistics')).text.split('\r\n')).toHaveLength(66)
    expect(data(demoCsv('archives')).text.split('\r\n')).toHaveLength(34)
  })
  it('装箱数据导出使用当前共享记录并保护公式单元格', () => {
    const row = demoArchiveBoxStore.save({ bah: '88800001', boxNo: 'DEMO-TEST', status: 'NORMAL', remark: '=1+1' })
    try {
      const csv = data(demoCsv('archive-boxes', { boxNo: 'DEMO-TEST' })).text
      expect(csv).toContain('DEMO-TEST,,NORMAL,\'=1+1')
    }
    finally { demoArchiveBoxStore.remove(row.id!) }
  })
  it('工作簿包含真实OOXML关系及内联字符串，而不是伪装CSV', async () => {
    const payload = data(await demoPatientWorkbook({ keyword: '26070000' }))
    const zip = await JSZip.loadAsync(payload.base64, { base64: true })
    expect(Object.keys(zip.files)).toContain('xl/worksheets/sheet1.xml')
    const xml = await zip.file('xl/worksheets/sheet1.xml')?.async('string')
    expect(xml).toContain('r="A2" t="inlineStr"')
    expect(xml).toContain('26070000')
    expect(xml).not.toContain('26070001')
    expect(xml).not.toContain('00000020000101')
    expect(xml).not.toContain('<f>')
    expect(demoWorkbookBlob(payload).type).toContain('spreadsheetml.sheet')
    expect(() => demoWorkbookBlob({ name: 'mrr-demo-patients.xlsx', base64: btoa('not a zip') })).toThrow()
    expect(demoCsvResponse(data(demoTemplate('scan'))).data.type).toContain('text/csv')
    expect(() => demoCsvResponse({ name: 'mrr-demo-scan.csv', text: 'not csv envelope' })).toThrow()
    expect(() => demoImageBlob({ svg: '<html></html>' })).toThrow()
  })
  it('模拟运行设置区分覆盖值、默认值，批量验证可拒绝不安全范围', () => {
    const overrides = { loginMaxAttempts: '7' }
    expect(runtimeSchema(overrides).items).toHaveLength(12)
    expect(runtimeSchema(overrides).items.find(i => i.key === 'loginMaxAttempts')).toMatchObject({ defaultValue: '5', overrideValue: '7', effectiveValue: '7' })
    expect(runtimeSchema({}).items.find(i => i.key === 'loginMaxAttempts')).toMatchObject({ overrideValue: null, effectiveValue: '5' })
    expect(validateRuntimeSetting('loginMaxAttempts', '2')).toBe(false)
    expect(validateRuntimeSetting('sessionExpireMinutes', '1441')).toBe(false)
    expect(validateRuntimeSetting('accessLogRetentionEnabled', 'yes')).toBe(false)
    expect(validateRuntimeSetting('__proto__', '{}')).toBe(false)
    expect(validateRuntimeSetting('loginMaxAttempts', '10')).toBe(true)
    expect(validateRuntimeSetting('imageDirectoryRoutingRules', '[{"type":"exact","value":"26.02.30","node":"ba01"}]')).toBe(false)
    expect(validateRuntimeSetting('imageDirectoryRoutingRules', '[]')).toBe(true)
    expect(runtimeSchema({ imageDirectoryRoutingRules: '[]' }).items.find(i => i.key === 'imageDirectoryRoutingRules')?.effectiveValue).toBe('[]')
  })
})
