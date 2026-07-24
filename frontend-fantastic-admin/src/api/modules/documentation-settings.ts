import { getRequest } from '../index'
import { createDefaultSystemSettings, isAllowedDocumentationUrl } from '@/utils/system-settings'

export interface DocumentationSettings {
  documentationUserGuideUrl: string
  documentationDeveloperUrl: string
  documentationOperationsUrl: string
}

const defaults = createDefaultSystemSettings()

export const DEFAULT_DOCUMENTATION_SETTINGS: DocumentationSettings = {
  documentationUserGuideUrl: defaults.documentationUserGuideUrl,
  documentationDeveloperUrl: defaults.documentationDeveloperUrl,
  documentationOperationsUrl: defaults.documentationOperationsUrl,
}

function normalizeUrl(value: unknown, fallback: string): string {
  if (value === undefined || value === null) { return fallback }
  const normalized = String(value).trim()
  if (!normalized) { return '' }
  return isAllowedDocumentationUrl(normalized) ? normalized : fallback
}

export function normalizeDocumentationSettings(
  values?: Record<string, unknown> | null,
): DocumentationSettings {
  const source = values || {}
  return {
    documentationUserGuideUrl: normalizeUrl(
      source.documentationUserGuideUrl,
      DEFAULT_DOCUMENTATION_SETTINGS.documentationUserGuideUrl,
    ),
    documentationDeveloperUrl: normalizeUrl(
      source.documentationDeveloperUrl,
      DEFAULT_DOCUMENTATION_SETTINGS.documentationDeveloperUrl,
    ),
    documentationOperationsUrl: normalizeUrl(
      source.documentationOperationsUrl,
      DEFAULT_DOCUMENTATION_SETTINGS.documentationOperationsUrl,
    ),
  }
}

/** 帮助中心只读取后端白名单后的三个文档入口。 */
export async function getPublicDocumentationSettings(): Promise<DocumentationSettings> {
  try {
    const response = await getRequest<Record<string, string>>('/api/v1/public/config/documentation', {
      skipGlobalError: true,
    } as any)
    return normalizeDocumentationSettings(response.data)
  }
  catch {
    return { ...DEFAULT_DOCUMENTATION_SETTINGS }
  }
}
