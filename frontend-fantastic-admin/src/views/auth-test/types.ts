export interface AuthTestHistoryEvent {
  name: string
  method: string
  path: string
  status: number
  durationMs: number
}

export interface AuthTestHistoryItem extends AuthTestHistoryEvent {
  id: number
  requestedAt: string
}
