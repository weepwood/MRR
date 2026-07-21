export function normalizeExternalLinkUrl(value: string): string | null {
  const input = value.trim()
  if (!input) {
    return null
  }

  const candidate = /^[a-z][a-z\d+.-]*:/i.test(input) ? input : `https://${input}`

  try {
    const url = new URL(candidate)
    if (!['http:', 'https:'].includes(url.protocol)) {
      return null
    }
    return url.toString()
  }
  catch {
    return null
  }
}

export function createExternalLinkId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `external-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}
