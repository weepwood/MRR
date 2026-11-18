import { afterEach, expect, it } from 'vitest'
import { actionDialog, ActionDialogCancelled, cancelActionDialogs, currentActionDialog, finishActionDialog } from './index'

afterEach(cancelActionDialogs)

it('按顺序完成各自的请求，忽略已经结束的请求', async () => {
  const update = actionDialog.confirm('重新加载后完成更新', '应用更新', {
    closeOnClickModal: false,
    closeOnPressEscape: false,
  })
  const updateCancelled = expect(update).rejects.toBeInstanceOf(ActionDialogCancelled)
  const version = actionDialog.prompt('请输入版本名称', '保存权限版本')
  const updateRequest = currentActionDialog.value!

  expect(updateRequest.title).toBe('应用更新')
  expect(updateRequest.options).toMatchObject({ closeOnClickModal: false, closeOnPressEscape: false })
  finishActionDialog(updateRequest)
  await updateCancelled

  const versionRequest = currentActionDialog.value!
  expect(versionRequest.title).toBe('保存权限版本')
  finishActionDialog(updateRequest, '已关闭的请求')
  expect(currentActionDialog.value).toBe(versionRequest)
  finishActionDialog(versionRequest, '测试版本')
  await expect(version).resolves.toEqual({ value: '测试版本' })
  expect(currentActionDialog.value).toBeUndefined()
})

it('卸载时取消仍在显示和等待中的请求', async () => {
  const confirm = expect(actionDialog.confirm('继续吗？', '确认操作')).rejects.toBeInstanceOf(ActionDialogCancelled)
  const prompt = expect(actionDialog.prompt('输入申请原因', '申请')).rejects.toBeInstanceOf(ActionDialogCancelled)
  cancelActionDialogs()
  await Promise.all([confirm, prompt])
  expect(currentActionDialog.value).toBeUndefined()
})
