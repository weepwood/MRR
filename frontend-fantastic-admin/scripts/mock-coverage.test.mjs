import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compare, extract, routeKey } from './mock-coverage.mjs'

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
