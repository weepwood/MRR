export type DashboardWidgetSize = 'small' | 'wide' | 'large'

export interface DashboardWidgetDefinition {
  id: string
  title: string
  description: string
  icon: string
  path: string
  permission?: string
  defaultSize: DashboardWidgetSize
  defaultPinned?: boolean
  defaultVisible?: boolean
}

export interface DashboardWidgetPreference {
  id: string
  title: string
  description: string
  size: DashboardWidgetSize
  pinned: boolean
  visible: boolean
  order: number
}

const widgetSizes: DashboardWidgetSize[] = ['small', 'wide', 'large']

function isWidgetSize(value: unknown): value is DashboardWidgetSize {
  return typeof value === 'string' && widgetSizes.includes(value as DashboardWidgetSize)
}

function normalizeText(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function reindexWidgetPreferences(preferences: DashboardWidgetPreference[]) {
  return preferences.map((item, order) => ({ ...item, order }))
}

export function createDefaultWidgetPreferences(definitions: DashboardWidgetDefinition[]): DashboardWidgetPreference[] {
  return definitions.map((definition, order) => ({
    id: definition.id,
    title: definition.title,
    description: definition.description,
    size: definition.defaultSize,
    pinned: definition.defaultPinned === true,
    visible: definition.defaultVisible !== false,
    order,
  }))
}

export function mergeWidgetPreferences(
  definitions: DashboardWidgetDefinition[],
  storedPreferences: unknown,
): DashboardWidgetPreference[] {
  const definitionsById = new Map(definitions.map(definition => [definition.id, definition]))
  const stored = Array.isArray(storedPreferences)
    ? storedPreferences
        .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
        .filter(item => typeof item.id === 'string' && definitionsById.has(item.id))
        .sort((left, right) => {
          const leftOrder = typeof left.order === 'number' && Number.isFinite(left.order) ? left.order : Number.MAX_SAFE_INTEGER
          const rightOrder = typeof right.order === 'number' && Number.isFinite(right.order) ? right.order : Number.MAX_SAFE_INTEGER
          return leftOrder - rightOrder
        })
    : []

  const usedIds = new Set<string>()
  const merged: DashboardWidgetPreference[] = []

  stored.forEach((item) => {
    const id = String(item.id)
    if (usedIds.has(id)) return
    const definition = definitionsById.get(id)
    if (!definition) return

    usedIds.add(id)
    merged.push({
      id,
      title: normalizeText(item.title, definition.title),
      description: normalizeText(item.description, definition.description),
      size: isWidgetSize(item.size) ? item.size : definition.defaultSize,
      pinned: typeof item.pinned === 'boolean' ? item.pinned : definition.defaultPinned === true,
      visible: typeof item.visible === 'boolean' ? item.visible : definition.defaultVisible !== false,
      order: merged.length,
    })
  })

  definitions.forEach((definition) => {
    if (usedIds.has(definition.id)) return
    merged.push({
      id: definition.id,
      title: definition.title,
      description: definition.description,
      size: definition.defaultSize,
      pinned: definition.defaultPinned === true,
      visible: definition.defaultVisible !== false,
      order: merged.length,
    })
  })

  return reindexWidgetPreferences(merged)
}

export function sortWidgetPreferences(preferences: DashboardWidgetPreference[]): DashboardWidgetPreference[] {
  return [...preferences].sort((left, right) => {
    if (left.pinned !== right.pinned) return left.pinned ? -1 : 1
    return left.order - right.order
  })
}

export function moveWidget(
  preferences: DashboardWidgetPreference[],
  sourceId: string,
  targetId: string,
): DashboardWidgetPreference[] {
  if (sourceId === targetId) return reindexWidgetPreferences([...preferences])

  const sorted = sortWidgetPreferences(preferences)
  const sourceIndex = sorted.findIndex(item => item.id === sourceId)
  const targetIndex = sorted.findIndex(item => item.id === targetId)
  if (sourceIndex < 0 || targetIndex < 0) return reindexWidgetPreferences(sorted)
  if (sorted[sourceIndex].pinned !== sorted[targetIndex].pinned) return reindexWidgetPreferences(sorted)

  const next = [...sorted]
  const [source] = next.splice(sourceIndex, 1)
  const insertionIndex = next.findIndex(item => item.id === targetId)
  next.splice(Math.max(0, insertionIndex), 0, source)
  return reindexWidgetPreferences(next)
}

export function setWidgetPinned(
  preferences: DashboardWidgetPreference[],
  widgetId: string,
  pinned: boolean,
): DashboardWidgetPreference[] {
  const updated = preferences.map(item => item.id === widgetId ? { ...item, pinned } : item)
  return reindexWidgetPreferences(sortWidgetPreferences(updated))
}

export function updateWidgetPreference(
  preferences: DashboardWidgetPreference[],
  widgetId: string,
  patch: Partial<Pick<DashboardWidgetPreference, 'title' | 'description' | 'size' | 'visible'>>,
): DashboardWidgetPreference[] {
  return preferences.map(item => item.id === widgetId ? { ...item, ...patch } : item)
}
