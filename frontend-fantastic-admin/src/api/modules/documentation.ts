import { postRequest } from '../index'

export interface DocumentationSession {
  target: string
  expiresIn: number
}

/** 创建短期 HttpOnly 文档访问会话。 */
export function createDocumentationSession(target: string) {
  return postRequest<DocumentationSession>('/api/v1/documentation/session', { target })
}
