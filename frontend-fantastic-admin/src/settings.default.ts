import type { RecursiveRequired, Settings } from '#/global'

const globalSettingsDefault: RecursiveRequired<Settings.all> = {
  app: {
    colorScheme: 'light',
    radius: 0.5,
    enableMournMode: false,
    enableColorAmblyopiaMode: false,
    enablePermission: true,
    enableProgress: true,
    enableDynamicTitle: false,
    routeBaseOn: 'frontend',
  },
  home: {
    enable: true,
    title: '管理概览',
    fullPath: '/',
  },
  layout: {
    enableMobileAdaptation: false,
  },
  menu: {
    baseOn: 'frontend',
    mode: 'side',
    mainMenuClickMode: 'switch',
    subMenuUniqueOpened: true,
    subMenuCollapse: false,
    enableSubMenuCollapseButton: false,
    enableHotkeys: false,
  },
  topbar: {
    mode: 'static',
  },
  tabbar: {
    enable: false,
    enableIcon: false,
    enableHotkeys: false,
  },
  toolbar: {
    enable: true,
    breadcrumb: true,
    navSearch: true,
    fullscreen: false,
    pageReload: false,
    colorScheme: false,
  },
  mainPage: {
    enableHotkeys: true,
  },
  navSearch: {
    enableHotkeys: true,
  },
  copyright: {
    enable: false,
    dates: '',
    company: '',
    website: '',
    beian: '',
  },
}

export default globalSettingsDefault
