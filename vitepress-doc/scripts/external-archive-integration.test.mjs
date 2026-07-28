import assert from 'node:assert/strict'
import { createHash, createHmac } from 'node:crypto'
import { access, readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const projectRoot = fileURLToPath(new URL('../', import.meta.url))
const documentPath = path.join(
  projectRoot,
  'internal',
  'external-archive-integration.md',
)

const expectedSourceImports = [
  '../../backend-repo/examples/his-integration/python/mrr_archive_ticket_client.py',
  '../../backend-repo/examples/his-integration/java/MrrArchiveTicketClient.java',
  '../../backend-repo/examples/his-integration/java/Main.java',
  '../../backend-repo/examples/his-integration/java/pom.xml',
  '../../backend-repo/examples/his-integration/csharp/MrrArchiveTicketClient.cs',
  '../../backend-repo/examples/his-integration/csharp/Program.cs',
  '../../backend-repo/examples/his-integration/csharp/MrrArchiveTicketClient.csproj',
]

const proxyGuidePaths = [
  documentPath,
  path.resolve(
    projectRoot,
    '../backend-repo/docs/his-external-archive-integration-guide.md',
  ),
  path.resolve(
    projectRoot,
    '../backend-repo/docs/external-archive-integration.md',
  ),
]

test('external integration guide renders every runnable client source file', async () => {
  const markdown = await readFile(documentPath, 'utf8')
  const importedPaths = [...markdown.matchAll(/^<<<\s+(\S+)(?:\s+.*)?$/gm)]
    .map(match => match[1])

  for (const sourcePath of expectedSourceImports) {
    assert.equal(
      importedPaths.filter(value => value === sourcePath).length,
      1,
      `expected exactly one VitePress source import for ${sourcePath}`,
    )

    await access(path.resolve(path.dirname(documentPath), sourcePath))
  }
})

test('external integration guide publishes a reproducible HMAC test vector', async () => {
  const markdown = await readFile(documentPath, 'utf8')
  const rawBody = Buffer.from(
    '{"externalUserId":"HIS-USER-DEMO","archives":[{"bah":"00001234","sjh":"00005678"}],"allowDownload":false}',
    'utf8',
  )
  const secret = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
  const timestamp = '1784383200'
  const nonce = '7fd72b36-d39a-4ea5-80cb-aafeeaed1815'
  const bodyHash = createHash('sha256').update(rawBody).digest('hex')
  const canonicalText = [
    'POST',
    '/api/v1/integration/archive/tickets',
    timestamp,
    nonce,
    bodyHash,
  ].join('\n')
  const signature = createHmac('sha256', Buffer.from(secret, 'utf8'))
    .update(Buffer.from(canonicalText, 'utf8'))
    .digest('hex')

  assert.ok(markdown.includes(bodyHash), 'guide must contain the computed body hash')
  assert.ok(markdown.includes(signature), 'guide must contain the computed HMAC signature')
})

test('external integration guides overwrite untrusted forwarded IP headers', async () => {
  for (const guidePath of proxyGuidePaths) {
    const markdown = await readFile(guidePath, 'utf8')

    assert.ok(
      markdown.includes('proxy_set_header X-Forwarded-For $remote_addr;'),
      `${guidePath} must overwrite client-supplied X-Forwarded-For`,
    )
    assert.ok(
      !markdown.includes(
        'proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;',
      ),
      `${guidePath} must not append an untrusted client-supplied X-Forwarded-For`,
    )
  }
})
