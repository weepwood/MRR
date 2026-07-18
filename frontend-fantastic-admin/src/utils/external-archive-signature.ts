export interface ExternalArchiveSignatureInput {
  method: string
  path: string
  timestamp: string
  nonce: string
  rawBody: string
  secret: string
}

function bytesToHex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

export async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return bytesToHex(digest)
}

export function buildExternalArchiveCanonicalText(input: Omit<ExternalArchiveSignatureInput, 'secret'> & { bodyHash: string }): string {
  return [
    input.method.trim().toUpperCase(),
    input.path.trim(),
    input.timestamp.trim(),
    input.nonce.trim(),
    input.bodyHash,
  ].join('\n')
}

export async function createExternalArchiveSignature(input: ExternalArchiveSignatureInput) {
  const bodyHash = await sha256Hex(input.rawBody)
  const canonicalText = buildExternalArchiveCanonicalText({
    method: input.method,
    path: input.path,
    timestamp: input.timestamp,
    nonce: input.nonce,
    rawBody: input.rawBody,
    bodyHash,
  })
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(input.secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(canonicalText),
  )

  return {
    bodyHash,
    canonicalText,
    signature: bytesToHex(signature),
  }
}

export function createRequestNonce(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes).map(byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
