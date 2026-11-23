import type { ComputedRef, InjectionKey } from 'vue'

/** Parent refresh state for row actions that may render their menus outside the table DOM. */
export const mrrDataTablePanelLoadingKey: InjectionKey<ComputedRef<boolean>> = Symbol('MrrDataTablePanelLoading')
