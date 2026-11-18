import type { Ref } from 'vue'
import type { GalleryImage } from '../types'
import { ElMessage } from 'element-plus'
import { watch } from 'vue'
import useAuth from '@/utils/composables/useAuth'
import { archiveAccessMode } from '../access-mode'
import { useArchiveExportJob } from './useArchiveExportJob'

/** 选中ZIP与整案ZIP分别持有任务，执行及下载仍使用同一任务机制。 */
export function useArchiveSelectedZip(selection: Readonly<Ref<GalleryImage[]>>) {
  const task = useArchiveExportJob('ZIP')
  const { auth } = useAuth()
  watch(() => JSON.stringify(selection.value.map(image => image.id)), () => {
    // discard也推进创建中的代数，迟到响应不能重新出现为当前选择的任务。
    void task.discard()
  }, { flush: 'sync' })

  async function start() {
    if (archiveAccessMode.value !== 'internal' || !auth('record:download')) {
      ElMessage.warning('当前会话没有选中影像 ZIP 导出权限')
      return
    }
    const images = selection.value.slice()
    if (!images.length) {
      ElMessage.warning('请先选择要导出的影像')
      return
    }
    const ids = images.map(image => image.id)
    if (ids.some(id => typeof id !== 'number' || !Number.isSafeInteger(id) || id <= 0)
      || new Set(ids).size !== ids.length) {
      ElMessage.warning('部分影像 ID 缺失、无效或重复，请刷新选择后重试')
      return
    }
    const bah = String(images[0].bah || '').trim()
    const sjh = String(images[0].sjh || '').trim()
    if (images.some(image => String(image.bah || '').trim() !== bah || String(image.sjh || '').trim() !== sjh)) {
      ElMessage.warning('选中的影像不属于同一个档案袋')
      return
    }
    try {
      await task.start({ format: 'ZIP', bah: bah || undefined, sjh: sjh || undefined, ids: ids as number[] })
    }
    catch (error: unknown) {
      ElMessage.error((error as { message?: string })?.message || '选中 ZIP 导出失败')
    }
  }
  return { ...task, start }
}
