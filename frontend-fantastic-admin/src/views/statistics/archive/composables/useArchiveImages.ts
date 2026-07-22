import type { Ref } from 'vue'
import type { GalleryImage, PatientInfo } from '../types'
import type { ExternalArchiveCase, ExternalArchiveSession } from '@/api/modules/external-archive'
import type { IdCardArchiveCase, IdCardArchiveSearchResponse } from '@/api/modules/search'
import type { ApiResult, BAHImageData } from '@/api/types'
import { ElMessage } from 'element-plus'
import { ref, shallowRef, watch } from 'vue'
import {
  downloadArchiveZip,
  getArchiveZipExportPlan,
} from '@/api/modules/archive-export'
import {
  downloadExternalArchive,
  externalArchiveSession,
  getExternalArchiveImages,
} from '@/api/modules/external-archive'
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
import { archiveAccessMode } from '../access-mode'
import { padCode, readArchiveImageVersion, resolveImageUrl, writeArchiveImageVersion } from '../constants'
import { useArchiveExportJob } from './useArchiveExportJob'
import { addArchiveSearchHistory } from './useArchiveSearchHistory'

export interface UseArchiveImagesOptions {
  externalSession?: Readonly<Ref<ExternalArchiveSession | null | undefined>>
}

const EXTERNAL_CASE_TOKEN = 'external-ticket'

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

function externalCaseToArchiveCase(item: ExternalArchiveCase): IdCardArchiveCase {
  return {
    bah: normalizeMedicalRecordCode(item.bah),
    sjh: normalizeMedicalRecordCode(item.sjh || ''),
    name: item.patientName,
    department: item.department,
    admissionTime: item.admissionTime,
  }
}

