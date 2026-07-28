declare interface Window {
  webkitDevicePixelRatio: any
  mozDevicePixelRatio: any
  __MRR_APP_STARTUP_BLOCKED__?: boolean
}

declare const __SYSTEM_INFO__: {
  product: {
    name: string
    version: string
    gitCommit: string
    buildTime: string
    database: {
      minimumCompatibleMigration: string
      maximumCompatibleMigration: string
      backwardCompatibleWithPreviousApplication: boolean
    }
    applicationRollback: {
      allowed: boolean
      reason: string
    }
    configurationSchemaVersion: number
  }
  template: {
    version: string
  }
  dependencies: Record<string, string>
  devDependencies: Record<string, string>
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<object, object, any>
  export default component
}
