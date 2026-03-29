/**
 * Theme Utility
 * Handles dynamic CSS variable manipulation for runtime theme customization.
 */

/**
 * Apply theme settings to the document root (:root).
 * @param settings Theme configuration object
 */
export const applyTheme = (settings: Record<string, any>) => {
  if (typeof document === 'undefined') return

  const root = document.documentElement

  // Primary Action Color
  // All variations (brand-50, 100, 700 and el-primary-light-*) are now 
  // automatically derived in CSS via color-mix() based on this token.
  if (settings.primaryColor) {
    root.style.setProperty('--pmr-color-action-primary', settings.primaryColor)
  }

  // Border Radius
  // Higher level radii are derived in CSS where possible, 
  // but we still set the base and its immediate variations here for precision.
  if (settings.borderRadius !== undefined) {
    const radius = Number(settings.borderRadius)
    root.style.setProperty('--pmr-radius-xl', `${radius}px`)
    
    // Scale other radii proportionally
    root.style.setProperty('--pmr-radius-2xl', `${radius + 2}px`)
    root.style.setProperty('--pmr-radius-lg', `${radius - 2}px`)
    root.style.setProperty('--pmr-radius-md', `${radius - 4}px`)
    root.style.setProperty('--pmr-radius-sm', `${radius - 6}px`)
  }
}
