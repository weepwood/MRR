import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compare, extract, routeKey, strictFailures } from './mock-coverage.mjs'

test('normalizes full dynamic segments without merging HTTP methods', () => {
  assert.equal(routeKey('get', '/api/v1/items/:id'), 'GET /api/v1/items/{}')
  assert.notEqual(routeKey('get', '/api/v1/items/:id'), routeKey('post', '/api/v1/items/:id'))
  assert.equal(routeKey('get', '/api/v1/items/{}-suffix'), undefined)
})

test('extracts generic calls, constant prefix, method clients and unresolved values', () => {
  const source = [
    "const BASE = '/api/v1/items'",
    "getRequest<Array<string>>(`${BASE}/${encodeURIComponent(id)}`)",
    "postRequest('/api/v1/items', payload)",
    "api.get<Blob, Blob>('/api/v1/download', config)",
    "getRequest(computePath())",
    "// getRequest('/api/v1/ignored')",
  ].join('\n')
  const result = extract(source, 'api.ts', 'api')
  assert.deepEqual(result.entries.map(item => item.key), [
    'GET /api/v1/items/{}', 'POST /api/v1/items', 'GET /api/v1/download',
  ])
  assert.equal(result.unresolved.length, 1)
})

test('reports missing methods and duplicate registrations; excludes unrelated url properties', () => {
  const source = [
    "{ url: '/api/v1/items/:id', response: () => ({}) }",
    "{ url: '/api/v1/items/:other', method: 'get', response() {} }",
    "{ url: '/api/v1/file', rawResponse: () => {} }",
    "{ url: '/not-a-route' }",
  ].map(s => `(${s})`).join('\n;')
  const mock = extract(source, 'mock.ts', 'mock')
  const api = extract("getRequest(`/api/v1/items/${id}`); postRequest('/api/v1/items')", 'api.ts', 'api')
  const report = compare(api.entries, mock.entries)
  assert.equal(mock.entries.length, 3)
  assert.equal(report.covered, 1)
  assert.equal(report.missing[0].key, 'POST /api/v1/items')
  assert.equal(report.duplicates[0].locations.length, 2)
})

test('strict coverage rejects duplicates and missing routes only for selected API modules', () => {
  const api = [
    { key: 'GET /api/v1/a', file: 'src/api/modules/ready.ts', line: 1 },
    { key: 'POST /api/v1/b', file: 'src/api/modules/ready.ts', line: 2 },
    { key: 'GET /api/v1/c', file: 'src/api/modules/unfinished.ts', line: 1 },
  ]
  const mock = [
    { key: 'GET /api/v1/a', file: 'src/mock/a.ts', line: 1 },
    { key: 'GET /api/v1/a', file: 'src/mock/b.ts', line: 1 },
  ]
  const report = { modules: ['ready', 'unfinished'], dev: compare(api, mock), pages: compare(api, mock), unresolved: [] }
  const failures = strictFailures(report, ['ready'])
  assert.deepEqual(failures.map(item => [item.mode, item.kind, item.key]), [
    ['dev', 'missing', 'POST /api/v1/b'],
    ['dev', 'duplicate', 'GET /api/v1/a'],
    ['pages', 'missing', 'POST /api/v1/b'],
    ['pages', 'duplicate', 'GET /api/v1/a'],
  ])
  assert.equal(strictFailures(report, ['unknown'])[0].kind, 'unknown-module')
})

test('strict coverage detects unresolved selected calls and permits incomplete unrelated modules', () => {
  const api = [{ key: 'GET /api/v1/a', file: 'src/api/modules/ready.ts', line: 1 }]
  const mock = [{ key: 'GET /api/v1/a', file: 'src/mock/a.ts', line: 1 }]
  const report = {
    modules: ['ready', 'unfinished'],
    dev: compare(api, mock),
    pages: compare(api, mock),
    unresolved: [{ file: 'src/api/modules/unfinished.ts', line: 2, method: 'GET' }],
  }
  assert.deepEqual(strictFailures(report, ['ready']), [])
  assert.equal(strictFailures(report, ['unfinished'])[0].kind, 'unresolved')
})
