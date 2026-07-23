export type MrrTableActionTone = 'default' | 'primary' | 'success' | 'warning' | 'danger'

export interface MrrTableAction {
  key: string
  label: string
  icon: string
  tone?: MrrTableActionTone
  permission?: string | string[]
  visible?: boolean
  disabled?: boolean
  disabledReason?: string
  loading?: boolean
  placement?: 'auto' | 'inline' | 'overflow'
}
