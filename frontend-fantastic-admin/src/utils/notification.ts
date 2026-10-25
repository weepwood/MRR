import { ElMessage } from 'element-plus'

type NotificationType = 'success' | 'warning' | 'info' | 'error'

export interface NotificationOptions {
  description?: string
  duration?: number
  showClose?: boolean
  grouping?: boolean
  offset?: number
}

function showNotification(type: NotificationType, message: string, options: NotificationOptions = {}) {
  const description = options.description?.trim()
  const displayMessage = description ? `${message}：${description}` : message

  return ElMessage({
    message: displayMessage,
    type,
    duration: options.duration ?? 5000,
    grouping: options.grouping ?? true,
    showClose: options.showClose ?? false,
    offset: options.offset,
  })
}

/** Single entry point for short-lived app notifications, rendered with Element Plus ElMessage. */
export const notify = {
  success: (message: string, options?: NotificationOptions) => showNotification('success', message, options),
  warning: (message: string, options?: NotificationOptions) => showNotification('warning', message, options),
  info: (message: string, options?: NotificationOptions) => showNotification('info', message, options),
  error: (message: string, options?: NotificationOptions) => showNotification('error', message, options),
}
