import type { GalleryImage, PatientInfo } from '../types'
import type { IdCardArchiveCase, IdCardArchiveSearchResponse } from '@/api/modules/search'
import type { ApiResult, BAHImageData } from '@/api/types'
import { ElMessage } from 'element-plus'
import { ref, shallowRef } from 'vue'
import {
  downloadArchiveZip,
  getArchiveZipExportPlan,
} from '@/api/modules/archive-export'
import { getImgByCode, updateImageType } from '@/api/modules/image'
import {
  getArchiveCasesByIdCard,
  getArchiveCasesByToken,
  getPatientByBah,
} from '@/api/modules/search'
import useAuth from '@/utils/composables/useAuth'
import {
  normalizeMedicalRecordCode,
  requiresSjhForBah,
  resolveArchiveLookup,
} from '@/utils/medical-record-code'
import { padCode, readArchiveImageVersion, resolveImageUrl, writeArchiveImageVersion } from '../constants'
import { useArchiveExportJob } from './useArchiveExportJob'
import { addArchiveSearchHistory } from './useArchiveSearchHistory'

function asResult<T>(promise: Promise<unknown>): Promise<ApiResult<T>> {
  return promise as unknown as Promise<ApiResult<T>>
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function useArchiveImages() {
  const { auth } = useAuth()
  const exportJob = useArchiveExportJob()
  const images = shallowRef<GalleryImage[]>([])
  const patientList = shallowRef<PatientInfo[]>([])
  const archiveCases = shallowRef<IdCardArchiveCase[]>([])
  const loading = ref(false)
  const patientLoading = ref(false)
  const idCardLoading = ref(false)
  const downloading = ref(false)
  const savingType = ref(false)
  const errorMsg = ref('')
  const searchBah = ref('')
  const searchSjh = ref('')
  const searchUserId = ref('')
  const searchIdCard = ref('')
  const idCardToken = ref('')
  const maskedIdCard = ref('')

  async function loadPatient(bah: string): Promise<void> {
    if (!bah || requiresSjhForBah(bah)) {
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

  async function loadImages(forceRefresh = false): Promise<void> {
    const lookup = resolveArchiveLookup(searchBah.value, searchSjh.value)
    const { bah, sjh } = lookup
    const userid = searchUserId.value.trim()

    searchBah.value = bah
    searchSjh.value = sjh

    if (lookup.validationMessage) {
      images.value = []
      patientList.value = []
      errorMsg.value = lookup.validationMessage
      ElMessage.warning(lookup.validationMessage)
      return
    }

    const requestBah = lookup.requestBah || ''
    const requestSjh = lookup.requestSjh || ''

    loading.value = true
    errorMsg.value = ''
    try {
      const res = await asResult<BAHImageData[]>(getImgByCode(
        requestBah || undefined,
        requestSjh || undefined,
        forceRefresh,
        userid || undefined,
      ))
      const rawList = Array.isArray(res?.data) ? res.data : []
      let cacheBuster = readArchiveImageVersion(requestBah, requestSjh)
      if (forceRefresh) {
        cacheBuster = Date.now()
        writeArchiveImageVersion(requestBah, requestSjh, cacheBuster)
      }
      images.value = rawList.map((item: BAHImageData) => ({
        ...item,
        bah: normalizeMedicalRecordCode(item.bah),
        sjh: normalizeMedicalRecordCode(item.sjh),
        imageUrl: resolveImageUrl(item, cacheBuster),
      }))
      const patientBah = normalizeMedicalRecordCode(rawList[0]?.bah || bah)
      await loadPatient(patientBah)

      const firstImage = images.value[0]
      if (firstImage) {
        addArchiveSearchHistory({
          bah: firstImage.bah || bah,
          sjh: firstImage.sjh || sjh,
          imageCount: images.value.length,
        })
      }
      else {
        addArchiveSearchHistory({
          bah,
          sjh,
          status: 'failure',
          failureReason: '未查询到影像',
        })
      }
    }
    catch (err: unknown) {
      errorMsg.value = (err as { message?: string })?.message || '影像加载失败'
      images.value = []
      patientList.value = []
      addArchiveSearchHistory({
        bah,
        sjh,
        status: 'failure',
        failureReason: errorMsg.value,
      })
    }
    finally {
      loading.value = false
    }
  }

  function applyArchiveCaseResponse(data: IdCardArchiveSearchResponse | undefined) {
    idCardToken.value = data?.token || ''
    maskedIdCard.value = data?.maskedIdCard || ''
    archiveCases.value = Array.isArray(data?.cases) ? data.cases : []
  }

  async function loadArchiveCasesByIdCard(idCard: string): Promise<IdCardArchiveSearchResponse | null> {
    idCardLoading.value = true
    errorMsg.value = ''
    try {
      const res = await asResult<IdCardArchiveSearchResponse>(getArchiveCasesByIdCard(idCard))
      const data = res?.data
      applyArchiveCaseResponse(data)
      if (!archiveCases.value.length) {
        errorMsg.value = '未查询到该患者的影像病案'
      }
      return data || null
    }
    catch (err: unknown) {
      applyArchiveCaseResponse(undefined)
      errorMsg.value = (err as { message?: string })?.message || '身份证查询失败'
      return null
    }
    finally {
      idCardLoading.value = false
    }
  }

  async function loadArchiveCasesByToken(token: string): Promise<IdCardArchiveSearchResponse | null> {
    idCardLoading.value = true
    errorMsg.value = ''
    try {
      const res = await asResult<IdCardArchiveSearchResponse>(getArchiveCasesByToken(token))
      const data = res?.data
      applyArchiveCaseResponse(data)
      if (!archiveCases.value.length) {
        errorMsg.value = '未查询到该患者的影像病案'
      }
      return data || null
    }
    catch (err: unknown) {
      applyArchiveCaseResponse(undefined)
      errorMsg.value = (err as { message?: string })?.message || '身份证查询链接无效'
      return null
    }
    finally {
      idCardLoading.value = false
    }
  }

  function setPatientFromArchiveCase(archiveCase: IdCardArchiveCase | undefined) {
    if (!archiveCase) {
      return
    }
    patientList.value = [{
      id: archiveCase.patientRecordId,
      bah: archiveCase.bah,
      name: archiveCase.name,
      department: archiveCase.department,
      admissionTime: archiveCase.admissionTime,
    }]
  }

  function clearIdCardSearch() {
    archiveCases.value = []
    idCardToken.value = ''
    maskedIdCard.value = ''
  }

  async function handleDownload(): Promise<void> {
    if (!auth('record:download')) {
      ElMessage.warning('当前账号没有病案下载权限')
      return
    }
    const firstImage = images.value[0]
    const bah = padCode(searchBah.value || firstImage?.bah || '')
    const sjh = padCode(searchSjh.value || firstImage?.sjh || '')
    if (!images.value.length) {
      ElMessage.warning('当前档案没有可下载的影像')
      return
    }

    downloading.value = true
    try {
      const plan = await getArchiveZipExportPlan(bah || undefined, sjh || undefined)
      if (plan.data?.executionMode === 'BACKEND_JOB') {
        await exportJob.start({ format: 'ZIP', bah: bah || undefined, sjh: sjh || undefined })
        return
      }
      const archiveBlob = await downloadArchiveZip(bah || undefined, sjh || undefined)
      saveBlob(archiveBlob, `${bah || 'archive'}${sjh ? `-${sjh}` : ''}.zip`)
      ElMessage.success('档案袋已由服务器打包并开始下载')
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
    archiveCases,
    loading,
    patientLoading,
    idCardLoading,
    downloading,
    savingType,
    errorMsg,
    searchBah,
    searchSjh,
    searchUserId,
    searchIdCard,
    idCardToken,
    maskedIdCard,
    downloadJob: exportJob.job,
    downloadJobCancelling: exportJob.cancelling,
    downloadJobDownloading: exportJob.downloading,
    loadImages,
    loadArchiveCasesByIdCard,
    loadArchiveCasesByToken,
    setPatientFromArchiveCase,
    clearIdCardSearch,
    handleDownload,
    cancelDownloadJob: exportJob.cancel,
    downloadPreparedArchive: exportJob.download,
    dismissDownloadJob: exportJob.dismiss,
    saveImageType,
  }
}
