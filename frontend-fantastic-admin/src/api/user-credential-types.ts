import type { AuthUser } from './types'

export interface CredentialAwareUser extends AuthUser {
  mustChangePassword?: boolean
  passwordVersion?: number
  passwordChangedAt?: string
  temporaryPasswordExpiresAt?: string
  passwordResetAt?: string
  passwordResetBy?: number
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
