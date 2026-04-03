import type { RecursiveRequired, Settings } from '#/global'
import { cloneDeep } from 'es-toolkit'
import settingsDefault from '@/settings.default'
import { merge } from '@/utils/object'

const globalSettings: Settings.all = {
  app: {
    enableDynamicTitle: true,
  },
  menu: {
    mode: 'single',
    subMenuUniqueOpened: false,
  },
  toolbar: {
    enable: false,
    breadcrumb: false,
    navSearch: false,
  },
}

export default merge(globalSettings, cloneDeep(settingsDefault)) as RecursiveRequired<Settings.all>
