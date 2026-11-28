import type { RecursiveRequired, Settings } from '#/global'
import { cloneDeep } from 'es-toolkit'
import settingsDefault from '@/settings.default'
import { merge } from '@/utils/object'

const globalSettings: Settings.all = {
  app: {
    enableDynamicTitle: true,
  },
  // 管理端窄窗口默认复用现有移动侧栏；保存过的显式关闭设置仍然优先。
  layout: {
    enableMobileAdaptation: true,
  },
  menu: {
    mode: 'single',
    subMenuUniqueOpened: false,
  },
  toolbar: {
    enable: true,
    breadcrumb: true,
    navSearch: true,
    fullscreen: true,
    pageReload: true,
  },
}

export default merge(globalSettings, cloneDeep(settingsDefault)) as RecursiveRequired<Settings.all>
