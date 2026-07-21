import { describe, expect, it } from 'vitest'
import {
  addArchiveDepartmentThemeHistoryToLocal,
  loadArchiveDepartmentThemeHistoryFromLocal,
  normalizeArchiveDepartmentThemes,
  resolveArchiveDepartmentTheme,
} from './archive-department-theme'

describe('archive department theme', () => {
  it('keeps automatic colors stable for the same department', () => {
    const first = resolveArchiveDepartmentTheme('心内科', [])
    const second = resolveArchiveDepartmentTheme('心内科', [])

    expect(second).toEqual(first)
  })

  it('uses configured folder and strip colors', () => {
    const themes = normalizeArchiveDepartmentThemes([{
      department: '呼吸科',
      folderColor: '#123456',
      stripColor: '#654321',
    }])

    expect(resolveArchiveDepartmentTheme('呼吸科', themes)).toEqual({
      department: '呼吸科',
      folderColor: '#123456',
      stripColor: '#654321',
    })
  })

  it('removes duplicate departments and repairs invalid colors', () => {
    const themes = normalizeArchiveDepartmentThemes([
      { department: '外科', folderColor: 'invalid', stripColor: '#112233' },
      { department: '外科', folderColor: '#445566', stripColor: '#778899' },
    ])

    expect(themes).toHaveLength(1)
    expect(themes[0].department).toBe('外科')
    expect(themes[0].folderColor).toMatch(/^#[\dA-F]{6}$/)
    expect(themes[0].stripColor).toBe('#112233')
  })

  it('keeps the most recent distinct color scheme snapshots in local history', () => {
    localStorage.clear()
    const first = [{ department: '内科', folderColor: '#123456', stripColor: '#654321' }]
    const second = [{ department: '外科', folderColor: '#112233', stripColor: '#445566' }]

    addArchiveDepartmentThemeHistoryToLocal(first)
    addArchiveDepartmentThemeHistoryToLocal(second)
    addArchiveDepartmentThemeHistoryToLocal(first)

    const history = loadArchiveDepartmentThemeHistoryFromLocal()
    expect(history).toHaveLength(2)
    expect(history[0].themes).toEqual(first)
    expect(history[1].themes).toEqual(second)
  })
})
