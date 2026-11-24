import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const verbs = new Map([
  ['getRequest', 'GET'], ['getRawRequest', 'GET'], ['postRequest', 'POST'],
  ['putRequest', 'PUT'], ['deleteRequest', 'DELETE'], ['patchRequest', 'PATCH'],
])
const httpMethods = new Set(['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'])

function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name)
    return entry.isDirectory()
      ? entry.name === '__tests__' ? [] : files(path)
      : path.endsWith('.ts') && !/\.(test|spec|d)\.ts$/.test(path) ? [path] : []
  }).sort()
}

function walk(node, visit) {
  visit(node)
  node.forEachChild(child => walk(child, visit))
}

function stringValue(node) {
  return node && ts.isStringLiteralLike(node) ? node.text : undefined
}

// Normalize full parameter segments only. Do not guess a partial dynamic segment.
export function routeKey(method, path) {
  if (!path || !path.startsWith('/') || /[?#]/.test(path)) {
    return undefined
  }
  const normalized = path.replace(/\/$/, '').split('/').map(segment =>
    /^:[\w]+$/.test(segment) || segment === '{}' ? '{}' : segment,
  ).join('/')
  if (/[{}:*]/.test(normalized.replaceAll('{}', ''))) {
    return undefined
  }
  return `${method.toUpperCase()} ${normalized}`
}

export function extract(source, filename, kind) {
  const ast = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true)
  const constants = new Map()
  const entries = []
  const unresolved = []
  walk(ast, node => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name)) {
      const value = stringValue(node.initializer)
      if (value !== undefined && node.parent.flags & ts.NodeFlags.Const) {
        constants.set(node.name.text, value)
      }
    }
  })
  function pathValue(node) {
    const literal = stringValue(node)
    if (literal !== undefined) { return literal }
    if (!node) { return undefined }
    if (ts.isIdentifier(node)) { return constants.get(node.text) }
    if (ts.isTemplateExpression(node)) {
      return node.head.text + node.templateSpans.map(span =>
        (ts.isIdentifier(span.expression) && constants.has(span.expression.text)
          ? constants.get(span.expression.text)
          : '{}') + span.literal.text,
      ).join('')
    }
    return undefined
  }
  function add(node, method, path) {
    const line = ast.getLineAndCharacterOfPosition(node.getStart(ast)).line + 1
    const key = method && httpMethods.has(method.toUpperCase()) && routeKey(method, path)
    if (key) {
      entries.push({ key, file: filename, line })
    }
    else {
      unresolved.push({ file: filename, line, method: method ?? 'UNKNOWN' })
    }
  }
  walk(ast, node => {
    if (kind === 'api' && ts.isCallExpression(node)) {
      const callee = node.expression
      if (ts.isIdentifier(callee) && verbs.has(callee.text)) {
        add(node, verbs.get(callee.text), pathValue(node.arguments[0]))
      }
      else if (ts.isPropertyAccessExpression(callee)
        && ts.isIdentifier(callee.expression)
        && ['api', 'externalApi', 'rawApi'].includes(callee.expression.text)
        && httpMethods.has(callee.name.text.toUpperCase())) {
        add(node, callee.name.text.toUpperCase(), pathValue(node.arguments[0]))
      }
    }
    if (kind === 'mock' && ts.isObjectLiteralExpression(node)) {
      const props = new Map(node.properties.filter(ts.isPropertyAssignment)
        .map(prop => [prop.name.getText(ast).replace(/^['"]|['"]$/g, ''), prop.initializer]))
      if (props.has('url') && (props.has('response') || props.has('rawResponse')
        || node.properties.some(prop => ts.isMethodDeclaration(prop) && ['response', 'rawResponse'].includes(prop.name.getText(ast))))) {
        add(node, props.has('method') ? stringValue(props.get('method')) : 'GET', pathValue(props.get('url')))
      }
    }
  })
  return { entries, unresolved }
}

export function compare(api, mock) {
  const registered = new Map()
  for (const entry of mock) {
    const group = registered.get(entry.key) ?? []
    group.push(entry)
    registered.set(entry.key, group)
  }
  const requested = new Map()
  for (const entry of api) {
    const group = requested.get(entry.key) ?? []
    group.push(entry)
    requested.set(entry.key, group)
  }
  const missing = [...requested].filter(([key]) => !registered.has(key))
    .map(([key, locations]) => ({ key, locations }))
  const duplicates = [...registered].filter(([, locations]) => locations.length > 1)
    .map(([key, locations]) => ({ key, locations }))
  return { requested: requested.size, covered: requested.size - missing.length, missing, duplicates }
}

export function audit(root) {
  const api = files(join(root, 'src/api/modules')).map(file =>
    extract(readFileSync(file, 'utf8'), relative(root, file), 'api'))
  const mock = files(join(root, 'src/mock')).map(file =>
    extract(readFileSync(file, 'utf8'), relative(root, file), 'mock'))
  const requested = api.flatMap(item => item.entries)
  const registered = mock.flatMap(item => item.entries)
  const select = mode => registered.filter(item => mode === 'pages'
    ? !item.file.endsWith('/nginx-browser.ts')
    : !item.file.endsWith('/nginx-browser.pages.ts'))
  return {
    dev: compare(requested, select('dev')),
    pages: compare(requested, select('pages')),
    unresolved: [...api, ...mock].flatMap(item => item.unresolved),
    limitations: 'Static API module call sites only; no runtime matching, OpenAPI schema, direct page requests or computed HTTP client aliases.',
  }
}

function main() {
  const root = fileURLToPath(new URL('../', import.meta.url))
  const report = audit(root)
  const strict = process.argv.find(arg => arg.startsWith('--strict='))
  if (process.argv.includes('--json')) {
    process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  }
  else {
    for (const mode of ['dev', 'pages']) {
      const result = report[mode]
      console.log(`[${mode}] ${result.covered}/${result.requested} statically matched API routes; ${result.missing.length} missing, ${result.duplicates.length} duplicate registrations`)
      for (const item of [...result.missing, ...result.duplicates]) {
        console.log(`  ${result.missing.includes(item) ? 'MISSING' : 'DUPLICATE'} ${item.key} — ${item.locations.map(loc => `${loc.file}:${loc.line}`).join(', ')}`)
      }
    }
    for (const item of report.unresolved) {
      console.log(`UNRESOLVED ${item.method} ${item.file}:${item.line}`)
    }
    console.log(report.limitations)
  }
  if (strict) {
    const modules = new Set(strict.slice('--strict='.length).split(','))
    const selected = loc => [...modules].some(name => loc.file.endsWith(`/api/modules/${name}.ts`))
    const missing = [report.dev, report.pages].flatMap(mode => mode.missing).some(item => item.locations.some(selected))
    if (missing || report.unresolved.some(selected)) {
      process.exitCode = 1
    }
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main()
}
