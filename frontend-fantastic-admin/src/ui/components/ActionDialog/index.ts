import { computed, shallowReactive } from 'vue'

interface DialogOptions {
  type?: 'info' | 'warning'
  confirmButtonText?: string
  cancelButtonText?: string
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
}
