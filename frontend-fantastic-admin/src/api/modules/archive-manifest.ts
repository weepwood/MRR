import { getRequest, postRequest } from '../index'

export interface ManifestObservation {
  version: number
  archiveId: number
  status: string
  processed: number
  verified: number
  anomalies: number
  completeDigestAvailable: boolean
  createdAt: string
  updatedAt: string
}

export interface ManifestIssue {
  cursor: number
  scanId: number
  status: string
  bytesRead: number
  digestAvailable: boolean
  checkedAt: string
}

const base = '/api/v1/operations/archive-manifests'

export function getLatestManifest(archiveId: number) {
  return getRequest<{ enabled: boolean, latest: ManifestObservation | null }>(base, { params: { archiveId } })
}

export function startManifest(archiveId: number) {
  return postRequest<ManifestObservation>(base, undefined, { params: { archiveId } })
}

export function cancelManifest(version: number) {
  return postRequest<ManifestObservation>(`${base}/${version}/cancel`)
}

export function getManifestIssues(version: number, after = 0) {
  return getRequest<ManifestIssue[]>(`${base}/${version}/anomalies`, { params: { after } })
}
