import type { PaginatedResult } from '../types'
import { deleteRequest, getRequest, postRequest, putRequest } from '../index'

export type ArchiveBoxStatus = 'NORMAL' | 'MISSING' | 'MISPLACED' | 'CONFLICT' | 'OTHER'

export interface ArchiveBoxRecord {
  id?: number
  bah?: string
  sjh?: string
  boxNo?: string
  expectedBoxNo?: string
  status: ArchiveBoxStatus
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ArchiveBoxRecordPayload {
  bah?: string
  sjh?: string
  boxNo?: string
  expectedBoxNo?: string
  status: ArchiveBoxStatus
  remark?: string
}

export interface ArchiveBoxSummary {
  totalRecords: number
  totalBoxes: number
  missingCount: number
  abnormalCount: number
}

export interface ArchiveBoxGroup {
  boxNo: string
  recordCount: number
  abnormalCount: number
  updatedAt?: string
}

export interface ArchiveBoxQuery {
  page: number
  size: number
  keyword?: string
  bah?: string
  sjh?: string
  boxNo?: string
  status?: ArchiveBoxStatus | ''
  sortBy?: 'bah' | 'sjh' | 'boxNo' | 'status' | 'createdAt' | 'updatedAt'
  sortOrder?: 'asc' | 'desc'
}

export function getArchiveBoxRecords(params: ArchiveBoxQuery) {
  return getRequest<PaginatedResult<ArchiveBoxRecord>>('/api/v1/archive-box-records', { params })
}

export function getArchiveBoxRecord(id: number) {
  return getRequest<ArchiveBoxRecord>(`/api/v1/archive-box-records/${id}`)
}

export function getArchiveBoxRecordsByCode(code: string) {
  return getRequest<ArchiveBoxRecord[]>(`/api/v1/archive-box-records/record/${encodeURIComponent(code)}`)
}

export function getArchiveBoxRecordsByBox(boxNo: string) {
  return getRequest<ArchiveBoxRecord[]>(`/api/v1/archive-box-records/box/${encodeURIComponent(boxNo)}`)
}

export function getArchiveBoxSummary() {
  return getRequest<ArchiveBoxSummary>('/api/v1/archive-box-records/summary')
}

export function getArchiveBoxGroups(params: { page: number, size: number, keyword?: string }) {
  return getRequest<PaginatedResult<ArchiveBoxGroup>>('/api/v1/archive-box-records/boxes', { params })
}

export function createArchiveBoxRecord(payload: ArchiveBoxRecordPayload) {
  return postRequest<ArchiveBoxRecord>('/api/v1/archive-box-records', payload)
}

export function updateArchiveBoxRecord(id: number, payload: ArchiveBoxRecordPayload) {
  return putRequest<ArchiveBoxRecord>(`/api/v1/archive-box-records/${id}`, payload)
}

export function deleteArchiveBoxRecord(id: number) {
  return deleteRequest<string>(`/api/v1/archive-box-records/${id}`)
}
