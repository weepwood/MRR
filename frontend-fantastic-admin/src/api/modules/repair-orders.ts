import { getRequest, postRequest } from '../index'

export type RepairAction = 'approve' | 'apply' | 'verify' | 'cancel' | 'compensate'
export interface RepairOrder {
  id: number
  issueId: number
  scanId: number | null
  entityType?: string
  entityId?: string
  targetArchiveId: number
  status: string
  proposedBy: number
  approvedBy: number | null
  reopenedFrom?: number | null
}
export interface RepairEvent {
  id: number
  actorId: number
  action: string
  beforeArchiveId: number | null
  afterArchiveId: number | null
  createdAt: string
}
export interface RepairWorkbench {
  enabled: boolean
  actorId: number
  orders: RepairOrder[]
}

const base = '/api/v1/system/data-quality/repair-orders'
export function listRepairOrders(filters: { archiveId?: number, issueId?: number, before?: number } = {}) {
  return getRequest<RepairWorkbench>(base, { params: filters })
}
export function getRepairEvents(id: number, after = 0) {
  return getRequest<RepairEvent[]>(`${base}/${id}/events`, { params: { after } })
}
export function proposeRepairOrder(issueId: number) {
  return postRequest<RepairOrder>(base, undefined, { params: { issueId } })
}
export function actOnRepairOrder(id: number, action: RepairAction) {
  switch (action) {
    case 'approve': return postRequest<RepairOrder>(`${base}/${id}/approve`)
    case 'apply': return postRequest<RepairOrder>(`${base}/${id}/apply`)
    case 'verify': return postRequest<RepairOrder>(`${base}/${id}/verify`)
    case 'cancel': return postRequest<RepairOrder>(`${base}/${id}/cancel`)
    case 'compensate': return postRequest<RepairOrder>(`${base}/${id}/compensate`)
  }
}
