import type { AuthUser } from './types'

export interface CredentialAwareUser extends AuthUser {
  contactInfo?: string
  applyRemark?: string
  appliedAt?: string
  reviewedAt?: string
  reviewedBy?: number
  rejectReason?: string
  mustChangePassword?: boolean
  passwordVersion?: number
  passwordChangedAt?: string
  temporaryPasswordExpiresAt?: string
  passwordResetAt?: string
  passwordResetBy?: number
}

export interface RegistrationPayload {
  username: string
  password: string
  displayName: string
  contactInfo?: string
  applyRemark?: string
}

export interface RegistrationResult {
  id: number
  username: string
  displayName: string
  status: 'pending'
  appliedAt?: string
}

export interface RegistrationApprovalPayload {
  roleCode: string
}

export interface RegistrationRejectionPayload {
  rejectReason: string
}

export interface AdminCreateUserPayload {
  username: string
  displayName?: string
  roleCode: string
  status: 'active'
  temporaryPasswordValidHours: number
}

export interface AdminResetPasswordPayload {
  administratorPassword: string
  temporaryPasswordValidHours: number
}

export interface RequiredPasswordChangePayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface UserCredentialResult {
  user: CredentialAwareUser
  temporaryPassword: string
  temporaryPasswordExpiresAt: string
}
