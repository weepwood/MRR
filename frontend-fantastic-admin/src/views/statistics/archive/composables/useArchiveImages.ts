import type { GalleryImage, PatientInfo } from '../types'
import type { ApiResult, BAHImageData } from '@/api/types'
import { ElMessage } from 'element-plus'
import { ref, shallowRef } from 'vue'
import { downloadBah, getImgByCode, updateImageType } from '@/api/modules/image'
import { getPatientByBah } from '@/api/modules/search'
import { getArchiveLookupValidationMessage, normalizeMedicalRecordCode } from '@/utils/medical-record-code'
import { padCode, resolveImageUrl } from '../constants'

function asResult<T>(promise: Promise<unknown>): Promise<ApiResult<T>> {
  return promise as unknown as Promise<ApiResult<T>>
}

export function useArchiveImages() {
  const images = shallowRef<GalleryImage[]>([])
  const patientList = shallowRef<PatientInfo[]>([])
  const loading = ref(false)
  const patientLoading = ref(false)
  const downloading = ref(false)
  const savingType = ref(false)
  const errorMsg = ref('')
  const searchBah = ref('')
  const searchSjh = ref('')

  async function loadPatient(bah: string): Promise<void> {
    if (!bah) {
      patientList.value = []
      return
    }
    patientLoading.value = true
    try {
      const res = await asResult<PatientInfo[]>(getPatientByBah(bah))
      patientList.value = Array.isArray(res?.data) ? res.data : []
    }
    catch {
      patientList.value = []
    }
    finally {
      patientLoading.value = false
    }
  }

  async function loadImages(): Promise<void> {
    const bah = padCode(searchBah.value)
    const sjh = padCode(searchSjh.value)
    const validationMessage = getArchiveLookupValidationMessage(bah, sjh)

    searchBah.value = bah
    searchSjh.value = sjh

    if (validationMessage) {
      images.value = []
      patientList.value = []
      errorMsg.value = validationMessage
      ElMessage.warning(validationMessage)
      return
    }

    loading.value = true
    errorMsg.value = ''
    try {
      const res = await asResult<BAHImageData[]>(getImgByCode(bah || undefined, sjh || undefined))
      const rawList = Array.isArray(res?.data) ? res.data : []
      images.value = rawList.map((item: BAHImageData) => ({
        ...item,
        bah: normalizeMedicalRecordCode(item.bah),
        sjh: normalizeMedicalRecordCode(item.sjh),
        imageUrl: resolveImageUrl(item),
      }))
      const patientBah = normalizeMedicalRecordCode(rawList[0]?.bah || bah)
      await loadPatient(patientBah)
    }
    catch (err: unknown) {
      errorMsg.value = (err as { message?: string })?.message || '影像加载失败'
      images.value = []
      patientList.value = []
    }
    finally {
      loading.value = false
    }
  }

  async function handleDownload(): Promise<void> {
    const bah = padCode(searchBah.value)
    if (!bah) {
      ElMessage.warning('请输入病案号')
      return
    }
    searchBah.value = bah
    downloading.value = true
    try {
      const result = await downloadBah(bah) as unknown as Blob | { data?: Blob }
      const blob = result instanceof Blob ? result : result?.data
      if (!(blob instanceof Blob)) {
        throw new TypeError('下载响应不是文件')
      }
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${bah}.zip`
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('档案袋下载已开始')
    }
    catch (err: unknown) {
      ElMessage.error((err as { message?: string })?.message || '下载失败')
    }
    finally {
      downloading.value = false
    }
  }

  async function saveImageType(img: GalleryImage, nextType: number): Promise<void> {
    if (!img?.id) {
      ElMessage.warning('当前影像缺少记录 ID，无法修改类型')
      return
    }
    const previousType = img.btype
    const index = images.value.findIndex(item => item.id === img.id)
    if (index < 0) {
      return
    }
    const next = images.value.slice()
    next[index] = { ...next[index], btype: nextType }
    images.value = next

    savingType.value = true
    try {
      await asResult<unknown>(updateImageType(img.id, { btype: nextType }))
      ElMessage.success('影像类型已更新')
    }
    catch (err: unknown) {
      const rollback = images.value.slice()
      rollback[index] = { ...rollback[index], btype: previousType }
      images.value = rollback
      ElMessage.error((err as { message?: string })?.message || '类型更新失败')
    }
    finally {
      savingType.value = false
    }
  }

  return {
    images,
    patientList,
    loading,
    patientLoading,
    downloading,
    savingType,
    errorMsg,
    searchBah,
    searchSjh,
    loadImages,
    handleDownload,
    saveImageType,
  }
}
