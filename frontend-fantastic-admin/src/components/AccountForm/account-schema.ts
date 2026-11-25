import type { TypedSchema } from 'vee-validate'
import type { z } from 'zod'

/**
 * Bridge Zod 4's public object API to Vee Validate 4.
 * Account forms use flat scalar fields and explicit initial values only.
 * This is not a general-purpose nested schema/default-value adapter.
 */
export function toAccountSchema<S extends z.ZodObject>(schema: S): TypedSchema<z.input<S>, z.output<S>> {
  return {
    __type: 'VVTypedSchema',
    async parse(values) {
      const result = await schema.safeParseAsync(values)
      if (result.success) { return { value: result.data, errors: [] } }
      const errors = new Map<string, string[]>()
      for (const issue of result.error.issues) {
        const path = issue.path.map(String).join('.')
        const messages = errors.get(path) ?? []
        messages.push(issue.message)
        errors.set(path, messages)
      }
      return { errors: Array.from(errors, ([path, errors]) => ({ path, errors })) }
    },
    cast(values) {
      const result = schema.safeParse(values)
      // Preserve incomplete initial form values, including invalid strings, until validation.
      return (result.success ? result.data : values) as z.input<S>
    },
    describe(path) {
      if (!path) { return { exists: true, required: !schema.isOptional() } }
      const key = String(path)
      if (!Object.hasOwn(schema.shape, key)) { return { exists: false, required: false } }
      return { exists: true, required: !schema.shape[key].isOptional() }
    },
  }
}
