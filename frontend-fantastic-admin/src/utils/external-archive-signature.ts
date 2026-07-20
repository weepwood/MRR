export interface ExternalArchiveSignatureInput {
  method: string
  path: string
  timestamp: string
  nonce: string
  rawBody: string
  secret: string
}

const SHA256_CONSTANTS = new Uint32Array([
  0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4, 0xAB1C5ED5,
  0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174,
  0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
  0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967,
  0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85,
  0xA2BFE8A1, 0xA81A664B, 0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
  0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
  0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
])

function rotateRight(value: number, amount: number): number {
  return (value >>> amount) | (value << (32 - amount))
}

function concatBytes(...chunks: Uint8Array[]): Uint8Array {
  const length = chunks.reduce((total, chunk) => total + chunk.length, 0)
  const result = new Uint8Array(length)
  let offset = 0
  chunks.forEach((chunk) => {
    result.set(chunk, offset)
    offset += chunk.length
  })
  return result
}

function toWebCryptoBytes(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  return Uint8Array.from(bytes)
}

function sha256Fallback(input: Uint8Array): Uint8Array {
  const bitLength = input.length * 8
  const paddedLength = Math.ceil((input.length + 9) / 64) * 64
  const padded = new Uint8Array(paddedLength)
  padded.set(input)
  padded[input.length] = 0x80

  const paddedView = new DataView(padded.buffer)
  paddedView.setUint32(paddedLength - 8, Math.floor(bitLength / 0x100000000), false)
  paddedView.setUint32(paddedLength - 4, bitLength >>> 0, false)

  let h0 = 0x6A09E667
  let h1 = 0xBB67AE85
  let h2 = 0x3C6EF372
  let h3 = 0xA54FF53A
  let h4 = 0x510E527F
  let h5 = 0x9B05688C
  let h6 = 0x1F83D9AB
  let h7 = 0x5BE0CD19
  const words = new Uint32Array(64)

  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let index = 0; index < 16; index += 1) {
      words[index] = paddedView.getUint32(offset + index * 4, false)
    }
    for (let index = 16; index < 64; index += 1) {
      const previous15 = words[index - 15]
      const previous2 = words[index - 2]
      const sigma0 = rotateRight(previous15, 7) ^ rotateRight(previous15, 18) ^ (previous15 >>> 3)
      const sigma1 = rotateRight(previous2, 17) ^ rotateRight(previous2, 19) ^ (previous2 >>> 10)
      words[index] = (words[index - 16] + sigma0 + words[index - 7] + sigma1) >>> 0
    }

    let a = h0
    let b = h1
    let c = h2
    let d = h3
    let e = h4
    let f = h5
    let g = h6
    let h = h7

    for (let index = 0; index < 64; index += 1) {
      const sum1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25)
      const choice = (e & f) ^ (~e & g)
      const temporary1 = (h + sum1 + choice + SHA256_CONSTANTS[index] + words[index]) >>> 0
      const sum0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22)
      const majority = (a & b) ^ (a & c) ^ (b & c)
      const temporary2 = (sum0 + majority) >>> 0

      h = g
      g = f
      f = e
      e = (d + temporary1) >>> 0
      d = c
      c = b
      b = a
      a = (temporary1 + temporary2) >>> 0
    }

    h0 = (h0 + a) >>> 0
    h1 = (h1 + b) >>> 0
    h2 = (h2 + c) >>> 0
    h3 = (h3 + d) >>> 0
    h4 = (h4 + e) >>> 0
    h5 = (h5 + f) >>> 0
    h6 = (h6 + g) >>> 0
    h7 = (h7 + h) >>> 0
  }

  const output = new Uint8Array(32)
  const outputView = new DataView(output.buffer)
  ;[h0, h1, h2, h3, h4, h5, h6, h7].forEach((value, index) => {
    outputView.setUint32(index * 4, value, false)
  })
  return output
}

async function sha256Bytes(input: Uint8Array): Promise<Uint8Array> {
  const subtle = globalThis.crypto?.subtle
  if (subtle) {
    try {
      return new Uint8Array(await subtle.digest('SHA-256', toWebCryptoBytes(input)))
    }
    catch {
      // 内网 HTTP 环境可能暴露 crypto 但拒绝 subtle 操作，回退到纯 JS 实现。
    }
  }
  return sha256Fallback(input)
}

async function hmacSha256Bytes(secret: Uint8Array, message: Uint8Array): Promise<Uint8Array> {
  const subtle = globalThis.crypto?.subtle
  if (subtle) {
    try {
      const key = await subtle.importKey(
        'raw',
        toWebCryptoBytes(secret),
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['sign'],
      )
      return new Uint8Array(await subtle.sign('HMAC', key, toWebCryptoBytes(message)))
    }
    catch {
      // 与 SHA-256 相同，安全上下文不可用时使用兼容实现。
    }
  }

  const blockSize = 64
  const normalizedKey = secret.length > blockSize ? await sha256Bytes(secret) : secret
  const keyBlock = new Uint8Array(blockSize)
  keyBlock.set(normalizedKey)
  const innerPad = new Uint8Array(blockSize)
  const outerPad = new Uint8Array(blockSize)
  for (let index = 0; index < blockSize; index += 1) {
    innerPad[index] = keyBlock[index] ^ 0x36
    outerPad[index] = keyBlock[index] ^ 0x5C
  }
  const innerHash = await sha256Bytes(concatBytes(innerPad, message))
  return sha256Bytes(concatBytes(outerPad, innerHash))
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

export async function sha256Hex(value: string): Promise<string> {
  return bytesToHex(await sha256Bytes(new TextEncoder().encode(value)))
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
  const signature = await hmacSha256Bytes(
    new TextEncoder().encode(input.secret),
    new TextEncoder().encode(canonicalText),
  )

  return {
    bodyHash,
    canonicalText,
    signature: bytesToHex(signature),
  }
}

export function createRequestNonce(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes)
  }
  else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0F) | 0x40
  bytes[8] = (bytes[8] & 0x3F) | 0x80
  const hex = Array.from(bytes).map(byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