export function useArchiveImages(options: UseArchiveImagesOptions = {}) {
  const { auth } = useAuth()
  const exportJob = useArchiveExportJob('ZIP')
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
  let downloadArchiveKey = ''

  const sessionSource = options.externalSession || externalArchiveSession
  const currentExternalSession = () => sessionSource.value || null
  const isExternalTicketMode = () => archiveAccessMode.value === 'external-ticket'

  function clearExternalViewState(message = '') {
    images.value = []
    patientList.value = []
    archiveCases.value = []
    idCardToken.value = ''
    maskedIdCard.value = ''
    errorMsg.value = message
  }

  function syncExternalArchiveCases(session = currentExternalSession()) {
    if (!session) {
      return false
    }
    archiveCases.value = session.cases.map(externalCaseToArchiveCase)
    idCardToken.value = EXTERNAL_CASE_TOKEN
    maskedIdCard.value = ''
    return true
  }

  function findExternalCase(bah: string, sjh: string): ExternalArchiveCase | undefined {
    const normalizedBah = normalizeMedicalRecordCode(bah)
    const normalizedSjh = normalizeMedicalRecordCode(sjh)
    return currentExternalSession()?.cases.find(item =>
      normalizeMedicalRecordCode(item.bah) === normalizedBah
      && normalizeMedicalRecordCode(item.sjh || '') === normalizedSjh,
    )
  }

  function bindDownloadArchive(bah: string, sjh: string) {
    const nextKey = `${padCode(bah)}|${padCode(sjh)}`
    if (downloadArchiveKey && downloadArchiveKey !== nextKey) {
      void exportJob.discard()
    }
    downloadArchiveKey = nextKey
  }

  async function loadPatient(bah: string, sjh = ''): Promise<void> {
    const externalCase = findExternalCase(bah, sjh)
    if (externalCase) {
      patientList.value = [{
        bah: normalizeMedicalRecordCode(externalCase.bah),
        name: externalCase.patientName,
        department: externalCase.department,
        admissionTime: externalCase.admissionTime,
      }]
      return
    }

    if (isExternalTicketMode()) {
      patientList.value = []
      return
    }

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
    const externalSession = currentExternalSession()

    searchBah.value = bah
    searchSjh.value = sjh

    if (isExternalTicketMode() && !externalSession) {
      clearExternalViewState('外部影像会话已失效，请重新发起访问')
      return
    }

    if (lookup.validationMessage) {
      images.value = []
      patientList.value = []
      errorMsg.value = lookup.validationMessage
      ElMessage.warning(lookup.validationMessage)
      return
    }

    const requestBah = lookup.requestBah || ''
    const requestSjh = lookup.requestSjh || ''
    if (externalSession && !findExternalCase(bah, sjh)) {
      images.value = []
      patientList.value = []
      errorMsg.value = '当前外部会话未授权访问该病案'
      return
    }

    bindDownloadArchive(requestBah, requestSjh)

    loading.value = true
    errorMsg.value = ''
    try {
      const res = externalSession
        ? await getExternalArchiveImages(requestBah, requestSjh || undefined, forceRefresh)
        : await asResult<BAHImageData[]>(getImgByCode(
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
      await loadPatient(patientBah, requestSjh || sjh)

      if (!externalSession) {
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

      if (!images.value.length) {
        errorMsg.value = '该病案暂未查询到影像'
      }
    }
    catch (err: unknown) {
      errorMsg.value = (err as { message?: string })?.message || '影像加载失败'
      images.value = []
      patientList.value = []
      if (!externalSession) {
        addArchiveSearchHistory({
          bah,
          sjh,
          status: 'failure',
          failureReason: errorMsg.value,
        })
      }
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
    if (isExternalTicketMode()) {
      return null
    }
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
    const externalSession = currentExternalSession()
    if (isExternalTicketMode()) {
      if (!externalSession || token !== EXTERNAL_CASE_TOKEN) {
        clearExternalViewState('外部影像会话已失效，请重新发起访问')
        return null
      }
      syncExternalArchiveCases(externalSession)
      return {
        token: EXTERNAL_CASE_TOKEN,
        maskedIdCard: '',
        cases: archiveCases.value,
      }
    }
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
    if (isExternalTicketMode()) {
      if (!syncExternalArchiveCases()) {
        clearExternalViewState('外部影像会话已失效，请重新发起访问')
      }
      return
    }
    archiveCases.value = []
    idCardToken.value = ''
    maskedIdCard.value = ''
  }

  async function handleDownload(): Promise<void> {
    const externalSession = currentExternalSession()
    const firstImage = images.value[0]
    const bah = padCode(searchBah.value || firstImage?.bah || '')
    const sjh = padCode(searchSjh.value || firstImage?.sjh || '')

    if (isExternalTicketMode()) {
      if (!externalSession) {
        ElMessage.warning('外部影像会话已失效，请重新发起访问')
        return
      }
      if (!externalSession.allowDownload) {
        ElMessage.warning('外部系统未授予批量下载权限')
        return
      }
      if (!images.value.length) {
        ElMessage.warning('当前档案没有可下载的影像')
        return
      }
      downloading.value = true
      try {
        const response = await downloadExternalArchive(bah, sjh || undefined)
        const archiveBlob = response.data
        if (!(archiveBlob instanceof Blob) || archiveBlob.size <= 0) {
          throw new Error('服务器返回的 ZIP 文件为空')
        }
        saveBlob(archiveBlob, `${bah || 'archive'}${sjh ? `-${sjh}` : ''}.zip`)
        ElMessage.success('外部档案袋已开始下载')
      }
      catch (err: unknown) {
        ElMessage.error((err as { message?: string })?.message || '档案下载失败')
      }
      finally {
        downloading.value = false
      }
      return
    }

    if (!auth('record:download')) {
      ElMessage.warning('当前账号没有病案下载权限')
      return
    }
    if (!images.value.length) {
      ElMessage.warning('当前档案没有可下载的影像')
      return
    }
    bindDownloadArchive(bah, sjh)

    downloading.value = true
    try {
      const plan = await getArchiveZipExportPlan(bah || undefined, sjh || undefined)
      if (plan.data?.executionMode === 'BACKEND_JOB') {
        await exportJob.start({ format: 'ZIP', bah: bah || undefined, sjh: sjh || undefined })
        return
      }

      try {
        const archiveBlob = await downloadArchiveZip(bah || undefined, sjh || undefined)
        if (!(archiveBlob instanceof Blob) || archiveBlob.size <= 0) {
          throw new Error('服务器返回的 ZIP 文件为空')
        }
        saveBlob(archiveBlob, `${bah || 'archive'}${sjh ? `-${sjh}` : ''}.zip`)
        ElMessage.success('档案袋已由服务器打包并开始下载')
      }
      catch {
        await exportJob.start({ format: 'ZIP', bah: bah || undefined, sjh: sjh || undefined })
        ElMessage.warning('直接下载未完成，已自动转为后台生成任务')
      }
    }
    catch (err: unknown) {
      ElMessage.error((err as { message?: string })?.message || '下载失败')
    }
    finally {
      downloading.value = false
    }
  }

  async function saveImageType(img: GalleryImage, nextType: number): Promise<void> {
    if (isExternalTicketMode()) {
      ElMessage.warning('外部影像会话为只读模式，不能修改图片类型')
      return
    }
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

  watch(sessionSource, (session) => {
    if (session) {
      syncExternalArchiveCases(session)
      return
    }
    if (isExternalTicketMode()) {
      clearExternalViewState('外部影像会话已失效，请重新发起访问')
    }
  }, { immediate: true })

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
