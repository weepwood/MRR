import type { ExternalArchiveSession } from '@/api/modules/external-archive'
import type { IdCardArchiveCase, IdCardArchiveSearchResponse } from '@/api/modules/search'
import type { ApiResult, BAHImageData } from '@/api/types'
import type { GalleryImage, PatientInfo } from '../types'
import { ElMessage } from 'element-plus'
import { ref, shallowRef } from 'vue'
import {
  getExternalArchiveImages,
} from '@/api/modules/external-archive'
import { getImgByCode, updateImageType } from '@/api/modules/image'
import {
  getArchiveCasesByIdCard,
  getArchiveCasesByToken,
  getPatientByBah,
} from '@/api/modules/search'
import {
  getArchiveLookupValidationMessage,
  normalizeMedicalRecordCode,
  requiresSjhForBah,
} from '@/utils/medical-record-code'
import { padCode, readArchiveImageVersion, resolveImageUrl, writeArchiveImageVersion } from '../constants'
import { createArchiveZip } from '../utils/client-zip'
import { addArchiveSearchHistory } from './useArchiveSearchHistory'

const EXTERNAL_SESSION_STORAGE_KEY = 'MRR-EXTERNAL-ARCHIVE:session'

function asResult<T>(promise: Promise<unknown>): Promise<ApiResult<T>> {
  return promise as unknown as Promise<ApiResult<T>>
}

function isExternalArchiveAccess(): boolean {
  return typeof window !== 'undefined' && window.location.pathname.startsWith('/archive/external')
}

function readExternalSession(): ExternalArchiveSession | null {
  if (!isExternalArchiveAccess()) {
    return null
  }
  try {
    const raw = sessionStorage.getItem(EXTERNAL_SESSION_STORAGE_KEY)
    return raw ? JSON.parse(raw) as ExternalArchiveSession : null
  }
  catch {
    return null
  }
}

function toArchiveCase(item: ExternalArchiveSession['cases'][number]): IdCardArchiveCase {
  return {
    bah: item.bah,
    sjh: item.sjh,
    name: item.patientName,
    department: item.department,
    admissionTime: item.admissionTime,
  }
}

export function useArchiveImages() {
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

  function syncExternalArchiveCases(): ExternalArchiveSession | null {
    const session = readExternalSession()
    archiveCases.value = session?.cases?.map(toArchiveCase) || []
    return session
  }

  async function loadPatient(bah: string): Promise<void> {
    if (isExternalArchiveAccess()) {
      const session = syncExternalArchiveCases()
      const normalizedBah = normalizeMedicalRecordCode(bah)
      const normalizedSjh = normalizeMedicalRecordCode(searchSjh.value)
      const selected = session?.cases?.find(item =>
        normalizeMedicalRecordCode(item.bah) === normalizedBah
        && normalizeMedicalRecordCode(item.sjh || '') === normalizedSjh,
      ) || session?.cases?.find(item => normalizeMedicalRecordCode(item.bah) === normalizedBah)
      patientList.value = selected
        ? [{
            bah: normalizedBah,
            name: selected.patientName,
            department: selected.department,
            admissionTime: selected.admissionTime,
          }]
        : []
      return
    }

    if (!bah || requiresSjhForBah(bah)) {
      // 患者表没有上架号，非唯一病案号无法可靠关联患者，避免显示第一条同号患者。
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
    const bah = padCode(searchBah.value)
    const sjh = padCode(searchSjh.value)
    const userid = searchUserId.value.trim()
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
      const request = isExternalArchiveAccess()
        ? getExternalArchiveImages(bah, sjh || undefined, forceRefresh)
        : getImgByCode(bah || undefined, sjh || undefined, forceRefresh, userid || undefined)
      const res = await asResult<BAHImageData[]>(request)
      const rawList = Array.isArray(res?.data) ? res.data : []
      let cacheBuster = readArchiveImageVersion(bah, sjh)
      if (forceRefresh) {
        cacheBuster = Date.now()
        writeArchiveImageVersion(bah, sjh, cacheBuster)
      }
      images.value = rawList.map((item: BAHImageData) => ({
        ...item,
        bah: normalizeMedicalRecordCode(item.bah),
        sjh: normalizeMedicalRecordCode(item.sjh),
        imageUrl: resolveImageUrl(item, cacheBuster),
      }))
      const patientBah = normalizeMedicalRecordCode(rawList[0]?.bah || bah)
      await loadPatient(patientBah)

      if (!isExternalArchiveAccess()) {
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
    }
    catch (err: unknown) {
      errorMsg.value = (err as { message?: string })?.message || '影像加载失败'
      images.value = []
      patientList.value = []
      if (!isExternalArchiveAccess()) {
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
    if (isExternalArchiveAccess()) {
      errorMsg.value = '外部影像会话不允许重新按身份证搜索'
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
    if (isExternalArchiveAccess()) {
      const session = syncExternalArchiveCases()
      return session
        ? { token: '', maskedIdCard: '', cases: archiveCases.value }
        : null
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
    if (isExternalArchiveAccess()) {
      syncExternalArchiveCases()
      idCardToken.value = ''
      maskedIdCard.value = ''
      return
    }
    archiveCases.value = []
    idCardToken.value = ''
    maskedIdCard.value = ''
  }

  async function handleDownload(): Promise<void> {
    const externalSession = readExternalSession()
    if (isExternalArchiveAccess() && !externalSession?.allowDownload) {
      ElMessage.warning('外部系统未授予批量下载权限')
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
      const archiveBlob = await createArchiveZip(images.value)
      const url = URL.createObjectURL(archiveBlob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${bah || 'archive'}${sjh ? `-${sjh}` : ''}.zip`
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
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
    if (isExternalArchiveAccess()) {
      ElMessage.warning('外部影像会话为只读模式，不能修改影像类型')
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
    loadImages,
    loadArchiveCasesByIdCard,
    loadArchiveCasesByToken,
    setPatientFromArchiveCase,
    clearIdCardSearch,
    handleDownload,
    saveImageType,
  }
}
