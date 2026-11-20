import { computed, shallowReactive } from 'vue'

/**
 * 确认/输入类弹窗的层级。
 *
 * Element Plus 的浮层基数是 2000，每打开一个浮层自增；页面里还叠着更靠上的「沉浸层」：
 * 全屏影像查看器 3000、影像预览悬浮工具栏 3001、OCR 抽屉基座 3100。
 * 弹窗若用默认基数，会整块压在影像下面——看得见遮罩、看不见弹窗，按钮点不到。
 * 这里显式抬到页面浮层之上，但仍低于档案水印（4000，`pointer-events: none`）。
 */
export const ACTION_DIALOG_Z_INDEX = 3200

/** 提交前需要复核的单项变更，`before` 缺省时只显示结果值。 */
export interface DialogDetail {
  label: string
  before?: string
  after: string
}

interface DialogOptions {
  type?: 'info' | 'warning'
  confirmButtonText?: string
  cancelButtonText?: string
  /** 可选变更清单，用于「确认保存」这类需要先看清影响的场景。 */
  details?: DialogDetail[]
  detailsCaption?: string
  /**
   * 可选的次要操作按钮（如「放弃修改」）。
   * 仅 `actionDialog.choose` 会读取，点击后以 extra 结果结束，不触发输入校验。
   */
  extraButtonText?: string
  closeOnClickModal?: boolean
  closeOnPressEscape?: boolean
}

interface DialogInput {
  inputType?: 'text' | 'textarea'
  inputPlaceholder?: string
  inputValidator?: (value: string) => true | string
}

interface ActionDialogRequest {
  message: string
  title: string
  options: DialogOptions
  input?: DialogInput
  finish: (value?: string) => void
}

export class ActionDialogCancelled extends Error {}

/** 「次要操作」按钮的结果标记，供 `actionDialog.choose` 区分确认、放弃与取消。 */
export const ACTION_DIALOG_EXTRA = 'extra'

export type ActionDialogChoice = 'confirm' | 'cancel' | 'extra'

const queue = shallowReactive<ActionDialogRequest[]>([])
export const currentActionDialog = computed(() => queue[0])

function enqueue(message: string, title: string, options: DialogOptions, input?: DialogInput) {
  return new Promise<string>((resolve, reject) => {
    queue.push({
      message,
      title,
      options,
      input,
      finish(value) {
        if (value === undefined) {
          reject(new ActionDialogCancelled('弹窗操作已取消'))
        }
        else {
          resolve(value)
        }
      },
    })
  })
}

export function finishActionDialog(request: ActionDialogRequest, value?: string) {
  if (currentActionDialog.value === request) {
    queue.shift()
    request.finish(value)
  }
}

export function cancelActionDialogs() {
  for (const request of queue.splice(0)) {
    request.finish()
  }
}

export const actionDialog = {
  async confirm(message: string, title: string, options: DialogOptions = {}) {
    await enqueue(message, title, options)
  },
  async prompt(message: string, title: string, options: DialogOptions & DialogInput = {}) {
    const value = await enqueue(message, title, options, options)
    return { value }
  },
  /**
   * 三选一确认：确认、次要操作（extra）或取消。
   * 关闭按钮、Esc、遮罩点击均视为「取消」，便于把「不改变现状」留给最容易触发的出口。
   */
  async choose(
    message: string,
    title: string,
    options: DialogOptions & { extraButtonText: string },
  ): Promise<ActionDialogChoice> {
    try {
      const value = await enqueue(message, title, options)
      return value === ACTION_DIALOG_EXTRA ? 'extra' : 'confirm'
    }
    catch (error) {
      if (error instanceof ActionDialogCancelled) { return 'cancel' }
      throw error
    }
  },
}
